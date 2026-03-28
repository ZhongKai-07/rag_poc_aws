package com.huatai.rag.application.rag;

import com.huatai.rag.application.chat.ConversationMemoryService;
import com.huatai.rag.application.common.ContextAssemblyService;
import com.huatai.rag.domain.chat.ChatMessage;
import com.huatai.rag.domain.chat.ChatMessagePort;
import com.huatai.rag.domain.chat.ChatSessionPort;
import com.huatai.rag.domain.chat.ConversationContext;
import com.huatai.rag.domain.history.QuestionHistoryPort;
import com.huatai.rag.domain.rag.AnswerGenerationPort;
import com.huatai.rag.domain.rag.Citation;
import com.huatai.rag.domain.rag.CitedAnswer;
import com.huatai.rag.domain.rag.ConfidenceLevel;
import com.huatai.rag.domain.rag.RewriteResult;
import com.huatai.rag.domain.rag.StructuredQuery;
import com.huatai.rag.domain.retrieval.RetrievalPort;
import com.huatai.rag.domain.retrieval.RetrievalRequest;
import com.huatai.rag.domain.retrieval.RetrievalResult;
import com.huatai.rag.domain.retrieval.RetrievedDocument;
import com.huatai.rag.domain.retrieval.RerankPort;
import com.huatai.rag.domain.retrieval.SearchMethod;
import com.huatai.rag.infrastructure.config.RagProperties;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
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
            List<Citation> citations,
            List<String> suggestedQuestions,
            ConfidenceLevel confidence,
            boolean historyCompressed,
            UUID sessionId) {

        public QueryResult {
            sourceDocuments = List.copyOf(sourceDocuments);
            recallDocuments = List.copyOf(recallDocuments);
            rerankDocuments = List.copyOf(rerankDocuments);
            citations = List.copyOf(citations);
            suggestedQuestions = suggestedQuestions != null ? List.copyOf(suggestedQuestions) : List.of();
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
        private final ConversationMemoryService conversationMemoryService;
        private final ChatSessionPort chatSessionPort;
        private final ChatMessagePort chatMessagePort;

        public Default(
                RetrievalPort retrievalPort,
                RerankPort rerankPort,
                AnswerGenerationPort answerGenerationPort,
                QuestionHistoryPort questionHistoryPort,
                ContextAssemblyService contextAssemblyService,
                QueryRewriteRouter queryRewriteRouter,
                CitationAssemblyService citationAssemblyService,
                RagProperties ragProperties,
                ConversationMemoryService conversationMemoryService,
                ChatSessionPort chatSessionPort,
                ChatMessagePort chatMessagePort) {
            this.retrievalPort = Objects.requireNonNull(retrievalPort, "retrievalPort");
            this.rerankPort = Objects.requireNonNull(rerankPort, "rerankPort");
            this.answerGenerationPort = Objects.requireNonNull(answerGenerationPort, "answerGenerationPort");
            this.questionHistoryPort = Objects.requireNonNull(questionHistoryPort, "questionHistoryPort");
            this.contextAssemblyService = Objects.requireNonNull(contextAssemblyService, "contextAssemblyService");
            this.queryRewriteRouter = Objects.requireNonNull(queryRewriteRouter, "queryRewriteRouter");
            this.citationAssemblyService = Objects.requireNonNull(citationAssemblyService, "citationAssemblyService");
            this.ragProperties = Objects.requireNonNull(ragProperties, "ragProperties");
            this.conversationMemoryService = conversationMemoryService;
            this.chatSessionPort = chatSessionPort;
            this.chatMessagePort = chatMessagePort;
        }

        @Override
        public QueryResult handle(QueryCommand command) {
            log.info("[RAG] session={} module={} indices={} query='{}'",
                    command.sessionId(), command.module(), command.indexNames(), command.query());

            // 0. Session management
            UUID sessionId = resolveSessionId(command.sessionId(), command.query(), command.module());
            ConversationContext conversationContext = loadConversationContext(sessionId);

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

            // 3b. Compute confidence from max rerank score
            Double maxRerankScore = rerankedDocuments.stream()
                    .map(RetrievedDocument::rerankScore)
                    .filter(Objects::nonNull)
                    .max(Double::compareTo)
                    .orElse(null);
            ConfidenceLevel confidence = ConfidenceLevel.fromScore(maxRerankScore);

            if (rerankedDocuments.isEmpty()) {
                log.warn("[RAG] no documents survived reranking, returning fallback answer");
                for (String indexName : command.indexNames()) {
                    questionHistoryPort.recordQuestion(indexName, command.query());
                }
                return new QueryResult(NO_DOCS_FALLBACK, List.of(),
                        retrievalResult.recallDocuments(), List.of(), List.of(),
                        List.of(), confidence, conversationContext.compressed(), sessionId);
            }

            // 4. Source document selection
            List<RetrievedDocument> sourceDocuments = contextAssemblyService.selectSourceDocuments(
                    retrievalResult, rerankedDocuments);
            log.info("[RAG] source selection: {} docs for answer generation", sourceDocuments.size());

            // 5. Citation assembly + answer generation
            long answerStart = System.currentTimeMillis();
            String answer;
            List<Citation> citations = List.of();
            List<String> suggestedQuestions = List.of();
            if (ragProperties.isCitationEnabled()) {
                // Build context with conversation history
                String indexName = command.indexNames().isEmpty() ? null : command.indexNames().get(0);
                PromptWithCitations pwc = citationAssemblyService.assemble(sourceDocuments, indexName);
                String contextWithHistory = conversationContext.formattedHistory().isEmpty()
                        ? pwc.formattedContext()
                        : "对话历史：\n" + conversationContext.formattedHistory() + "\n\n" + pwc.formattedContext();
                log.debug("[RAG] citation prompt assembled with {} references", pwc.citationMap().size());

                String rawOutput = answerGenerationPort.generateAnswer(command.query(), contextWithHistory);

                // Parse JSON output (answer + suggested questions)
                ParsedLlmOutput parsed = citationAssemblyService.parseLlmOutput(rawOutput);
                suggestedQuestions = parsed.suggestedQuestions();

                CitedAnswer citedAnswer = citationAssemblyService.parseResponse(parsed.answerText(), pwc.citationMap());
                answer = citedAnswer.answer();
                citations = citedAnswer.citations();
                log.info("[RAG] citation: {} references used, {} suggested questions", citations.size(), suggestedQuestions.size());
            } else {
                String simpleContext = sourceDocuments.stream()
                        .map(RetrievedDocument::pageContent)
                        .collect(Collectors.joining("\n"));
                answer = answerGenerationPort.generateAnswer(command.query(), simpleContext);
                log.info("[RAG] citation disabled, using simple context");
            }
            long answerMs = System.currentTimeMillis() - answerStart;
            log.info("[RAG] answer generation: {}ms, answer length={} chars", answerMs, answer.length());

            // 6. Save messages to session
            saveMessages(sessionId, command.query(), answer, citations, suggestedQuestions);

            // 7. Record question history
            for (String indexName : command.indexNames()) {
                questionHistoryPort.recordQuestion(indexName, command.query());
            }

            log.info("[RAG] pipeline complete: rewrite={}ms + retrieval={}ms + rerank={}ms + answer={}ms = {}ms total",
                    rewriteMs, retrievalMs, rerankMs, answerMs,
                    rewriteMs + retrievalMs + rerankMs + answerMs);

            return new QueryResult(answer, sourceDocuments,
                    retrievalResult.recallDocuments(), rerankedDocuments, citations,
                    suggestedQuestions, confidence, conversationContext.compressed(), sessionId);
        }

        private UUID resolveSessionId(String rawSessionId, String query, String module) {
            if (rawSessionId != null && !rawSessionId.isBlank()) {
                try {
                    UUID id = UUID.fromString(rawSessionId);
                    if (chatSessionPort != null && chatSessionPort.findById(id).isPresent()) {
                        return id;
                    }
                } catch (IllegalArgumentException ignored) {
                    // Not a valid UUID (legacy frontend) — create new session
                }
            }
            if (chatSessionPort != null) {
                String title = query.length() > 50 ? query.substring(0, 50).trim() : query;
                return chatSessionPort.create(title, module != null ? module : "RAG").id();
            }
            return UUID.randomUUID();
        }

        private ConversationContext loadConversationContext(UUID sessionId) {
            if (conversationMemoryService != null) {
                return conversationMemoryService.loadContext(sessionId);
            }
            return new ConversationContext("", false);
        }

        private void saveMessages(UUID sessionId, String query, String answer,
                                  List<Citation> citations, List<String> suggestedQuestions) {
            if (chatMessagePort == null) return;
            try {
                chatMessagePort.save(new ChatMessage(UUID.randomUUID(), sessionId,
                        "USER", query, null, null, Instant.now()));
                chatMessagePort.save(new ChatMessage(UUID.randomUUID(), sessionId,
                        "ASSISTANT", answer, citations, suggestedQuestions, Instant.now()));
            } catch (Exception e) {
                log.warn("[RAG] failed to save chat messages: {}", e.getMessage());
            }
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
