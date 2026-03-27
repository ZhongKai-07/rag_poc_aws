package com.huatai.rag.infrastructure.opensearch;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("unchecked")
class MetadataFilterQueryTest {

    @Test
    void builds_knn_query_without_filter_when_null() {
        var body = OpenSearchQueryBuilder.buildVectorQuery(
                List.of(0.1f, 0.2f), 5, null);
        assertThat(body).containsKey("query");
        var query = (Map<String, Object>) body.get("query");
        assertThat(query).containsKey("knn");
        assertThat(query).doesNotContainKey("bool");
    }

    @Test
    void builds_knn_query_without_filter_when_empty() {
        var body = OpenSearchQueryBuilder.buildVectorQuery(
                List.of(0.1f, 0.2f), 5, Map.of());
        var query = (Map<String, Object>) body.get("query");
        assertThat(query).containsKey("knn");
        assertThat(query).doesNotContainKey("bool");
    }

    @Test
    void builds_bool_knn_filter_query_when_filters_present() {
        var filters = Map.of("counterparty", "HSBC", "agreement_type", "ISDA_CSA");
        var body = OpenSearchQueryBuilder.buildVectorQuery(
                List.of(0.1f, 0.2f), 5, filters);
        var query = (Map<String, Object>) body.get("query");
        assertThat(query).containsKey("bool");
        var bool = (Map<String, Object>) query.get("bool");
        assertThat(bool).containsKey("must");
        assertThat(bool).containsKey("filter");
        var filterList = (List<?>) bool.get("filter");
        assertThat(filterList).hasSize(2);
    }
}
