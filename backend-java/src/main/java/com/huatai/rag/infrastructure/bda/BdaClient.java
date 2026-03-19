package com.huatai.rag.infrastructure.bda;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockdataautomationruntime.BedrockDataAutomationRuntimeClient;
import software.amazon.awssdk.services.bedrockdataautomationruntime.model.DataAutomationConfiguration;
import software.amazon.awssdk.services.bedrockdataautomationruntime.model.GetDataAutomationStatusRequest;
import software.amazon.awssdk.services.bedrockdataautomationruntime.model.GetDataAutomationStatusResponse;
import software.amazon.awssdk.services.bedrockdataautomationruntime.model.InvokeDataAutomationAsyncRequest;
import software.amazon.awssdk.services.bedrockdataautomationruntime.model.OutputConfiguration;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

public class BdaClient {

    private final Gateway gateway;
    private final int maxPollAttempts;
    private final Duration pollInterval;

    public BdaClient(Gateway gateway, int maxPollAttempts, Duration pollInterval) {
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.maxPollAttempts = maxPollAttempts;
        this.pollInterval = Objects.requireNonNull(pollInterval, "pollInterval");
    }

    public JsonNode parse(String inputUri, String outputUri) {
        String invocationArn = gateway.startParsing(inputUri, outputUri);
        for (int attempt = 0; attempt < maxPollAttempts; attempt++) {
            InvocationStatus status = gateway.poll(invocationArn);
            if (status.isSuccess()) {
                if (status.payload() != null) {
                    return status.payload();
                }
                String resolvedOutputUri = status.outputUri() == null || status.outputUri().isBlank()
                        ? outputUri
                        : status.outputUri();
                return gateway.fetchOutput(resolvedOutputUri);
            }
            if (status.isFailure()) {
                throw new IllegalStateException("BDA parsing failed: " + status.errorMessage());
            }
            sleepBeforeNextPoll(attempt);
        }
        throw new IllegalStateException("BDA parsing timed out after " + maxPollAttempts + " polls");
    }

    private void sleepBeforeNextPoll(int attempt) {
        if (attempt >= maxPollAttempts - 1 || pollInterval.isZero() || pollInterval.isNegative()) {
            return;
        }
        try {
            Thread.sleep(pollInterval.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while polling BDA status", exception);
        }
    }

    public static BdaClient aws(
            BedrockDataAutomationRuntimeClient runtimeClient,
            S3Client s3Client,
            ObjectMapper objectMapper,
            String dataAutomationProjectArn,
            String dataAutomationProfileArn,
            String stage,
            int maxPollAttempts,
            Duration pollInterval) {
        return new BdaClient(
                new AwsGateway(
                        runtimeClient,
                        s3Client,
                        objectMapper,
                        dataAutomationProjectArn,
                        dataAutomationProfileArn,
                        stage),
                maxPollAttempts,
                pollInterval);
    }

    public interface Gateway {
        String startParsing(String inputUri, String outputUri);

        InvocationStatus poll(String invocationArn);

        default JsonNode fetchOutput(String outputUri) {
            throw new UnsupportedOperationException("Output fetching is not supported by this gateway");
        }
    }

    public record InvocationStatus(
            String status,
            String outputUri,
            JsonNode payload,
            String errorMessage) {

        public InvocationStatus {
            status = status == null ? "" : status;
            errorMessage = errorMessage == null ? status : errorMessage;
        }

        public InvocationStatus(String status, JsonNode payload) {
            this(status, null, payload, status);
        }

        public InvocationStatus(String status, String outputUri) {
            this(status, outputUri, null, status);
        }

        public boolean isSuccess() {
            return normalize(status).equals("SUCCESS");
        }

        public boolean isFailure() {
            String normalized = normalize(status);
            return normalized.equals("FAILED")
                    || normalized.equals("CLIENT_ERROR")
                    || normalized.equals("SERVICE_ERROR");
        }

        private String normalize(String rawStatus) {
            return rawStatus.replace('-', '_').trim().toUpperCase();
        }
    }

    private static final class AwsGateway implements Gateway {
        private final BedrockDataAutomationRuntimeClient runtimeClient;
        private final S3Client s3Client;
        private final ObjectMapper objectMapper;
        private final String dataAutomationProjectArn;
        private final String dataAutomationProfileArn;
        private final String stage;

        private AwsGateway(
                BedrockDataAutomationRuntimeClient runtimeClient,
                S3Client s3Client,
                ObjectMapper objectMapper,
                String dataAutomationProjectArn,
                String dataAutomationProfileArn,
                String stage) {
            this.runtimeClient = Objects.requireNonNull(runtimeClient, "runtimeClient");
            this.s3Client = Objects.requireNonNull(s3Client, "s3Client");
            this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
            this.dataAutomationProjectArn = dataAutomationProjectArn;
            this.dataAutomationProfileArn = dataAutomationProfileArn;
            this.stage = stage;
        }

        @Override
        public String startParsing(String inputUri, String outputUri) {
            String projectArn = requireText(dataAutomationProjectArn, "dataAutomationProjectArn");
            String profileArn = requireText(dataAutomationProfileArn, "dataAutomationProfileArn");
            String resolvedStage = requireText(stage, "stage");
            return runtimeClient.invokeDataAutomationAsync(InvokeDataAutomationAsyncRequest.builder()
                            .clientToken(UUID.randomUUID().toString())
                            .inputConfiguration(input -> input.s3Uri(inputUri))
                            .outputConfiguration(output -> output.s3Uri(outputUri))
                            .dataAutomationProfileArn(profileArn)
                            .dataAutomationConfiguration(DataAutomationConfiguration.builder()
                                    .dataAutomationProjectArn(projectArn)
                                    .stage(resolvedStage)
                                    .build())
                            .build())
                    .invocationArn();
        }

        @Override
        public InvocationStatus poll(String invocationArn) {
            GetDataAutomationStatusResponse response = runtimeClient.getDataAutomationStatus(GetDataAutomationStatusRequest.builder()
                    .invocationArn(invocationArn)
                    .build());
            OutputConfiguration outputConfiguration = response.outputConfiguration();
            String outputUri = outputConfiguration == null ? null : outputConfiguration.s3Uri();
            return new InvocationStatus(
                    response.statusAsString(),
                    outputUri,
                    null,
                    response.errorMessage());
        }

        @Override
        public JsonNode fetchOutput(String outputUri) {
            S3Location location = S3Location.parse(outputUri);
            ResponseBytes<?> response = s3Client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(location.bucket())
                    .key(location.key())
                    .build());
            try {
                return objectMapper.readTree(response.asByteArray());
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to parse BDA output payload from " + outputUri, exception);
            }
        }

        private static String requireText(String value, String fieldName) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(fieldName + " must not be blank");
            }
            return value;
        }
    }

    private record S3Location(String bucket, String key) {
        private static S3Location parse(String uri) {
            if (uri == null || !uri.startsWith("s3://")) {
                throw new IllegalArgumentException("Expected S3 URI but received: " + uri);
            }
            String withoutScheme = uri.substring("s3://".length());
            int delimiterIndex = withoutScheme.indexOf('/');
            if (delimiterIndex < 0 || delimiterIndex == withoutScheme.length() - 1) {
                throw new IllegalArgumentException("Expected S3 URI with bucket and key but received: " + uri);
            }
            return new S3Location(
                    withoutScheme.substring(0, delimiterIndex),
                    withoutScheme.substring(delimiterIndex + 1));
        }
    }
}
