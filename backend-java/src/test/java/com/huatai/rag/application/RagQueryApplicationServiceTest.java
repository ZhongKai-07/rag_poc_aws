package com.huatai.rag.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.huatai.rag.application.common.ContextAssemblyService;
import com.huatai.rag.application.history.QuestionHistoryApplicationService;
import com.huatai.rag.application.rag.RagQueryApplicationService;
import com.huatai.rag.domain.history.QuestionHistoryPort;
import com.huatai.rag.domain.retrieval.RetrievalPort;
import com.huatai.rag.domain.retrieval.RetrievalResult;
import com.huatai.rag.domain.retrieval.RetrievedDocument;
import com.huatai.rag.domain.retrieval.RerankPort;
import com.huatai.rag.domain.retrieval.SearchMethod;
import com.huatai.rag.domain.rag.AnswerGenerationPort;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RagQueryApplicationServiceTest {

    @Test
    void ragQueryServiceOrchestratesRetrievalRerankAnswerAndQuestionHistory() {
        RetrievedDocument recallOne = new RetrievedDocument(
                "The onboarding checklist must be approved before activation.",
                91.2,
                null,
                Map.of("source", "Onboarding Decision Chart.pdf", "page_number", 2));
        RetrievedDocument recallTwo = new RetrievedDocument(
                "CSA collateral thresholds are reviewed weekly.",
                86.4,
                null,
                Map.of("source", "ISDA CSA.pdf", "page_number", 5));
        RetrievalResult retrievalResult = new RetrievalResult(List.of(recallOne, recallTwo), List.of(recallOne, recallTwo));

        FakeQuestionHistoryPort questionHistoryPort = new FakeQuestionHistoryPort();
        FakeRagQueryDependencies dependencies = new FakeRagQueryDependencies(retrievalResult);
        dependencies.rerankedDocuments = List.of(
                new RetrievedDocument(recallOne.pageContent(), recallOne.score(), 0.93, recallOne.metadata()));
        dependencies.answer = "Answer assembled from reranked context";

        RagQueryApplicationService.Default service = new RagQueryApplicationService.Default(
                dependencies.retrievalPort,
                dependencies.rerankPort,
                dependencies.answerGenerationPort,
                questionHistoryPort,
                new ContextAssemblyService());

        RagQueryApplicationService.QueryResult result = service.handle(new RagQueryApplicationService.QueryCommand(
                "baseline-session-001",
                List.of("2f295fa6", "32a592c0"),
                "Summarize the onboarding requirements.",
                "RAG",
                3,
                2,
                0.0,
                0.0,
                0.5,
                "mix"));

        assertThat(dependencies.requestedIndexNames).containsExactly("2f295fa6", "32a592c0");
        assertThat(dependencies.requestedSearchMethod).isEqualTo(SearchMethod.MIX);
        assertThat(dependencies.rerankQuery).isEqualTo("Summarize the onboarding requirements.");
        assertThat(result.answer()).isEqualTo("Answer assembled from reranked context");
        assertThat(result.recallDocuments()).hasSize(2);
        assertThat(result.rerankDocuments()).hasSize(1);
        assertThat(result.sourceDocuments()).hasSize(1);
        assertThat(questionHistoryPort.recordedQuestions())
                .containsExactly(
                        "2f295fa6|Summarize the onboarding requirements.",
                        "32a592c0|Summarize the onboarding requirements.");
    }

    @Test
    void questionHistoryServiceMapsSingleAndMultiIndexQueries() {
        FakeQuestionHistoryPort questionHistoryPort = new FakeQuestionHistoryPort();
        questionHistoryPort.singleIndexResults = List.of(
                new QuestionHistoryPort.QuestionCount("What is the onboarding decision path?", 4),
                new QuestionHistoryPort.QuestionCount("What are the CSA terms?", 2));
        questionHistoryPort.multiIndexResults = List.of(
                new QuestionHistoryPort.QuestionCount("What is the onboarding decision path?", 5));

        QuestionHistoryApplicationService.Default service = new QuestionHistoryApplicationService.Default(questionHistoryPort);

        QuestionHistoryApplicationService.TopQuestionsResult singleIndexResult =
                service.getTopQuestionsView("32a592c0", 5);
        QuestionHistoryApplicationService.TopQuestionsResult multiIndexResult =
                service.getTopQuestionsView(List.of("2f295fa6", "32a592c0"), 3);

        assertThat(singleIndexResult.status()).isEqualTo("success");
        assertThat(singleIndexResult.questions())
                .extracting(QuestionHistoryApplicationService.QuestionCountResult::question)
                .containsExactly("What is the onboarding decision path?", "What are the CSA terms?");
        assertThat(multiIndexResult.questions())
                .extracting(QuestionHistoryApplicationService.QuestionCountResult::count)
                .containsExactly(5L);
        assertThat(questionHistoryPort.multiIndexNames).containsExactly("2f295fa6", "32a592c0");
    }

    private static final class FakeRagQueryDependencies {
        private final RetrievalPort retrievalPort;
        private final RerankPort rerankPort;
        private final AnswerGenerationPort answerGenerationPort;
        private final RetrievalResult retrievalResult;
        private List<RetrievedDocument> rerankedDocuments = List.of();
        private String answer;
        private List<String> requestedIndexNames = List.of();
        private SearchMethod requestedSearchMethod;
        private String rerankQuery;

        private FakeRagQueryDependencies(RetrievalResult retrievalResult) {
            this.retrievalResult = retrievalResult;
            this.retrievalPort = (indexNames, query, searchMethod, vectorLimit, textLimit, vectorScoreThreshold, textScoreThreshold) -> {
                this.requestedIndexNames = indexNames;
                this.requestedSearchMethod = searchMethod;
                return this.retrievalResult;
            };
            this.rerankPort = (query, documents, rerankScoreThreshold) -> {
                this.rerankQuery = query;
                return rerankedDocuments;
            };
            this.answerGenerationPort = (query, sourceDocuments) -> answer;
        }
    }

    private static final class FakeQuestionHistoryPort implements QuestionHistoryPort {
        private final List<String> recordedQuestions = new ArrayList<>();
        private List<QuestionCount> singleIndexResults = List.of();
        private List<QuestionCount> multiIndexResults = List.of();
        private List<String> multiIndexNames = List.of();

        @Override
        public void recordQuestion(String indexName, String question) {
            recordedQuestions.add(indexName + "|" + question);
        }

        @Override
        public List<QuestionCount> topQuestions(String indexName, int limit) {
            return singleIndexResults;
        }

        @Override
        public List<QuestionCount> topQuestionsMulti(List<String> indexNames, int limit) {
            this.multiIndexNames = indexNames;
            return multiIndexResults;
        }

        private List<String> recordedQuestions() {
            return recordedQuestions;
        }
    }
}
