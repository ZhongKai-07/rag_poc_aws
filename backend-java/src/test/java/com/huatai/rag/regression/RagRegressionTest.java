package com.huatai.rag.regression;

import static org.assertj.core.api.Assertions.assertThat;

import com.huatai.rag.application.common.ContextAssemblyService;
import com.huatai.rag.application.rag.CitationAssemblyService;
import com.huatai.rag.application.rag.QueryRewriteRouter;
import com.huatai.rag.application.rag.RagQueryApplicationService;
import com.huatai.rag.domain.rag.RewriteResult;
import com.huatai.rag.infrastructure.config.RagProperties;
import com.huatai.rag.domain.history.QuestionHistoryPort;
import com.huatai.rag.domain.rag.AnswerGenerationPort;
import com.huatai.rag.domain.retrieval.RetrievalPort;
import com.huatai.rag.domain.retrieval.RetrievalRequest;
import com.huatai.rag.domain.retrieval.RetrievalResult;
import com.huatai.rag.domain.retrieval.RetrievedDocument;
import com.huatai.rag.domain.retrieval.RerankPort;
import com.huatai.rag.domain.retrieval.SearchMethod;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class RagRegressionTest {

    private static final String QUESTIONS_FIXTURE = "fixtures/regression/questions.csv";
    private static final String NO_DOCS_FALLBACK =
            "\u62b1\u6b49\uff0c\u6211\u65e0\u6cd5\u627e\u5230\u76f8\u5173\u4fe1\u606f\u6765\u56de\u7b54\u60a8\u7684\u95ee\u9898\u3002";

    @Test
    void baselineQuestionFixturePreservesAnswerKeywordsSourcePresenceAndHistoryFanOut() throws IOException {
        List<RegressionCase> cases = RegressionCase.loadFromClasspath(QUESTIONS_FIXTURE);
        FakeQuestionHistoryPort questionHistoryPort = new FakeQuestionHistoryPort();
        var ragProps = new RagProperties();
        com.huatai.rag.domain.rag.QueryRewriteStrategy passthroughStrategy = new com.huatai.rag.domain.rag.QueryRewriteStrategy() {
            public boolean supports(String module) { return true; }
            public RewriteResult rewrite(String query) { return RewriteResult.passthrough(query); }
        };
        var router = new QueryRewriteRouter(List.of(), passthroughStrategy, ragProps);
        RagQueryApplicationService.Default service = new RagQueryApplicationService.Default(
                new FixtureBackedRetrievalPort(cases),
                new DeterministicRerankPort(),
                new FixtureAnswerGenerationPort(),
                questionHistoryPort,
                new ContextAssemblyService(),
                router,
                new CitationAssemblyService(),
                ragProps);

        for (RegressionCase regressionCase : cases) {
            List<String> indexNames = regressionCase.indexNames();
            RagQueryApplicationService.QueryResult result = service.handle(new RagQueryApplicationService.QueryCommand(
                    "regression-session",
                    indexNames,
                    regressionCase.question(),
                    "RAG",
                    3,
                    3,
                    0.0,
                    0.0,
                    0.5,
                    regressionCase.searchMethod()));

            assertThat(result.sourceDocuments())
                    .as("source document count for %s", regressionCase.question())
                    .hasSizeGreaterThanOrEqualTo(regressionCase.minSourceCount());
            assertThat(result.sourceDocuments())
                    .allSatisfy(document -> assertThat(document.rerankScore()).isNotNull());
            assertThat(result.answer().toLowerCase())
                    .as("answer keywords for %s", regressionCase.question())
                    .containsAnyOf(regressionCase.expectedKeywords());
            assertThat(questionHistoryPort.recordedQuestions())
                    .containsAll(indexNames.stream()
                            .map(indexName -> indexName + "|" + regressionCase.question())
                            .toList());
        }
    }

    @Test
    void emptyRerankResultsReturnPythonCompatibleNoDocsFallback() {
        TrackingAnswerGenerationPort answerGenerationPort = new TrackingAnswerGenerationPort();
        RetrievalPort retrievalPort = (request) ->
                new RetrievalResult(
                        List.of(new RetrievedDocument(
                                "The ISDA agreement requires collateral review before approval.",
                                0.91,
                                null,
                                Map.of("source", "(2) OTCD - ISDA.pdf"))),
                        List.of(new RetrievedDocument(
                                "The ISDA agreement requires collateral review before approval.",
                                0.91,
                                null,
                                Map.of("source", "(2) OTCD - ISDA.pdf"))));
        RerankPort rerankPort = (query, documents, rerankScoreThreshold) -> List.of();

        var ragProps2 = new RagProperties();
        com.huatai.rag.domain.rag.QueryRewriteStrategy passthroughStrategy2 = new com.huatai.rag.domain.rag.QueryRewriteStrategy() {
            public boolean supports(String module) { return true; }
            public RewriteResult rewrite(String query) { return RewriteResult.passthrough(query); }
        };
        var router2 = new QueryRewriteRouter(List.of(), passthroughStrategy2, ragProps2);
        RagQueryApplicationService.Default service = new RagQueryApplicationService.Default(
                retrievalPort,
                rerankPort,
                answerGenerationPort,
                new FakeQuestionHistoryPort(),
                new ContextAssemblyService(),
                router2,
                new CitationAssemblyService(),
                ragProps2);

        RagQueryApplicationService.QueryResult result = service.handle(new RagQueryApplicationService.QueryCommand(
                "regression-session",
                List.of("dca3cd03"),
                "What does the ISDA document cover?",
                "RAG",
                3,
                3,
                0.0,
                0.0,
                0.95,
                "vector"));

        assertThat(answerGenerationPort.invocationCount).isZero();
        assertThat(result.answer()).isEqualTo(NO_DOCS_FALLBACK);
        assertThat(result.sourceDocuments()).isEmpty();
        assertThat(result.rerankDocuments()).isEmpty();
        assertThat(result.recallDocuments()).hasSize(1);
    }

    private record RegressionCase(
            String filename,
            String indexNamesRaw,
            String question,
            String searchMethod,
            String expectedKeywordsRaw,
            int minSourceCount) {

        private static List<RegressionCase> loadFromClasspath(String resourcePath) throws IOException {
            try (InputStream inputStream = resourcePath(resourcePath)) {
                return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .skip(1)
                    .filter(line -> !line.isBlank())
                    .map(RegressionCase::parse)
                    .toList();
            }
        }

        private static RegressionCase parse(String line) {
            String[] columns = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
            return new RegressionCase(
                    columns[0],
                    columns[1],
                    columns[2],
                    columns[3],
                    columns[4].replace("\"", ""),
                    Integer.parseInt(columns[5]));
        }

        private List<String> indexNames() {
            return List.of(indexNamesRaw.split("\\|"));
        }

        private String[] expectedKeywords() {
            return expectedKeywordsRaw.toLowerCase().split("\\|");
        }
    }

    private static InputStream resourcePath(String resourcePath) {
        InputStream inputStream = RagRegressionTest.class.getClassLoader().getResourceAsStream(resourcePath);
        if (inputStream == null) {
            throw new IllegalStateException("Missing regression fixture: " + resourcePath);
        }
        return inputStream;
    }

    private static final class FixtureBackedRetrievalPort implements RetrievalPort {
        private final Map<String, RegressionCase> casesByQuestion;

        private FixtureBackedRetrievalPort(List<RegressionCase> regressionCases) {
            this.casesByQuestion = regressionCases.stream()
                    .collect(Collectors.toMap(RegressionCase::question, regressionCase -> regressionCase, (left, right) -> left, LinkedHashMap::new));
        }

        @Override
        public RetrievalResult retrieve(RetrievalRequest request) {
            RegressionCase regressionCase = casesByQuestion.get(request.query());
            List<RetrievedDocument> documents = new ArrayList<>();
            for (String keyword : regressionCase.expectedKeywords()) {
                documents.add(new RetrievedDocument(
                        buildContent(keyword, request.searchMethod(), regressionCase.filename()),
                        0.9 - (documents.size() * 0.05),
                        null,
                        Map.of(
                                "source", regressionCase.filename(),
                                "index_name", request.indexNames().get(Math.min(documents.size(), request.indexNames().size() - 1)),
                                "search_method", request.searchMethod().name().toLowerCase())));
            }
            return new RetrievalResult(documents, documents);
        }

        private String buildContent(String keyword, SearchMethod searchMethod, String filename) {
            return "Baseline content for " + filename + " keeps the " + keyword + " requirement visible through "
                    + searchMethod.name().toLowerCase() + " retrieval.";
        }
    }

    private static final class DeterministicRerankPort implements RerankPort {
        @Override
        public List<RetrievedDocument> rerank(String query, List<RetrievedDocument> documents, double rerankScoreThreshold) {
            List<RetrievedDocument> reranked = new ArrayList<>();
            for (int index = 0; index < documents.size(); index++) {
                RetrievedDocument document = documents.get(index);
                reranked.add(new RetrievedDocument(
                        document.pageContent(),
                        document.score(),
                        0.96 - (index * 0.03),
                        document.metadata()));
            }
            return reranked;
        }
    }

    private static final class FixtureAnswerGenerationPort implements AnswerGenerationPort {
        @Override
        public String generateAnswer(String query, String formattedContext) {
            return formattedContext;
        }
        @Override
        public void generateAnswerStream(String query, String formattedContext,
                                          java.util.function.Consumer<String> tokenConsumer) {
            tokenConsumer.accept(generateAnswer(query, formattedContext));
        }
    }

    private static final class TrackingAnswerGenerationPort implements AnswerGenerationPort {
        private int invocationCount;

        @Override
        public String generateAnswer(String query, String formattedContext) {
            invocationCount++;
            return "should-not-be-used";
        }
        @Override
        public void generateAnswerStream(String query, String formattedContext,
                                          java.util.function.Consumer<String> tokenConsumer) {
            tokenConsumer.accept(generateAnswer(query, formattedContext));
        }
    }

    private static final class FakeQuestionHistoryPort implements QuestionHistoryPort {
        private final List<String> recordedQuestions = new ArrayList<>();

        @Override
        public void recordQuestion(String indexName, String question) {
            recordedQuestions.add(indexName + "|" + question);
        }

        @Override
        public List<QuestionCount> topQuestions(String indexName, int limit) {
            return List.of();
        }

        @Override
        public List<QuestionCount> topQuestionsMulti(List<String> indexNames, int limit) {
            return List.of();
        }

        private List<String> recordedQuestions() {
            return List.copyOf(recordedQuestions);
        }
    }
}
