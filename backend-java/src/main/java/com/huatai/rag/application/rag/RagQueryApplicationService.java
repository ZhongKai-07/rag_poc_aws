package com.huatai.rag.application.rag;

import com.huatai.rag.application.common.ContextAssemblyService;
import com.huatai.rag.domain.history.QuestionHistoryPort;
import com.huatai.rag.domain.rag.AnswerGenerationPort;
import com.huatai.rag.domain.rag.Citation;
import com.huatai.rag.domain.rag.CitedAnswer;
import com.huatai.rag.domain.rag.RewriteResult;
import com.huatai.rag.domain.rag.StructuredQuery;
import com.huatai.rag.domain.retrieval.RetrievalPort;
import com.huatai.rag.domain.retrieval.RetrievalRequest;
import com.huatai.rag.domain.retrieval.RetrievalResult;
import com.huatai.rag.domain.retrieval.RetrievedDocument;
import com.huatai.rag.domain.retrieval.RerankPort;
import com.huatai.rag.domain.retrieval.SearchMethod;
import com.huatai.rag.infrastructure.config.RagProperties;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

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
            List<RetrievedDocument> rerankDocuments,
            List<Citation> citations) {

        public QueryResult {
            sourceDocuments = List.copyOf(sourceDocuments);
            recallDocuments = List.copyOf(recallDocuments);
            rerankDocuments = List.copyOf(rerankDocuments);
            citations = List.copyOf(citations);
        }
    }

    final class Default implements RagQueryApplicationService {
        private final RetrievalPort retrievalPort;
        private final RerankPort rerankPort;
        private final AnswerGenerationPort answerGenerationPort;
        private final QuestionHistoryPort questionHistoryPort;
        private final ContextAssemblyService contextAssemblyService;
        private final QueryRewriteRouter queryRewriteRouter;
        private final CitationAssemblyService citationAssemblyService;
        private final RagProperties ragProperties;

        public Default(
                RetrievalPort retrievalPort,
                RerankPort rerankPort,
                AnswerGenerationPort answerGenerationPort,
                QuestionHistoryPort questionHistoryPort,
                ContextAssemblyService contextAssemblyService,
                QueryRewriteRouter queryRewriteRouter,
                CitationAssemblyService citationAssemblyService,
                RagProperties ragProperties) {
            this.retrievalPort = Objects.requireNonNull(retrievalPort, "retrievalPort");
            this.rerankPort = Objects.requireNonNull(rerankPort, "rerankPort");
            this.answerGenerationPort = Objects.requireNonNull(answerGenerationPort, "answerGenerationPort");
            this.questionHistoryPort = Objects.requireNonNull(questionHistoryPort, "questionHistoryPort");
            this.contextAssemblyService = Objects.requireNonNull(contextAssemblyService, "contextAssemblyService");
            this.queryRewriteRouter = Objects.requireNonNull(queryRewriteRouter, "queryRewriteRouter");
            this.citationAssemblyService = Objects.requireNonNull(citationAssemblyService, "citationAssemblyService");
            this.ragProperties = Objects.requireNonNull(ragProperties, "ragProperties");
        }

        @Override
        public QueryResult handle(QueryCommand command) {
            // 1. Query rewriting
            RewriteResult rewriteResult = queryRewriteRouter.rewrite(command.query(), command.module());

            // 2. Build retrieval request (with metadata filters for Collateral)
            Map<String, String> metadataFilters = null;
            if (rewriteResult.structured() != null) {
                metadataFilters = buildMetadataFilters(rewriteResult.structured());
            }
            var retrievalRequest = new RetrievalRequest(
                    command.indexNames(), rewriteResult.rewrittenQuery(),
                    SearchMethod.fromValue(command.searchMethod()),
                    command.vecDocsNum(), command.txtDocsNum(),
                    command.vecScoreThreshold(), command.textScoreThreshold(),
                    metadataFilters);
            RetrievalResult retrievalResult = retrievalPort.retrieve(retrievalRequest);

            // 3. Rerank
            List<RetrievedDocument> rerankedDocuments = rerankPort.rerank(
                    rewriteResult.rewrittenQuery(),
                    retrievalResult.rerankDocuments(),
                    command.rerankScoreThreshold());

            if (rerankedDocuments.isEmpty()) {
                for (String indexName : command.indexNames()) {
                    questionHistoryPort.recordQuestion(indexName, command.query());
                }
                return new QueryResult(NO_DOCS_FALLBACK, List.of(),
                        retrievalResult.recallDocuments(), List.of(), List.of());
            }

            // 4. Source document selection
            List<RetrievedDocument> sourceDocuments = contextAssemblyService.selectSourceDocuments(
                    retrievalResult, rerankedDocuments);

            // 5. Citation assembly + answer generation
            String answer;
            List<Citation> citations = List.of();
            if (ragProperties.isCitationEnabled()) {
                PromptWithCitations pwc = citationAssemblyService.assemble(sourceDocuments);
                String rawAnswer = answerGenerationPort.generateAnswer(command.query(), pwc.formattedContext());
                CitedAnswer citedAnswer = citationAssemblyService.parseResponse(rawAnswer, pwc.citationMap());
                answer = citedAnswer.answer();
                citations = citedAnswer.citations();
            } else {
                String simpleContext = sourceDocuments.stream()
                        .map(RetrievedDocument::pageContent)
                        .collect(Collectors.joining("\n"));
                answer = answerGenerationPort.generateAnswer(command.query(), simpleContext);
            }

            // 6. Record question history
            for (String indexName : command.indexNames()) {
                questionHistoryPort.recordQuestion(indexName, command.query());
            }

            return new QueryResult(answer, sourceDocuments,
                    retrievalResult.recallDocuments(), rerankedDocuments, citations);
        }

        private Map<String, String> buildMetadataFilters(StructuredQuery structured) {
            Map<String, String> filters = new LinkedHashMap<>();
            if (structured.counterparty() != null) {
                filters.put("counterparty", structured.counterparty());
            }
            if (structured.agreementType() != null) {
                filters.put("agreement_type", structured.agreementType());
            }
            return filters.isEmpty() ? null : filters;
        }
    }
}
