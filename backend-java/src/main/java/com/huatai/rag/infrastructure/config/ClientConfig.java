package com.huatai.rag.infrastructure.config;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.rest_client.RestClientTransport;
import org.opensearch.client.RestClient;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.util.Timeout;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient.Builder;
import software.amazon.awssdk.services.bedrockdataautomationruntime.BedrockDataAutomationRuntimeClient;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@EnableConfigurationProperties({
        AwsProperties.class,
        OpenSearchProperties.class,
        StorageProperties.class,
        RagProperties.class
})
public class ClientConfig {

    @Bean
    public DefaultCredentialsProvider awsCredentialsProvider() {
        return DefaultCredentialsProvider.create();
    }

    @Bean
    public BedrockRuntimeClient bedrockRuntimeClient(
            AwsProperties awsProperties,
            DefaultCredentialsProvider credentialsProvider) {
        return BedrockRuntimeClient.builder()
                .region(Region.of(awsProperties.getBedrockRegion()))
                .credentialsProvider(credentialsProvider)
                .build();
    }

    @Bean
    public BedrockAgentRuntimeClient bedrockAgentRuntimeClient(
            AwsProperties awsProperties,
            DefaultCredentialsProvider credentialsProvider) {
        return BedrockAgentRuntimeClient.builder()
                .region(Region.of(awsProperties.getRegion()))
                .credentialsProvider(credentialsProvider)
                .build();
    }

    @Bean
    public BedrockDataAutomationRuntimeClient bedrockDataAutomationRuntimeClient(
            AwsProperties awsProperties,
            DefaultCredentialsProvider credentialsProvider) {
        return BedrockDataAutomationRuntimeClient.builder()
                .region(Region.of(awsProperties.getBdaRegion()))
                .credentialsProvider(credentialsProvider)
                .build();
    }

    @Bean
    public S3Client s3Client(
            AwsProperties awsProperties,
            DefaultCredentialsProvider credentialsProvider) {
        return S3Client.builder()
                .region(Region.of(awsProperties.getRegion()))
                .credentialsProvider(credentialsProvider)
                .build();
    }

    @Bean(destroyMethod = "close")
    public RestClient openSearchRestClient(OpenSearchProperties properties) {
        HttpHost httpHost;
        try {
            httpHost = HttpHost.create(properties.getEndpoint());
        } catch (java.net.URISyntaxException exception) {
            throw new IllegalArgumentException("Invalid OpenSearch endpoint: " + properties.getEndpoint(), exception);
        }

        var builder = RestClient.builder(httpHost)
                .setRequestConfigCallback(requestConfigBuilder -> requestConfigBuilder
                        .setConnectTimeout(Timeout.ofMilliseconds(properties.getConnectTimeout().toMillis()))
                        .setResponseTimeout(Timeout.ofMilliseconds(properties.getSocketTimeout().toMillis())));

        if (StringUtils.hasText(properties.getUsername()) && StringUtils.hasText(properties.getPassword())) {
            String token = Base64.getEncoder()
                    .encodeToString((properties.getUsername() + ":" + properties.getPassword())
                            .getBytes(StandardCharsets.UTF_8));
            builder.setDefaultHeaders(new org.apache.hc.core5.http.Header[] {
                    new BasicHeader("Authorization", "Basic " + token)
            });
        }

        return builder.build();
    }

    @Bean(destroyMethod = "close")
    public OpenSearchTransport openSearchTransport(RestClient restClient) {
        return new RestClientTransport(restClient, new JacksonJsonpMapper());
    }

    @Bean
    public OpenSearchClient openSearchClient(OpenSearchTransport transport) {
        return new OpenSearchClient(transport);
    }

    @Bean
    public org.springframework.web.client.RestClient infrastructureRestClient(Builder builder) {
        return builder.build();
    }
}
