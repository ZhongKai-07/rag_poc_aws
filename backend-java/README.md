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

- Java 21
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
