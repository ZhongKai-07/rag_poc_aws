# Backend Java

Spring Boot + Spring AI migration target for the existing Python RAG backend.

## Goals

- Keep the React frontend unchanged.
- Preserve the Python API contract and behavior during migration.
- Implement the new backend under layered boundaries:
  - `api`
  - `application`
  - `domain`
  - `infrastructure`

## Runtime

- Java 17 (temporary local constraint for this migration worktree)
- Spring Boot
- Spring AI
- PostgreSQL
- Flyway
- AWS OpenSearch
- AWS Bedrock
- AWS BDA

## Local Test Bootstrap

Run the bootstrap test:

```bash
mvn -f backend-java/pom.xml -q -Dtest=RagApplicationTest test
```

The service port is pinned to `8001` for frontend compatibility.

## Local Verification

When Maven needs to resolve newer plugins or dependencies in this environment, add:

```bash
-Dmaven.repo.local=%USERPROFILE%\.m2\repository
```

Current milestone verification commands:

```bash
mvn -f backend-java/pom.xml -q "-Dmaven.repo.local=%USERPROFILE%\.m2\repository" "-Dspring.profiles.active=test" "-Dtest=RagApplicationTest" test
mvn -f backend-java/pom.xml -q "-Dmaven.repo.local=%USERPROFILE%\.m2\repository" "-Dspring.profiles.active=test" "-Dtest=PostgresIntegrationTest" test
mvn -f backend-java/pom.xml -q "-Dmaven.repo.local=%USERPROFILE%\.m2\repository" "-Dspring.profiles.active=test" "-Dtest=RagControllerContractTest,UploadControllerContractTest,QuestionControllerContractTest" test
mvn -f backend-java/pom.xml -q "-Dmaven.repo.local=%USERPROFILE%\.m2\repository" "-Dspring.profiles.active=test" "-Dtest=DomainModelTest" test
mvn -f backend-java/pom.xml -q "-Dmaven.repo.local=%USERPROFILE%\.m2\repository" "-Dspring.profiles.active=test" "-Dtest=IndexNamingPolicyTest,OpenSearchIntegrationTest" test
mvn -f backend-java/pom.xml -q "-Dmaven.repo.local=%USERPROFILE%\.m2\repository" "-Dspring.profiles.active=test" "-Dtest=PromptTemplateFactoryTest,BedrockAdapterWiringTest" test
mvn -f backend-java/pom.xml -q "-Dmaven.repo.local=%USERPROFILE%\.m2\repository" "-Dspring.profiles.active=test" "-Dtest=BdaResultMapperTest" test
mvn -f backend-java/pom.xml -q "-Dmaven.repo.local=%USERPROFILE%\.m2\repository" "-Dspring.profiles.active=test" "-Dtest=RagQueryApplicationServiceTest,DocumentIngestionApplicationServiceTest" test
mvn -f backend-java/pom.xml -q "-Dmaven.repo.local=%USERPROFILE%\.m2\repository" "-Dspring.profiles.active=test" "-Dtest=ApiLayerIntegrationTest" test
mvn -f backend-java/pom.xml -q "-Dmaven.repo.local=%USERPROFILE%\.m2\repository" "-Dspring.profiles.active=test" "-Dtest=RequestCorrelationFilterTest" test
```

## Runtime Configuration

Relevant environment variables:

```bash
DB_HOST
DB_PORT
DB_NAME
DB_USERNAME
DB_PASSWORD
AWS_DEFAULT_REGION
BEDROCK_REGION
AWS_SESSION_TOKEN
OPENSEARCH_ENDPOINT
OPENSEARCH_USERNAME
OPENSEARCH_PASSWORD
S3_DOCUMENT_BUCKET
S3_DOCUMENT_PREFIX
S3_BDA_OUTPUT_PREFIX
BDA_PROJECT_ARN
BDA_PROFILE_ARN
BDA_STAGE
BDA_MAX_POLL_ATTEMPTS
BDA_POLL_INTERVAL
```

## Upload Storage

`POST /upload_files` still accepts the frontend's `file` and `directory_path` fields unchanged.

- The backend now uploads the incoming file directly to S3.
- `directory_path` is interpreted as an S3 key prefix, not a local filesystem path.
- Uploaded source files are stored under:
  - `s3://<S3_DOCUMENT_BUCKET>/<S3_DOCUMENT_PREFIX>/<directory_path>/<filename>`
- BDA output JSON is written to the same bucket under:
  - `s3://<S3_DOCUMENT_BUCKET>/<S3_BDA_OUTPUT_PREFIX>/<indexName>.json`

The runtime IAM or credentials must allow:

- `s3:PutObject` for uploaded documents
- `s3:GetObject` for BDA output reads
- Bedrock Runtime / Bedrock Agent Runtime / BDA invoke and status access

## Current Status

- API, application, domain, and infrastructure layers are wired in the test profile.
- Upload ingestion now expects S3-backed document storage for the main runtime path.
- Full local runtime still depends on real PostgreSQL/OpenSearch/Bedrock/BDA connectivity.
