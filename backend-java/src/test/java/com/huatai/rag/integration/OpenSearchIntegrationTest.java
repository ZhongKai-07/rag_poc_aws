package com.huatai.rag.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.huatai.rag.domain.parser.ParsedAsset;
import com.huatai.rag.domain.parser.ParsedChunk;
import com.huatai.rag.domain.retrieval.RetrievalRequest;
import com.huatai.rag.domain.retrieval.RetrievalResult;
import com.huatai.rag.domain.retrieval.RetrievedDocument;
import com.huatai.rag.domain.retrieval.SearchMethod;
import com.huatai.rag.infrastructure.opensearch.OpenSearchChunkMapper;
import com.huatai.rag.infrastructure.opensearch.OpenSearchIndexManager;
import com.huatai.rag.infrastructure.opensearch.OpenSearchRetrievalAdapter;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpenSearchIntegrationTest {

    @Test
    void chunkMapperPreservesCompatibleFieldNames() {
        ParsedChunk chunk = new ParsedChunk(
                "chunk-1",
                3,
                "Full paragraph content",
                "Short sentence content",
                List.of("Section A", "Subsection B"),
                List.of(new ParsedAsset("asset-1", "image", "fig-1", 3)),
                Map.of("source", "PRC Client.pdf", "chunk_id", "chunk-1"));

        Map<String, Object> document = new OpenSearchChunkMapper()
                .toDocument(chunk, List.of(0.1f, 0.2f, 0.3f));

        assertThat(document).containsKeys("sentence_vector", "paragraph", "sentence", "metadata");
        assertThat(document.get("paragraph")).isEqualTo("Full paragraph content");
        assertThat(document.get("sentence")).isEqualTo("Short sentence content");
        assertThat(cast(document.get("metadata")))
                .containsEntry("source", "PRC Client.pdf")
                .containsEntry("page_number", 3);
    }

    @Test
    void indexMappingUsesExplicitCompatibleFields() {
        Map<String, Object> mapping = new OpenSearchIndexManager(null, new com.fasterxml.jackson.databind.ObjectMapper())
                .buildIndexMapping(1536);

        assertThat(mapping).containsKey("settings");
        assertThat(mapping).containsKey("mappings");

        Map<String, Object> mappings = cast(mapping.get("mappings"));
        Map<String, Object> properties = cast(mappings.get("properties"));
        assertThat(properties).containsKeys("sentence_vector", "paragraph", "sentence", "metadata");
    }

    @Test
    void mixRetrievalCombinesVectorAndTextResultsWithoutDuplicates() {
        RetrievedDocument shared = new RetrievedDocument(
                "shared paragraph",
                90.0,
                null,
                Map.of("source", "shared.pdf"));
        RetrievedDocument vectorOnly = new RetrievedDocument(
                "vector paragraph",
                88.0,
                null,
                Map.of("source", "vector.pdf"));
        RetrievedDocument textOnly = new RetrievedDocument(
                "text paragraph",
                77.0,
                null,
                Map.of("source", "text.pdf"));

        OpenSearchRetrievalAdapter adapter = new OpenSearchRetrievalAdapter(new FakeSearchGateway(
                List.of(shared, vectorOnly),
                List.of(shared, textOnly)));

        RetrievalResult result = adapter.retrieve(new RetrievalRequest(
                List.of("2f295fa6", "32a592c0"),
                "onboarding agreement",
                SearchMethod.MIX,
                2,
                2,
                0.0,
                0.0));

        assertThat(result.recallDocuments())
                .extracting(RetrievedDocument::pageContent)
                .containsExactly("shared paragraph", "vector paragraph", "text paragraph");
        assertThat(result.rerankDocuments())
                .extracting(RetrievedDocument::pageContent)
                .containsExactly("shared paragraph", "vector paragraph", "text paragraph");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> cast(Object value) {
        return (Map<String, Object>) value;
    }

    private static final class FakeSearchGateway implements OpenSearchRetrievalAdapter.SearchGateway {
        private final List<RetrievedDocument> vectorResults;
        private final List<RetrievedDocument> textResults;

        private FakeSearchGateway(List<RetrievedDocument> vectorResults, List<RetrievedDocument> textResults) {
            this.vectorResults = vectorResults;
            this.textResults = textResults;
        }

        @Override
        public List<RetrievedDocument> vectorSearch(List<String> indexNames, String query, int limit, double scoreThreshold) {
            return vectorResults;
        }

        @Override
        public List<RetrievedDocument> textSearch(List<String> indexNames, String query, int limit, double scoreThreshold) {
            return textResults;
        }
    }
}
