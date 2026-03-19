package com.huatai.rag;

import org.junit.jupiter.api.Test;
import org.opensearch.client.RestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class RagApplicationTest {

    @MockBean
    private RestClient restClient;

    @Test
    void contextLoads() {
    }
}
