package com.huatai.rag.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huatai.rag.application.admin.ParseResultQueryApplicationService;
import com.huatai.rag.application.common.ContextAssemblyService;
import com.huatai.rag.application.history.QuestionHistoryApplicationService;
import com.huatai.rag.application.ingestion.DocumentIngestionApplicationService;
import com.huatai.rag.application.registry.ProcessedFileQueryApplicationService;
import com.huatai.rag.application.rag.RagQueryApplicationService;
import com.huatai.rag.domain.bda.BdaParseResultPort;
import com.huatai.rag.domain.document.DocumentRegistryPort;
import com.huatai.rag.domain.history.QuestionHistoryPort;
import com.huatai.rag.domain.parser.DocumentParser;
import com.huatai.rag.domain.rag.AnswerGenerationPort;
import com.huatai.rag.domain.retrieval.EmbeddingPort;
import com.huatai.rag.domain.retrieval.RetrievalPort;
import com.huatai.rag.domain.retrieval.RerankPort;
import com.huatai.rag.infrastructure.bda.BdaClient;
import com.huatai.rag.infrastructure.bda.BdaDocumentParserAdapter;
import com.huatai.rag.infrastructure.bda.BdaResultMapper;
import com.huatai.rag.infrastructure.bedrock.BedrockAnswerGenerationAdapter;
import com.huatai.rag.infrastructure.bedrock.BedrockEmbeddingAdapter;
import com.huatai.rag.infrastructure.bedrock.BedrockRerankAdapter;
import com.huatai.rag.infrastructure.bedrock.PromptTemplateFactory;
import com.huatai.rag.infrastructure.opensearch.OpenSearchChunkMapper;
import com.huatai.rag.infrastructure.opensearch.OpenSearchDocumentChunkWriter;
import com.huatai.rag.infrastructure.opensearch.OpenSearchDocumentWriter;
import com.huatai.rag.infrastructure.opensearch.OpenSearchIndexManager;
import com.huatai.rag.infrastructure.opensearch.OpenSearchRetrievalAdapter;
import com.huatai.rag.infrastructure.persistence.BdaParseResultPersistenceAdapter;
import com.huatai.rag.infrastructure.persistence.DocumentRegistryPersistenceAdapter;
import com.huatai.rag.infrastructure.persistence.QuestionHistoryPersistenceAdapter;
import com.huatai.rag.infrastructure.persistence.repository.BdaParseResultJpaRepository;
import com.huatai.rag.infrastructure.persistence.repository.DocumentFileJpaRepository;
import com.huatai.rag.infrastructure.persistence.repository.IngestionJobJpaRepository;
import com.huatai.rag.infrastructure.persistence.repository.QuestionHistoryJpaRepository;
import com.huatai.rag.infrastructure.storage.S3DocumentStorageAdapter;
import com.huatai.rag.infrastructure.support.RetryUtils;
import org.opensearch.client.RestClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.bedrockdataautomationruntime.BedrockDataAutomationRuntimeClient;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class ApplicationWiringConfig {

    @Bean
    public RetryUtils retryUtils() {
        return new RetryUtils();
    }

    @Bean
    public PromptTemplateFactory promptTemplateFactory() {
        return new PromptTemplateFactory();
    }

    @Bean
    public EmbeddingPort embeddingPort(
            BedrockRuntimeClient bedrockRuntimeClient,
            ObjectMapper objectMapper,
            RagProperties ragProperties,
            RetryUtils retryUtils) {
        return new BedrockEmbeddingAdapter(bedrockRuntimeClient, objectMapper, ragProperties, retryUtils);
    }

    @Bean
    public RerankPort rerankPort(
            BedrockAgentRuntimeClient bedrockAgentRuntimeClient,
            RagProperties ragProperties,
            AwsProperties awsProperties,
            RetryUtils retryUtils) {
        return new BedrockRerankAdapter(bedrockAgentRuntimeClient, ragProperties, awsProperties, retryUtils);
    }

    @Bean
    public AnswerGenerationPort answerGenerationPort(
            BedrockRuntimeClient bedrockRuntimeClient,
            RagProperties ragProperties,
            PromptTemplateFactory promptTemplateFactory,
            RetryUtils retryUtils) {
        return new BedrockAnswerGenerationAdapter(
                bedrockRuntimeClient,
                ragProperties,
                promptTemplateFactory,
                retryUtils);
    }

    @Bean
    public OpenSearchChunkMapper openSearchChunkMapper() {
        return new OpenSearchChunkMapper();
    }

    @Bean
    public OpenSearchIndexManager openSearchIndexManager(RestClient restClient, ObjectMapper objectMapper) {
        return new OpenSearchIndexManager(restClient, objectMapper);
    }

    @Bean
    public OpenSearchDocumentWriter openSearchDocumentWriter(
            RestClient restClient,
            ObjectMapper objectMapper,
            OpenSearchChunkMapper openSearchChunkMapper,
            OpenSearchIndexManager openSearchIndexManager) {
        return new OpenSearchDocumentWriter(restClient, objectMapper, openSearchChunkMapper, openSearchIndexManager);
    }

    @Bean
    public DocumentIngestionApplicationService.DocumentChunkWriter documentChunkWriter(
            OpenSearchIndexManager openSearchIndexManager,
            OpenSearchDocumentWriter openSearchDocumentWriter) {
        return new OpenSearchDocumentChunkWriter(openSearchIndexManager, openSearchDocumentWriter);
    }

    @Bean
    public RetrievalPort retrievalPort(RestClient restClient, ObjectMapper objectMapper, EmbeddingPort embeddingPort) {
        return new OpenSearchRetrievalAdapter(restClient, objectMapper, embeddingPort);
    }

    @Bean
    public DocumentRegistryPort documentRegistryPort(
            DocumentFileJpaRepository documentFileJpaRepository,
            IngestionJobJpaRepository ingestionJobJpaRepository) {
        return new DocumentRegistryPersistenceAdapter(documentFileJpaRepository, ingestionJobJpaRepository);
    }

    @Bean
    public QuestionHistoryPort questionHistoryPort(QuestionHistoryJpaRepository questionHistoryJpaRepository) {
        return new QuestionHistoryPersistenceAdapter(questionHistoryJpaRepository);
    }

    @Bean
    public DocumentIngestionApplicationService.DocumentStorage documentStorage(
            S3Client s3Client,
            StorageProperties storageProperties) {
        return new S3DocumentStorageAdapter(s3Client, storageProperties);
    }

    @Bean
    public BdaResultMapper bdaResultMapper() {
        return new BdaResultMapper();
    }

    @Bean
    public BdaClient bdaClient(
            BedrockDataAutomationRuntimeClient bedrockDataAutomationRuntimeClient,
            S3Client s3Client,
            ObjectMapper objectMapper,
            AwsProperties awsProperties) {
        return BdaClient.aws(
                bedrockDataAutomationRuntimeClient,
                s3Client,
                objectMapper,
                awsProperties.getBdaProjectArn(),
                awsProperties.getBdaProfileArn(),
                awsProperties.getBdaStage(),
                awsProperties.getBdaMaxPollAttempts(),
                awsProperties.getBdaPollInterval());
    }

    @Bean
    public DocumentParser documentParser(
            BdaClient bdaClient,
            BdaResultMapper bdaResultMapper,
            StorageProperties storageProperties) {
        return new BdaDocumentParserAdapter(bdaClient, bdaResultMapper, storageProperties);
    }

    @Bean
    public ContextAssemblyService contextAssemblyService() {
        return new ContextAssemblyService();
    }

    @Bean
    public RagQueryApplicationService ragQueryApplicationService(
            RetrievalPort retrievalPort,
            RerankPort rerankPort,
            AnswerGenerationPort answerGenerationPort,
            QuestionHistoryPort questionHistoryPort,
            ContextAssemblyService contextAssemblyService) {
        return new RagQueryApplicationService.Default(
                retrievalPort,
                rerankPort,
                answerGenerationPort,
                questionHistoryPort,
                contextAssemblyService);
    }

    @Bean
    public BdaParseResultPort bdaParseResultPort(BdaParseResultJpaRepository bdaParseResultJpaRepository) {
        return new BdaParseResultPersistenceAdapter(bdaParseResultJpaRepository);
    }

    @Bean
    public DocumentIngestionApplicationService documentIngestionApplicationService(
            DocumentIngestionApplicationService.DocumentStorage documentStorage,
            DocumentRegistryPort documentRegistryPort,
            DocumentParser documentParser,
            EmbeddingPort embeddingPort,
            DocumentIngestionApplicationService.DocumentChunkWriter documentChunkWriter,
            BdaParseResultPort bdaParseResultPort) {
        return new DocumentIngestionApplicationService.Default(
                documentStorage,
                documentRegistryPort,
                documentParser,
                embeddingPort,
                documentChunkWriter,
                bdaParseResultPort);
    }

    @Bean
    public ProcessedFileQueryApplicationService processedFileQueryApplicationService(DocumentRegistryPort documentRegistryPort) {
        return new ProcessedFileQueryApplicationService.Default(documentRegistryPort);
    }

    @Bean
    public QuestionHistoryApplicationService questionHistoryApplicationService(QuestionHistoryPort questionHistoryPort) {
        return new QuestionHistoryApplicationService.Default(questionHistoryPort);
    }

    @Bean
    public ParseResultQueryApplicationService parseResultQueryApplicationService(
            BdaParseResultPort bdaParseResultPort,
            DocumentRegistryPort documentRegistryPort,
            S3Client s3Client,
            RestClient openSearchRestClient,
            ObjectMapper objectMapper) {
        return new ParseResultQueryApplicationService(
                bdaParseResultPort,
                documentRegistryPort,
                s3Client,
                openSearchRestClient,
                objectMapper);
    }
}
