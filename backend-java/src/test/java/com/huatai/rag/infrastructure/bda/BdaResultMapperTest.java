package com.huatai.rag.infrastructure.bda;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huatai.rag.domain.parser.ParsedChunk;
import com.huatai.rag.domain.parser.ParsedDocument;
import com.huatai.rag.domain.parser.ParserRequest;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.Test;

class BdaResultMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void mapsBdaPayloadIntoNormalizedParsedDocument() throws IOException {
        JsonNode payload = readFixture();

        ParsedDocument parsedDocument = new BdaResultMapper()
                .map(payload, "sample-financing.pdf", "c0ffee12", "s3://huatai-rag/_bda_output/c0ffee12.json");

        assertThat(parsedDocument.fileName()).isEqualTo("sample-financing.pdf");
        assertThat(parsedDocument.indexName()).isEqualTo("c0ffee12");
        assertThat(parsedDocument.parserProvenance()).isEqualTo("aws-bda:2025-03-01");
        assertThat(parsedDocument.pages()).hasSize(2);
        assertThat(parsedDocument.pages().get(0).pageNumber()).isEqualTo(1);
        assertThat(parsedDocument.pages().get(0).sectionPath()).containsExactly("Executive Summary");
        assertThat(parsedDocument.pages().get(1).sectionPath()).containsExactly("Risk Factors", "Collateral");
        assertThat(parsedDocument.assets()).hasSize(1);
        assertThat(parsedDocument.assets().get(0).reference()).isEqualTo("s3://huatai-rag/output/figure-1.png");

        ParsedChunk firstChunk = parsedDocument.chunks().get(0);
        assertThat(firstChunk.chunkId()).isEqualTo("chunk-1");
        assertThat(firstChunk.pageNumber()).isEqualTo(1);
        assertThat(firstChunk.paragraphText())
                .isEqualTo("The client agreement sets out onboarding steps and required approvals.");
        assertThat(firstChunk.sentenceText()).isEqualTo("The client agreement sets out onboarding steps.");
        assertThat(firstChunk.sectionPath()).containsExactly("Executive Summary");
        assertThat(firstChunk.metadata())
                .containsEntry("source", "sample-financing.pdf")
                .containsEntry("chunk_id", "chunk-1")
                .containsEntry("parser_type", "aws-bda")
                .containsEntry("parser_version", "2025-03-01");

        ParsedChunk secondChunk = parsedDocument.chunks().get(1);
        assertThat(secondChunk.sectionPath()).containsExactly("Risk Factors", "Collateral");
        assertThat(secondChunk.assets())
                .extracting(asset -> asset.reference())
                .containsExactly("s3://huatai-rag/output/figure-1.png");
        assertThat(secondChunk.sentenceText()).isEqualTo("Collateral terms are summarized in Figure 1.");

        assertThat(parsedDocument.s3OutputPath())
                .isEqualTo("s3://huatai-rag/_bda_output/c0ffee12.json");
        assertThat(parsedDocument.parserType()).isEqualTo("aws-bda");
        assertThat(parsedDocument.parserVersion()).isEqualTo("2025-03-01");
        assertThat(parsedDocument.parserProvenance()).isEqualTo("aws-bda:2025-03-01");
    }

    @Test
    void bdaClientPollsUntilSuccessAndReturnsOutputPayload() {
        FakeGateway gateway = new FakeGateway(
                List.of(
                        new BdaClient.InvocationStatus("IN_PROGRESS", null, null, "IN_PROGRESS"),
                        new BdaClient.InvocationStatus("SUCCESS", readFixtureUnchecked())));

        BdaClient client = new BdaClient(gateway, 5, java.time.Duration.ZERO);

        JsonNode result = client.parse("s3://input/sample-financing.pdf", "s3://output/");

        assertThat(gateway.startedInputs()).containsExactly("s3://input/sample-financing.pdf");
        assertThat(gateway.startedOutputs()).containsExactly("s3://output/");
        assertThat(gateway.polledInvocationArns()).containsExactly(
                "arn:aws:bedrock:invocation/sample",
                "arn:aws:bedrock:invocation/sample");
        assertThat(result.path("document").path("pages")).hasSize(2);
    }

    @Test
    void bdaClientFollowsStandardOutputPathWhenStatusOutputResolvesToJobMetadata() throws IOException {
        JsonNode jobMetadata = objectMapper.readTree("""
                {
                  "job_id": "de8e9ec5-07de-4b62-9af2-40c362b590d3",
                  "job_status": "PROCESSED",
                  "semantic_modality": "DOCUMENT",
                  "output_metadata": [
                    {
                      "segment_metadata": [
                        {
                          "standard_output_path": "s3://huatai-rag-docs/_bda_output/63e9a42e.json/de8e9ec5-07de-4b62-9af2-40c362b590d3/0/standard_output/0/result.json"
                        }
                      ]
                    }
                  ]
                }
                """);
        JsonNode resultPayload = objectMapper.readTree("""
                {
                  "document": {
                    "representation": {
                      "text": "Fallback document text"
                    }
                  },
                  "pages": [
                    {
                      "id": "page-uuid-1",
                      "page_index": 0,
                      "representation": {
                        "text": "First paragraph. Second sentence."
                      }
                    }
                  ]
                }
                """);
        FakeGateway gateway = new FakeGateway(List.of(new BdaClient.InvocationStatus(
                "SUCCESS",
                "s3://huatai-rag-docs/_bda_output/63e9a42e.json/de8e9ec5-07de-4b62-9af2-40c362b590d3/job_metadata.json")));
        gateway.fetchedOutputs.put(
                "s3://huatai-rag-docs/_bda_output/63e9a42e.json/de8e9ec5-07de-4b62-9af2-40c362b590d3/job_metadata.json",
                jobMetadata);
        gateway.fetchedOutputs.put(
                "s3://huatai-rag-docs/_bda_output/63e9a42e.json/de8e9ec5-07de-4b62-9af2-40c362b590d3/0/standard_output/0/result.json",
                resultPayload);
        BdaClient client = new BdaClient(gateway, 3, java.time.Duration.ZERO);

        JsonNode result = client.parse(
                "s3://input/sample-financing.pdf",
                "s3://huatai-rag-docs/_bda_output/63e9a42e.json");

        assertThat(result).isEqualTo(resultPayload);
        assertThat(gateway.fetchedOutputUris()).containsExactly(
                "s3://huatai-rag-docs/_bda_output/63e9a42e.json/de8e9ec5-07de-4b62-9af2-40c362b590d3/job_metadata.json",
                "s3://huatai-rag-docs/_bda_output/63e9a42e.json/de8e9ec5-07de-4b62-9af2-40c362b590d3/0/standard_output/0/result.json");
    }

    @Test
    void documentParserAdapterMapsClientPayloadIntoDomainDocument() {
        JsonNode payload = readFixtureUnchecked();
        FakeGateway gateway = new FakeGateway(List.of(new BdaClient.InvocationStatus("SUCCESS", payload)));
        BdaClient client = new BdaClient(gateway,
                3,
                java.time.Duration.ZERO);
        com.huatai.rag.infrastructure.config.StorageProperties storageProperties =
                new com.huatai.rag.infrastructure.config.StorageProperties();
        storageProperties.setDocumentBucket("huatai-rag-docs");
        storageProperties.setBdaOutputPrefix("parsed-output");
        BdaDocumentParserAdapter adapter = new BdaDocumentParserAdapter(client, new BdaResultMapper(), storageProperties);

        ParsedDocument parsedDocument = adapter.parse(new ParserRequest(
                "sample-financing.pdf",
                "c0ffee12",
                "s3://input/sample-financing.pdf"));

        assertThat(parsedDocument.indexName()).isEqualTo("c0ffee12");
        assertThat(parsedDocument.chunks()).hasSize(2);
        assertThat(parsedDocument.assets()).hasSize(1);
        assertThat(gateway.startedOutputs())
                .containsExactly("s3://huatai-rag-docs/parsed-output/c0ffee12.json");
    }

    @Test
    void documentParserAdapterUsesConfiguredOutputPrefixInsteadOfInputChildPath() {
        JsonNode payload = readFixtureUnchecked();
        FakeGateway gateway = new FakeGateway(List.of(new BdaClient.InvocationStatus("SUCCESS", payload)));
        BdaClient client = new BdaClient(gateway, 3, java.time.Duration.ZERO);
        com.huatai.rag.infrastructure.config.StorageProperties storageProperties =
                new com.huatai.rag.infrastructure.config.StorageProperties();
        storageProperties.setDocumentBucket("huatai-rag-docs");
        storageProperties.setBdaOutputPrefix("/_bda_output/");
        BdaDocumentParserAdapter adapter = new BdaDocumentParserAdapter(client, new BdaResultMapper(), storageProperties);

        adapter.parse(new ParserRequest(
                "sample-financing.pdf",
                "c0ffee12",
                "s3://huatai-rag-docs/uploads/sample-financing.pdf"));

        assertThat(gateway.startedOutputs()).containsExactly("s3://huatai-rag-docs/_bda_output/c0ffee12.json");
    }

    @Test
    void derivesChunksFromTopLevelPageTextWhenLegacyChunkArrayIsMissing() throws IOException {
        JsonNode payload = objectMapper.readTree("""
                {
                  "metadata": {
                    "parser": {
                      "type": "aws-bda",
                      "version": "2026-03-20"
                    }
                  },
                  "document": {
                    "representation": {
                      "text": "Fallback document text"
                    },
                    "description": {},
                    "summary": {},
                    "statistics": {}
                  },
                  "pages": [
                    {
                      "id": "page-uuid-1",
                      "page_index": 0,
                      "representation": {
                        "text": "First paragraph. Second sentence."
                      },
                      "statistics": {},
                      "asset_metadata": []
                    },
                    {
                      "id": "page-uuid-2",
                      "page_index": 1,
                      "representation": {
                        "text": "Only paragraph on page two"
                      },
                      "statistics": {},
                      "asset_metadata": []
                    }
                  ],
                  "elements": []
                }
                """);

        ParsedDocument parsedDocument = new BdaResultMapper()
                .map(payload, "sample-financing.pdf", "c0ffee12", "s3://huatai-rag/_bda_output/c0ffee12.json");

        assertThat(parsedDocument.pages()).hasSize(2);
        assertThat(parsedDocument.pages().get(0).pageNumber()).isEqualTo(1);
        assertThat(parsedDocument.pages().get(0).text()).isEqualTo("First paragraph. Second sentence.");
        assertThat(parsedDocument.pages().get(1).pageNumber()).isEqualTo(2);
        assertThat(parsedDocument.pages().get(1).text()).isEqualTo("Only paragraph on page two");
        assertThat(parsedDocument.chunks()).hasSize(2);
        assertThat(parsedDocument.chunks().get(0).chunkId()).isEqualTo("page-uuid-1");
        assertThat(parsedDocument.chunks().get(0).pageNumber()).isEqualTo(1);
        assertThat(parsedDocument.chunks().get(0).paragraphText()).isEqualTo("First paragraph. Second sentence.");
        assertThat(parsedDocument.chunks().get(0).sentenceText()).isEqualTo("First paragraph.");
        assertThat(parsedDocument.chunks().get(0).metadata())
                .containsEntry("source", "sample-financing.pdf")
                .containsEntry("chunk_id", "page-uuid-1")
                .containsEntry("parser_type", "aws-bda")
                .containsEntry("parser_version", "2026-03-20");
        assertThat(parsedDocument.chunks().get(1).chunkId()).isEqualTo("page-uuid-2");
        assertThat(parsedDocument.chunks().get(1).pageNumber()).isEqualTo(2);
        assertThat(parsedDocument.chunks().get(1).paragraphText()).isEqualTo("Only paragraph on page two");
        assertThat(parsedDocument.chunks().get(1).sentenceText()).isEqualTo("Only paragraph on page two");
    }

    private JsonNode readFixture() throws IOException {
        try (InputStream inputStream = getClass()
                .getResourceAsStream("/fixtures/parser/bda-sample-response.json")) {
            return objectMapper.readTree(inputStream);
        }
    }

    private JsonNode readFixtureUnchecked() {
        try {
            return readFixture();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load fixture", exception);
        }
    }

    private static final class FakeGateway implements BdaClient.Gateway {
        private final List<BdaClient.InvocationStatus> statuses;
        private final java.util.List<String> startedInputs = new java.util.ArrayList<>();
        private final java.util.List<String> startedOutputs = new java.util.ArrayList<>();
        private final java.util.List<String> polledInvocationArns = new java.util.ArrayList<>();
        private final java.util.Map<String, JsonNode> fetchedOutputs = new java.util.LinkedHashMap<>();
        private final java.util.List<String> fetchedOutputUris = new java.util.ArrayList<>();
        private int pollIndex;

        private FakeGateway(List<BdaClient.InvocationStatus> statuses) {
            this.statuses = statuses;
        }

        @Override
        public String startParsing(String inputUri, String outputUri) {
            startedInputs.add(inputUri);
            startedOutputs.add(outputUri);
            return "arn:aws:bedrock:invocation/sample";
        }

        @Override
        public BdaClient.InvocationStatus poll(String invocationArn) {
            polledInvocationArns.add(invocationArn);
            return statuses.get(Math.min(pollIndex++, statuses.size() - 1));
        }

        @Override
        public JsonNode fetchOutput(String outputUri) {
            fetchedOutputUris.add(outputUri);
            JsonNode payload = fetchedOutputs.get(outputUri);
            if (payload == null) {
                throw new IllegalStateException("No fake payload configured for " + outputUri);
            }
            return payload;
        }

        private java.util.List<String> startedInputs() {
            return startedInputs;
        }

        private java.util.List<String> startedOutputs() {
            return startedOutputs;
        }

        private java.util.List<String> polledInvocationArns() {
            return polledInvocationArns;
        }

        private java.util.List<String> fetchedOutputUris() {
            return fetchedOutputUris;
        }
    }
}
