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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        private static final Logger log = LoggerFactory.getLogger(Default.class);
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
            log.info("[RAG] session={} module={} indices={} query='{}'",
                    command.sessionId(), command.module(), command.indexNames(), command.query());

            // 1. Query rewriting
            long rewriteStart = System.currentTimeMillis();
            RewriteResult rewriteResult = queryRewriteRouter.rewrite(command.query(), command.module());
            long rewriteMs = System.currentTimeMillis() - rewriteStart;
            log.info("[RAG] rewrite: {}ms, rewritten='{}', keywords={}, structured={}",
                    rewriteMs, rewriteResult.rewrittenQuery(), rewriteResult.keywords(),
                    rewriteResult.structured() != null ? rewriteResult.structured() : "null");

            // 2. Build retrieval request (with metadata filters for Collateral)
            Map<String, String> metadataFilters = null;
            if (rewriteResult.structured() != null) {
                metadataFilters = buildMetadataFilters(rewriteResult.structured());
                log.info("[RAG] metadata filters: {}", metadataFilters);
            }
            var retrievalRequest = new RetrievalRequest(
                    command.indexNames(), rewriteResult.rewrittenQuery(),
                    SearchMethod.fromValue(command.searchMethod()),
                    command.vecDocsNum(), command.txtDocsNum(),
                    command.vecScoreThreshold(), command.textScoreThreshold(),
                    metadataFilters);
            long retrievalStart = System.currentTimeMillis();
            RetrievalResult retrievalResult = retrievalPort.retrieve(retrievalRequest);
            long retrievalMs = System.currentTimeMillis() - retrievalStart;
            log.info("[RAG] retrieval: {}ms, recall={} docs, method={}",
                    retrievalMs, retrievalResult.recallDocuments().size(), command.searchMethod());

            // 3. Rerank (apply server-side floor to client threshold)
            double effectiveRerankThreshold = Math.max(
                    command.rerankScoreThreshold(),
                    ragProperties.getRerankScoreThreshold());
            long rerankStart = System.currentTimeMillis();
            List<RetrievedDocument> rerankedDocuments = rerankPort.rerank(
                    rewriteResult.rewrittenQuery(),
                    retrievalResult.rerankDocuments(),
                    effectiveRerankThreshold);
            long rerankMs = System.currentTimeMillis() - rerankStart;
            log.info("[RAG] rerank: {}ms, input={} -> output={} docs (threshold={}, requested={}, floor={})",
                    rerankMs, retrievalResult.rerankDocuments().size(),
                    rerankedDocuments.size(), effectiveRerankThreshold,
                    command.rerankScoreThreshold(), ragProperties.getRerankScoreThreshold());

            if (rerankedDocuments.isEmpty()) {
                log.warn("[RAG] no documents survived reranking, returning fallback answer");
                for (String indexName : command.indexNames()) {
                    questionHistoryPort.recordQuestion(indexName, command.query());
                }
                return new QueryResult(NO_DOCS_FALLBACK, List.of(),
                        retrievalResult.recallDocuments(), List.of(), List.of());
            }

            // 4. Source document selection
            List<RetrievedDocument> sourceDocuments = contextAssemblyService.selectSourceDocuments(
                    retrievalResult, rerankedDocuments);
            log.info("[RAG] source selection: {} docs for answer generation", sourceDocuments.size());

            // 5. Citation assembly + answer generation
            long answerStart = System.currentTimeMillis();
            String answer;
            List<Citation> citations = List.of();
            if (ragProperties.isCitationEnabled()) {
                PromptWithCitations pwc = citationAssemblyService.assemble(sourceDocuments);
                log.debug("[RAG] citation prompt assembled with {} references", pwc.citationMap().size());
                String rawAnswer = answerGenerationPort.generateAnswer(command.query(), pwc.formattedContext());
                CitedAnswer citedAnswer = citationAssemblyService.parseResponse(rawAnswer, pwc.citationMap());
                answer = citedAnswer.answer();
                citations = citedAnswer.citations();
                log.info("[RAG] citation: {} references used in answer", citations.size());
            } else {
                String simpleContext = sourceDocuments.stream()
                        .map(RetrievedDocument::pageContent)
                        .collect(Collectors.joining("\n"));
                answer = answerGenerationPort.generateAnswer(command.query(), simpleContext);
                log.info("[RAG] citation disabled, using simple context");
            }
            long answerMs = System.currentTimeMillis() - answerStart;
            log.info("[RAG] answer generation: {}ms, answer length={} chars", answerMs, answer.length());

            // 6. Record question history
            for (String indexName : command.indexNames()) {
                questionHistoryPort.recordQuestion(indexName, command.query());
            }

            log.info("[RAG] pipeline complete: rewrite={}ms + retrieval={}ms + rerank={}ms + answer={}ms = {}ms total",
                    rewriteMs, retrievalMs, rerankMs, answerMs,
                    rewriteMs + retrievalMs + rerankMs + answerMs);

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
