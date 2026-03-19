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
OPENSEARCH_ENDPOINT
OPENSEARCH_USERNAME
OPENSEARCH_PASSWORD
DOCUMENT_ROOT
BDA_PROJECT_ARN
BDA_PROFILE_ARN
BDA_STAGE
BDA_MAX_POLL_ATTEMPTS
BDA_POLL_INTERVAL
```

## Current Status

- API, application, domain, and infrastructure layers are wired in the test profile.
- Local file storage and request correlation support are implemented.
- Full local runtime still depends on real PostgreSQL/OpenSearch/Bedrock/BDA connectivity.
