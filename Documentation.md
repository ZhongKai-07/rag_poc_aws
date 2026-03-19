# Migration Documentation

## Current Status

Current milestone status:

- Task 1: completed
- Task 2: completed
- Task 3: completed
- Task 4: completed
- Task 5: completed
- Task 6: completed
- Task 7: completed
- Task 8: completed
- Task 9: completed
- Task 10: completed
- Task 11: completed
- Task 12: completed except exact-port smoke verification

The Java backend is partially built but not yet ready for full local service startup against the frontend.

## Completed Commits

- `dd907a21` `test: add migration baseline fixtures`
- `b1f9021c` `build: bootstrap layered spring boot rag backend`
- `34ea224d` `feat: add infrastructure configuration and persistence adapters`
- `c9e9f40f` `feat: add frontend-compatible api layer`
- `592f9c31` `feat: add domain models and business ports`
- `2210e272` `feat: add opensearch infrastructure adapters`
- `0908fd61` `feat: add bedrock infrastructure adapters`
- `baba1107` `docs: add migration control documents`

## Decisions And Reasons

1. Keep Python backend intact
   - Reason: it is the baseline, control group, and parity reference until the migration is complete.

2. Freeze frontend contract early
   - Reason: prevents later Java implementation work from drifting away from the existing React integration.

3. Introduce placeholder `application` interfaces in Task 4
   - Reason: allows API contract tests to pass before real orchestration is implemented in Task 9 and wired in Task 10.

4. Use H2 PostgreSQL compatibility mode in test profile
   - Reason: removes local PostgreSQL as a blocker for current milestone testing.

5. Temporarily target Java 17
   - Reason: local machine currently has JDK 17 available and the user explicitly approved the temporary downgrade to keep work moving.

6. Ignore `backend-java/target/`
   - Reason: Maven build output accidentally entered the index once and should never be committed.

7. Use the user `.m2` repository override when resolving new Maven artifacts locally
   - Reason: the default Maven local repository setting in this environment points to an unavailable `D:\download\Maven\localRepository`, while `C:\Users\zhong kai\.m2\repository` works.

8. Move DTO mapping back into controllers in Task 10
   - Reason: this restores the intended boundary where `application/` exposes native request/result models and `api/` owns HTTP contract mapping.

9. Complete the main runtime bean graph in Task 11
   - Reason: once controllers and application services were real, the test-profile startup path still needed concrete beans for storage, persistence adapters, parser wiring, and OpenSearch chunk writing.

10. Lock Python-compatible Task 12 regressions in tests
   - Reason: rerank-empty fallback behavior and duplicate-upload skip behavior were still implicit in the Python baseline and needed executable guards before cutover.

11. Exclude Spring AI Bedrock auto-config from the `test` profile only
   - Reason: the full Maven suite was instantiating extra Bedrock event loops unrelated to the layered adapters, which blocked local test execution on `2026-03-19`.

## Run And Demo Commands

Current milestone verification commands:

- Bootstrap:
  - `mvn -f backend-java/pom.xml -q "-Dspring.profiles.active=test" "-Dtest=RagApplicationTest" test`
- Persistence:
  - `mvn -f backend-java/pom.xml -q "-Dspring.profiles.active=test" "-Dtest=PostgresIntegrationTest" test`
- API contract:
  - `mvn -f backend-java/pom.xml -q "-Dspring.profiles.active=test" "-Dtest=RagControllerContractTest,UploadControllerContractTest,QuestionControllerContractTest" test`
- Domain:
  - `mvn -f backend-java/pom.xml -q "-Dspring.profiles.active=test" "-Dtest=DomainModelTest" test`
- OpenSearch:
  - `mvn -f backend-java/pom.xml -q "-Dspring.profiles.active=test" "-Dtest=IndexNamingPolicyTest,OpenSearchIntegrationTest" test`
- Bedrock:
  - `mvn -f backend-java/pom.xml -q "-Dspring.profiles.active=test" "-Dtest=PromptTemplateFactoryTest,BedrockAdapterWiringTest" test`
- BDA parsing:
  - `mvn -f backend-java/pom.xml -q "-Dmaven.repo.local=$env:USERPROFILE\.m2\repository" "-Dspring.profiles.active=test" "-Dtest=BdaResultMapperTest" test`
- Application orchestration:
  - `mvn -f backend-java/pom.xml -q "-Dmaven.repo.local=$env:USERPROFILE\.m2\repository" "-Dspring.profiles.active=test" "-Dtest=RagQueryApplicationServiceTest,DocumentIngestionApplicationServiceTest" test`
- Application-safe regression for completed milestones touched by Task 9:
  - `mvn -f backend-java/pom.xml -q "-Dmaven.repo.local=$env:USERPROFILE\.m2\repository" "-Dspring.profiles.active=test" "-Dtest=RagControllerContractTest,UploadControllerContractTest,QuestionControllerContractTest,BdaResultMapperTest" test`
- API integration:
  - `mvn -f backend-java/pom.xml -q "-Dmaven.repo.local=$env:USERPROFILE\.m2\repository" "-Dspring.profiles.active=test" "-Dtest=ApiLayerIntegrationTest,RagControllerContractTest,UploadControllerContractTest,QuestionControllerContractTest,RagQueryApplicationServiceTest,DocumentIngestionApplicationServiceTest,BdaResultMapperTest" test`
- Storage and observability:
  - `mvn -f backend-java/pom.xml -q "-Dmaven.repo.local=$env:USERPROFILE\.m2\repository" "-Dspring.profiles.active=test" "-Dtest=RequestCorrelationFilterTest,ApiLayerIntegrationTest,RagControllerContractTest,UploadControllerContractTest,QuestionControllerContractTest,RagQueryApplicationServiceTest,DocumentIngestionApplicationServiceTest,BdaResultMapperTest" test`
- Regression and cutover readiness:
  - `mvn -f backend-java/pom.xml -q "-Dmaven.repo.local=$env:USERPROFILE\.m2\repository" "-Dspring.profiles.active=test" "-Dtest=RagRegressionTest,IngestionRegressionTest" test`
  - `mvn -f backend-java/pom.xml "-Dmaven.repo.local=$env:USERPROFILE\.m2\repository" test`

Future local service run command after wiring milestones are complete:

- `mvn -f backend-java/pom.xml spring-boot:run`
- `curl http://localhost:8001/health`

## Known Gaps

- Exact Java smoke validation on `http://localhost:8001/health` is still pending in this workspace because `python.exe` PID `42828` was already listening on port `8001` on `2026-03-19`.
- Representative external baseline PDFs are still intentionally not committed under `backend-java/src/test/resources/fixtures/regression/documents/`, so binary-level ingestion parity beyond the filename/question fixtures still depends on operator-supplied files during cutover rehearsal.

## Known Warnings

- Spring AI Bedrock auto-configuration emits an AWS region warning in some tests.
- This warning has not blocked current milestone tests, but runtime configuration still needs proper AWS environment setup for real execution.
- In this shell environment, Maven may need `-Dmaven.repo.local=$env:USERPROFILE\.m2\repository` when new plugins or dependencies must be resolved.
- A `curl http://localhost:8001/health` response was observed on `2026-03-19`, but it came from the already-running Python baseline listener, not from a freshly started Java smoke process.

## Next Recommended Step

Finish Task 12 closeout:

- stop or relocate the existing Python listener on port `8001`
- rerun exact-port Java smoke on `8001`
- rehearse frontend cutover using the new checklist

## Maintenance Rule

These four root documents are part of the active development workflow and must be updated continuously as implementation progresses:

- `Prompt.md`
- `Plan.md`
- `Implement.md`
- `Documentation.md`
