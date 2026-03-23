package com.huatai.rag.api;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.huatai.rag.api.common.ApiExceptionHandler;
import com.huatai.rag.api.health.HealthController;
import com.huatai.rag.api.question.QuestionController;
import com.huatai.rag.api.rag.RagController;
import com.huatai.rag.api.upload.UploadController;
import com.huatai.rag.application.common.ContextAssemblyService;
import com.huatai.rag.application.history.QuestionHistoryApplicationService;
import com.huatai.rag.application.ingestion.DocumentIngestionApplicationService;
import com.huatai.rag.application.registry.ProcessedFileQueryApplicationService;
import com.huatai.rag.application.rag.RagQueryApplicationService;
import com.huatai.rag.domain.document.DocumentFileRecord;
import com.huatai.rag.domain.document.DocumentRegistryPort;
import com.huatai.rag.domain.document.IndexNamingPolicy;
import com.huatai.rag.domain.document.IngestionJobRecord;
import com.huatai.rag.domain.document.IngestionStatus;
import com.huatai.rag.domain.history.QuestionHistoryPort;
import com.huatai.rag.domain.parser.DocumentParser;
import com.huatai.rag.domain.parser.ParsedChunk;
import com.huatai.rag.domain.parser.ParsedDocument;
import com.huatai.rag.domain.parser.ParsedPage;
import com.huatai.rag.domain.parser.ParserRequest;
import com.huatai.rag.domain.rag.AnswerGenerationPort;
import com.huatai.rag.domain.retrieval.EmbeddingPort;
import com.huatai.rag.domain.retrieval.RetrievalPort;
import com.huatai.rag.domain.retrieval.RetrievalResult;
import com.huatai.rag.domain.retrieval.RetrievedDocument;
import com.huatai.rag.domain.retrieval.RerankPort;
import com.huatai.rag.domain.retrieval.SearchMethod;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {RagController.class, UploadController.class, QuestionController.class, HealthController.class})
@Import({ApiExceptionHandler.class, ApiLayerIntegrationTest.TestConfig.class})
class ApiLayerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FakeAnswerGenerationPort fakeAnswerGenerationPort;

    @Autowired
    private FakeDocumentParser fakeDocumentParser;

    @BeforeEach
    void resetFailures() {
        fakeAnswerGenerationPort.failure = null;
        fakeDocumentParser.failure = null;
    }

    @Test
    void controllersUseRealApplicationServicesWithFrontendCompatiblePayloads() throws Exception {
        mockMvc.perform(post("/rag_answer")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "session_id": "baseline-session-001",
                                  "index_names": ["2f295fa6", "32a592c0"],
                                  "query": "Summarize the onboarding and agreement requirements.",
                                  "module": "RAG",
                                  "vec_docs_num": 3,
                                  "txt_docs_num": 3,
                                  "vec_score_threshold": 0.0,
                                  "text_score_threshold": 0.0,
                                  "rerank_score_threshold": 0.5,
                                  "search_method": "mix"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                          "answer": "A baseline-compatible answer is returned as plain text.",
                          "source_documents": [
                            {
                              "page_content": "The onboarding flow requires completing the agreement review before account activation.",
                              "score": 91.2,
                              "rerank_score": 0.93
                            }
                          ],
                          "recall_documents": [
                            {
                              "page_content": "The onboarding flow requires completing the agreement review before account activation.",
                              "score": 91.2
                            },
                            {
                              "page_content": "The ISDA and CSA terms are maintained in the same source document for this baseline sample.",
                              "score": 86.4
                            }
                          ],
                          "rerank_documents": [
                            {
                              "page_content": "The onboarding flow requires completing the agreement review before account activation.",
                              "score": 91.2,
                              "rerank_score": 0.93
                            }
                          ]
                        }
                        """, true));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.pdf",
                "application/pdf",
                "pdf".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/upload_files")
                        .file(file)
                        .param("directory_path", "./documents/test-batch"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"success\",\"message\":\"Files processed successfully\"}", true));

        String indexName = IndexNamingPolicy.indexNameFor("sample.pdf");

        mockMvc.perform(get("/processed_files"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                          "status": "success",
                          "files": [
                            {
                              "filename": "sample.pdf",
                              "index_name": "%s"
                            }
                          ]
                        }
                        """.formatted(indexName), true));

        mockMvc.perform(get("/get_index/sample.pdf"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                          "status": "success",
                          "index_name": "%s"
                        }
                        """.formatted(indexName), true));

        mockMvc.perform(get("/top_questions/2f295fa6"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                          "status": "success",
                          "questions": [
                            {
                              "question": "Summarize the onboarding and agreement requirements.",
                              "count": 1
                            }
                          ]
                        }
                        """, true));

        mockMvc.perform(get("/top_questions_multi")
                        .param("index_names", "2f295fa6,32a592c0"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                          "status": "success",
                          "questions": [
                            {
                              "question": "Summarize the onboarding and agreement requirements.",
                              "count": 2
                            }
                          ]
                        }
                        """, true));
    }

    @Test
    void infrastructureFailuresAreMappedToFrontendSafeMessages() throws Exception {
        fakeAnswerGenerationPort.failure = new IllegalStateException("Failed to invoke Bedrock model");

        mockMvc.perform(post("/rag_answer")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "session_id": "baseline-session-001",
                                  "index_names": ["2f295fa6"],
                                  "query": "Fail with bedrock",
                                  "module": "RAG",
                                  "vec_docs_num": 3,
                                  "txt_docs_num": 3,
                                  "vec_score_threshold": 0.0,
                                  "text_score_threshold": 0.0,
                                  "rerank_score_threshold": 0.5,
                                  "search_method": "mix"
                                }
                                """))
                .andExpect(status().isInternalServerError())
                .andExpect(content().json("{\"detail\":\"Bedrock request failed\"}", true));

        fakeAnswerGenerationPort.failure = null;
        fakeDocumentParser.failure = new IllegalStateException("BDA parsing failed: timeout");

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.pdf",
                "application/pdf",
                "pdf".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/upload_files")
                        .file(file)
                        .param("directory_path", "./documents/test-batch"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().json("{\"detail\":\"BDA parsing failed\"}", true));
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        @Primary
        FakeDocumentRegistryPort fakeDocumentRegistryPort() {
            return new FakeDocumentRegistryPort();
        }

        @Bean
        @Primary
        FakeQuestionHistoryPort fakeQuestionHistoryPort() {
            return new FakeQuestionHistoryPort();
        }

        @Bean
        @Primary
        FakeRetrievalPort fakeRetrievalPort() {
            return new FakeRetrievalPort();
        }

        @Bean
        @Primary
        FakeRerankPort fakeRerankPort() {
            return new FakeRerankPort();
        }

        @Bean
        @Primary
        FakeAnswerGenerationPort fakeAnswerGenerationPort() {
            return new FakeAnswerGenerationPort();
        }

        @Bean
        @Primary
        FakeDocumentStorage fakeDocumentStorage() {
            return new FakeDocumentStorage();
        }

        @Bean
        @Primary
        FakeDocumentParser fakeDocumentParser() {
            return new FakeDocumentParser();
        }

        @Bean
        @Primary
        FakeEmbeddingPort fakeEmbeddingPort() {
            return new FakeEmbeddingPort();
        }

        @Bean
        @Primary
        FakeDocumentChunkWriter fakeDocumentChunkWriter() {
            return new FakeDocumentChunkWriter();
        }

        @Bean
        ContextAssemblyService contextAssemblyService() {
            return new ContextAssemblyService();
        }

        @Bean
        RagQueryApplicationService ragQueryApplicationService(
                FakeRetrievalPort retrievalPort,
                FakeRerankPort rerankPort,
                FakeAnswerGenerationPort answerGenerationPort,
                FakeQuestionHistoryPort questionHistoryPort,
                ContextAssemblyService contextAssemblyService) {
            return new RagQueryApplicationService.Default(
                    retrievalPort,
                    rerankPort,
                    answerGenerationPort,
                    questionHistoryPort,
                    contextAssemblyService);
        }

        @Bean
        DocumentIngestionApplicationService documentIngestionApplicationService(
                FakeDocumentStorage documentStorage,
                FakeDocumentRegistryPort documentRegistryPort,
                FakeDocumentParser documentParser,
                FakeEmbeddingPort embeddingPort,
                FakeDocumentChunkWriter documentChunkWriter) {
            return new DocumentIngestionApplicationService.Default(
                    documentStorage,
                    documentRegistryPort,
                    documentParser,
                    embeddingPort,
                    documentChunkWriter);
        }

        @Bean
        ProcessedFileQueryApplicationService processedFileQueryApplicationService(FakeDocumentRegistryPort documentRegistryPort) {
            return new ProcessedFileQueryApplicationService.Default(documentRegistryPort);
        }

        @Bean
        QuestionHistoryApplicationService questionHistoryApplicationService(FakeQuestionHistoryPort questionHistoryPort) {
            return new QuestionHistoryApplicationService.Default(questionHistoryPort);
        }
    }

    static final class FakeRetrievalPort implements RetrievalPort {
        @Override
        public RetrievalResult retrieve(
                List<String> indexNames,
                String query,
                SearchMethod searchMethod,
                int vectorLimit,
                int textLimit,
                double vectorScoreThreshold,
                double textScoreThreshold) {
            RetrievedDocument recallOne = new RetrievedDocument(
                    "The onboarding flow requires completing the agreement review before account activation.",
                    91.2,
                    null,
                    Map.of("source", "Onboarding Decision Chart.pdf", "page_number", 2));
            RetrievedDocument recallTwo = new RetrievedDocument(
                    "The ISDA and CSA terms are maintained in the same source document for this baseline sample.",
                    86.4,
                    null,
                    Map.of("source", "ISDA CSA.pdf", "page_number", 5));
            return new RetrievalResult(List.of(recallOne, recallTwo), List.of(recallOne, recallTwo));
        }
    }

    static final class FakeRerankPort implements RerankPort {
        @Override
        public List<RetrievedDocument> rerank(String query, List<RetrievedDocument> documents, double rerankScoreThreshold) {
            RetrievedDocument topDocument = documents.get(0);
            return List.of(new RetrievedDocument(
                    topDocument.pageContent(),
                    topDocument.score(),
                    0.93,
                    topDocument.metadata()));
        }
    }

    static final class FakeAnswerGenerationPort implements AnswerGenerationPort {
        private RuntimeException failure;

        @Override
        public String generateAnswer(String query, List<RetrievedDocument> sourceDocuments) {
            if (failure != null) {
                throw failure;
            }
            return "A baseline-compatible answer is returned as plain text.";
        }
    }

    static final class FakeQuestionHistoryPort implements QuestionHistoryPort {
        private final Map<String, List<String>> questionsByIndex = new LinkedHashMap<>();

        @Override
        public void recordQuestion(String indexName, String question) {
            questionsByIndex.computeIfAbsent(indexName, ignored -> new ArrayList<>()).add(question);
        }

        @Override
        public List<QuestionCount> topQuestions(String indexName, int limit) {
            return aggregate(questionsByIndex.getOrDefault(indexName, List.of()), limit);
        }

        @Override
        public List<QuestionCount> topQuestionsMulti(List<String> indexNames, int limit) {
            List<String> combined = new ArrayList<>();
            for (String indexName : indexNames) {
                combined.addAll(questionsByIndex.getOrDefault(indexName, List.of()));
            }
            return aggregate(combined, limit);
        }

        private List<QuestionCount> aggregate(List<String> questions, int limit) {
            Map<String, Long> counts = new LinkedHashMap<>();
            for (String question : questions) {
                counts.merge(question, 1L, Long::sum);
            }
            return counts.entrySet().stream()
                    .limit(limit)
                    .map(entry -> new QuestionCount(entry.getKey(), entry.getValue()))
                    .toList();
        }
    }

    static final class FakeDocumentStorage implements DocumentIngestionApplicationService.DocumentStorage {
        @Override
        public DocumentIngestionApplicationService.StoredDocument store(String filename, byte[] content, String directoryPath) {
            return new DocumentIngestionApplicationService.StoredDocument(
                    filename,
                    "s3://huatai-rag/input/" + filename);
        }
    }

    static final class FakeDocumentParser implements DocumentParser {
        private RuntimeException failure;

        @Override
        public ParsedDocument parse(ParserRequest request) {
            if (failure != null) {
                throw failure;
            }
            ParsedChunk chunk = new ParsedChunk(
                    "chunk-1",
                    1,
                    "Uploaded sample content for indexing.",
                    "Uploaded sample content for indexing.",
                    List.of("Uploads"),
                    List.of(),
                    Map.of("source", request.fileName(), "chunk_id", "chunk-1"));
            return new ParsedDocument(
                    request.fileName(),
                    request.indexName(),
                    List.of(new ParsedPage(1, "Uploads", List.of("Uploads"))),
                    List.of(chunk),
                    List.of(),
                    "",
                    "aws-bda",
                    "2025-03-01");
        }
    }

    static final class FakeEmbeddingPort implements EmbeddingPort {
        @Override
        public List<List<Float>> embedAll(List<String> texts) {
            return List.of(List.of(0.1f, 0.2f, 0.3f));
        }
    }

    static final class FakeDocumentChunkWriter implements DocumentIngestionApplicationService.DocumentChunkWriter {
        @Override
        public void ensureIndexExists(String indexName, int embeddingDimension) {
        }

        @Override
        public void writeChunks(String indexName, List<ParsedChunk> chunks, List<List<Float>> embeddings) {
        }
    }

    static final class FakeDocumentRegistryPort implements DocumentRegistryPort {
        private final Map<String, DocumentFileRecord> filesByName = new LinkedHashMap<>();

        @Override
        public DocumentFileRecord saveDocument(DocumentFileRecord documentFileRecord) {
            DocumentFileRecord savedRecord = new DocumentFileRecord(
                    documentFileRecord.id() == null ? UUID.randomUUID() : documentFileRecord.id(),
                    documentFileRecord.filename(),
                    documentFileRecord.indexName(),
                    documentFileRecord.storagePath(),
                    documentFileRecord.status(),
                    documentFileRecord.createdAt() == null ? Instant.now() : documentFileRecord.createdAt(),
                    Instant.now());
            filesByName.put(savedRecord.filename(), savedRecord);
            return savedRecord;
        }

        @Override
        public IngestionJobRecord saveIngestionJob(IngestionJobRecord ingestionJobRecord) {
            return new IngestionJobRecord(
                    ingestionJobRecord.id() == null ? UUID.randomUUID() : ingestionJobRecord.id(),
                    ingestionJobRecord.documentFileId(),
                    ingestionJobRecord.indexName(),
                    ingestionJobRecord.status(),
                    ingestionJobRecord.createdAt() == null ? Instant.now() : ingestionJobRecord.createdAt(),
                    Instant.now());
        }

        @Override
        public Optional<DocumentFileRecord> findByFilename(String filename) {
            return Optional.ofNullable(filesByName.get(filename));
        }

        @Override
        public Optional<DocumentFileRecord> findByIndexName(String indexName) {
            return filesByName.values().stream()
                    .filter(record -> record.indexName().equals(indexName))
                    .findFirst();
        }

        @Override
        public List<DocumentFileRecord> listProcessedFiles() {
            return filesByName.values().stream()
                    .filter(record -> record.status() == IngestionStatus.COMPLETED)
                    .toList();
        }
    }
}
