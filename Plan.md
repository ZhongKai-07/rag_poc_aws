# Spring Boot RAG Migration Plan

## Source Of Truth

Primary implementation plan:

- `docs/superpowers/plans/2026-03-19-springboot-rag-migration-layered-plan.md`

This file is the execution overlay for current status, milestone boundaries, verification, and decisions already made.

## Target Architecture

- `api`
  - HTTP endpoints, request validation, DTO mapping, exception handling
- `application`
  - use-case orchestration only
- `domain`
  - business models, value objects, enums, ports, invariants
- `infrastructure`
  - OpenSearch, Bedrock, BDA, persistence, storage, support wiring

## Milestones

### Completed Milestones

1. Baseline freeze
   - Status: completed
   - Acceptance:
     - contract fixtures exist
     - regression question baseline exists
     - migration baseline checklist exists
   - Verification:
     - `Get-ChildItem backend-java\src\test\resources\fixtures -Recurse`

2. Spring Boot bootstrap
   - Status: completed
   - Acceptance:
     - `backend-java/` Maven project exists
     - Spring context bootstrap test passes
     - server port configured to `8001`
   - Verification:
     - `mvn -f backend-java/pom.xml -q "-Dspring.profiles.active=test" "-Dtest=RagApplicationTest" test`

3. Infrastructure config and persistence
   - Status: completed
   - Acceptance:
     - property binding classes exist
     - Flyway migrations create required tables
     - repositories persist and query records
   - Verification:
     - `mvn -f backend-java/pom.xml -q "-Dspring.profiles.active=test" "-Dtest=PostgresIntegrationTest" test`

4. API contract layer
   - Status: completed
   - Acceptance:
     - controllers and DTOs preserve endpoint and field compatibility
     - MockMvc contract tests pass
   - Verification:
     - `mvn -f backend-java/pom.xml -q "-Dspring.profiles.active=test" "-Dtest=RagControllerContractTest,UploadControllerContractTest,QuestionControllerContractTest" test`

5. Domain contracts
   - Status: completed
   - Acceptance:
     - index naming and retrieval domain behavior tested
     - domain has no Spring/JPA/AWS/OpenSearch client dependency
   - Verification:
     - `mvn -f backend-java/pom.xml -q "-Dspring.profiles.active=test" "-Dtest=DomainModelTest" test`

6. OpenSearch adapters
   - Status: completed
   - Acceptance:
     - compatible index naming preserved
     - explicit field mapping preserved
     - vector/text/mix retrieval adapter behavior covered
   - Verification:
     - `mvn -f backend-java/pom.xml -q "-Dspring.profiles.active=test" "-Dtest=IndexNamingPolicyTest,OpenSearchIntegrationTest" test`

7. Bedrock adapters
   - Status: completed
   - Acceptance:
     - prompt factory shape preserved
     - adapter wiring and retry helper exist
   - Verification:
     - `mvn -f backend-java/pom.xml -q "-Dspring.profiles.active=test" "-Dtest=PromptTemplateFactoryTest,BedrockAdapterWiringTest" test`

### Remaining Milestones

8. BDA parsing and normalization
   - Status: completed
   - Acceptance:
     - BDA client polling flow exists
     - BDA result mapping preserves page, section path, paragraph, short sentence, assets, provenance
   - Verification:
     - `mvn -f backend-java/pom.xml -q "-Dspring.profiles.active=test" "-Dtest=BdaResultMapperTest" test`

9. Application orchestration
   - Status: completed
   - Acceptance:
     - ingestion/query/history/registry flows implemented in `application/`
     - orchestration tests pass
   - Verification:
     - `mvn -f backend-java/pom.xml -q "-Dspring.profiles.active=test" "-Dtest=RagQueryApplicationServiceTest,DocumentIngestionApplicationServiceTest" test`

10. Real API wiring
   - Status: completed
   - Acceptance:
     - controllers use real application services
     - placeholder wiring removed
     - API integration test passes
   - Verification:
     - `mvn -f backend-java/pom.xml -q "-Dspring.profiles.active=test" "-Dtest=ApiLayerIntegrationTest" test`

11. Storage and observability
   - Status: completed
   - Acceptance:
      - local file storage adapter exists
      - request correlation support exists
      - README updated
   - Verification:
     - `mvn -f backend-java/pom.xml -q "-Dspring.profiles.active=test" "-Dtest=RequestCorrelationFilterTest" test`

12. Regression and cutover readiness
   - Status: next
   - Acceptance:
      - regression tests pass
      - full suite passes
     - local smoke test passes
     - cutover checklist exists
   - Verification:
     - `mvn -f backend-java/pom.xml -q "-Dspring.profiles.active=test" "-Dtest=RagRegressionTest,IngestionRegressionTest" test`
     - `mvn -f backend-java/pom.xml test`
     - `mvn -f backend-java/pom.xml spring-boot:run`
     - `curl http://localhost:8001/health`

## Stop And Fix Rules

Stop and fix before moving on when any of the following happens:

- The current milestone test does not fail for the expected reason before implementation.
- A milestone verification command fails after implementation.
- A new change breaks a previously completed milestone verification.
- A change would alter frontend field names, endpoint names, or response shapes.
- A change would move AWS/OpenSearch/JPA concerns into `domain/`.
- A change would require deleting or breaking the Python backend.
- The milestone status in these root documents is no longer accurate.

## Documentation Update Rule

After every milestone, update:

- `Prompt.md` if scope, constraints, or done criteria changed
- `Plan.md` if milestone status or execution decisions changed
- `Implement.md` if working rules changed
- `Documentation.md` with current progress, decisions, and next step

## Decision Notes

- Current worktree is already isolated:
  - `C:\Users\zhong kai\.codex\worktrees\ff34\huatai_rag_github_share`
- Current HEAD is detached and work continues there intentionally.
- Java target is temporarily `17` because the local machine currently exposes only JDK 17.
- Test profile uses H2 in PostgreSQL compatibility mode, so local PostgreSQL is not required for current test milestones.
- When Maven needs to resolve newly added plugins or dependencies in this environment, use `-Dmaven.repo.local=$env:USERPROFILE\.m2\repository` because the default local repository setting points at an unavailable `D:\download\Maven\localRepository`.
- Task 10 removed the temporary API-DTO bridge from `application/`.
  - Controllers now perform DTO-to-application and application-to-DTO mapping explicitly.
  - Task 11 completed the local storage adapter, request correlation filter, and the main runtime bean graph needed for the real service stack in the test profile.
- `backend-java/target/` must stay ignored and must not be committed.
