package com.huatai.rag.application.rag;

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

    String NO_DOCS_FALLBACK =
            "\u62b1\u6b49\uff0c\u6211\u65e0\u6cd5\u627e\u5230\u76f8\u5173\u4fe1\u606f\u6765\u56de\u7b54\u60a8\u7684\u95ee\u9898\u3002";

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

            if (rerankedDocuments.isEmpty()) {
                for (String indexName : command.indexNames()) {
                    questionHistoryPort.recordQuestion(indexName, command.query());
                }
                return new QueryResult(
                        NO_DOCS_FALLBACK,
                        List.of(),
                        retrievalResult.recallDocuments(),
                        List.of());
            }

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
