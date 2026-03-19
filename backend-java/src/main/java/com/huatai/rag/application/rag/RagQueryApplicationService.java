package com.huatai.rag.application.rag;

import com.huatai.rag.api.rag.dto.RagRequest;
import com.huatai.rag.api.rag.dto.RagResponse;
import com.huatai.rag.api.rag.dto.RecallDocumentDto;
import com.huatai.rag.api.rag.dto.SourceDocumentDto;
import com.huatai.rag.application.common.ContextAssemblyService;
import com.huatai.rag.domain.history.QuestionHistoryPort;
import com.huatai.rag.domain.rag.AnswerGenerationPort;
import com.huatai.rag.domain.retrieval.RetrievalPort;
import com.huatai.rag.domain.retrieval.RetrievalResult;
import com.huatai.rag.domain.retrieval.RetrievedDocument;
import com.huatai.rag.domain.retrieval.RerankPort;
import com.huatai.rag.domain.retrieval.SearchMethod;
import java.util.List;
import java.util.Objects;

public interface RagQueryApplicationService {

    default RagResponse answer(RagRequest request) {
        return QueryResult.toApiResponse(handle(QueryCommand.from(request)));
    }

    QueryResult handle(QueryCommand command);

    record QueryCommand(
            String sessionId,
            List<String> indexNames,
            String query,
            String module,
            int vecDocsNum,
            int txtDocsNum,
            double vecScoreThreshold,
            double textScoreThreshold,
            double rerankScoreThreshold,
            String searchMethod) {

        public QueryCommand {
            indexNames = List.copyOf(indexNames);
        }

        public static QueryCommand from(RagRequest request) {
            return new QueryCommand(
                    request.getSessionId(),
                    request.getIndexNames(),
                    request.getQuery(),
                    request.getModule(),
                    request.getVecDocsNum(),
                    request.getTxtDocsNum(),
                    request.getVecScoreThreshold(),
                    request.getTextScoreThreshold(),
                    request.getRerankScoreThreshold(),
                    request.getSearchMethod());
        }
    }

    record QueryResult(
            String answer,
            List<RetrievedDocument> sourceDocuments,
            List<RetrievedDocument> recallDocuments,
            List<RetrievedDocument> rerankDocuments) {

        public QueryResult {
            sourceDocuments = List.copyOf(sourceDocuments);
            recallDocuments = List.copyOf(recallDocuments);
            rerankDocuments = List.copyOf(rerankDocuments);
        }

        private static RagResponse toApiResponse(QueryResult result) {
            RagResponse response = new RagResponse();
            response.setAnswer(result.answer());
            response.setSourceDocuments(result.sourceDocuments().stream()
                    .map(QueryResult::toSourceDocument)
                    .toList());
            response.setRecallDocuments(result.recallDocuments().stream()
                    .map(QueryResult::toRecallDocument)
                    .toList());
            response.setRerankDocuments(result.rerankDocuments().stream()
                    .map(QueryResult::toSourceDocument)
                    .toList());
            return response;
        }

        private static SourceDocumentDto toSourceDocument(RetrievedDocument document) {
            SourceDocumentDto dto = new SourceDocumentDto();
            dto.setPageContent(document.pageContent());
            dto.setScore(document.score());
            dto.setRerankScore(document.rerankScore());
            return dto;
        }

        private static RecallDocumentDto toRecallDocument(RetrievedDocument document) {
            RecallDocumentDto dto = new RecallDocumentDto();
            dto.setPageContent(document.pageContent());
            dto.setScore(document.score());
            return dto;
        }
    }

    final class Default implements RagQueryApplicationService {
        private final RetrievalPort retrievalPort;
        private final RerankPort rerankPort;
        private final AnswerGenerationPort answerGenerationPort;
        private final QuestionHistoryPort questionHistoryPort;
        private final ContextAssemblyService contextAssemblyService;

        public Default(
                RetrievalPort retrievalPort,
                RerankPort rerankPort,
                AnswerGenerationPort answerGenerationPort,
                QuestionHistoryPort questionHistoryPort,
                ContextAssemblyService contextAssemblyService) {
            this.retrievalPort = Objects.requireNonNull(retrievalPort, "retrievalPort");
            this.rerankPort = Objects.requireNonNull(rerankPort, "rerankPort");
            this.answerGenerationPort = Objects.requireNonNull(answerGenerationPort, "answerGenerationPort");
            this.questionHistoryPort = Objects.requireNonNull(questionHistoryPort, "questionHistoryPort");
            this.contextAssemblyService = Objects.requireNonNull(contextAssemblyService, "contextAssemblyService");
        }

        @Override
        public QueryResult handle(QueryCommand command) {
            RetrievalResult retrievalResult = retrievalPort.retrieve(
                    command.indexNames(),
                    command.query(),
                    SearchMethod.fromValue(command.searchMethod()),
                    command.vecDocsNum(),
                    command.txtDocsNum(),
                    command.vecScoreThreshold(),
                    command.textScoreThreshold());

            List<RetrievedDocument> rerankedDocuments = rerankPort.rerank(
                    command.query(),
                    retrievalResult.rerankDocuments(),
                    command.rerankScoreThreshold());
            List<RetrievedDocument> sourceDocuments = contextAssemblyService.selectSourceDocuments(
                    retrievalResult,
                    rerankedDocuments);
            String answer = answerGenerationPort.generateAnswer(command.query(), sourceDocuments);

            for (String indexName : command.indexNames()) {
                questionHistoryPort.recordQuestion(indexName, command.query());
            }

            return new QueryResult(
                    answer,
                    sourceDocuments,
                    retrievalResult.recallDocuments(),
                    rerankedDocuments);
        }
    }
}
