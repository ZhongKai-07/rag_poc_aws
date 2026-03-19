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
- Task 10: next

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

8. Keep temporary API-facing bridge methods on the Task 9 application service interfaces
   - Reason: this lets Task 9 land real orchestration without breaking the already-completed Task 4 controller contract tests.
   - Removal plan: Task 10 will rewire controllers to application-native request/result models and delete the bridge shape.

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

Future local service run command after wiring milestones are complete:

- `mvn -f backend-java/pom.xml spring-boot:run`
- `curl http://localhost:8001/health`

## Known Gaps

- Controllers are not yet wired to real service implementations.
- Full local backend startup for frontend use is not ready yet.
- Regression suite and cutover checklist are not finished yet.

## Known Warnings

- Spring AI Bedrock auto-configuration emits an AWS region warning in some tests.
- This warning has not blocked current milestone tests, but runtime configuration still needs proper AWS environment setup for real execution.
- In this shell environment, Maven may need `-Dmaven.repo.local=$env:USERPROFILE\.m2\repository` when new plugins or dependencies must be resolved.

## Next Recommended Step

Continue with Task 9:

- add `ApiLayerIntegrationTest`
- rewire controllers to application-native request/result models
- remove the temporary API-DTO bridge from application services
- add frontend-safe exception mapping for application failures
- run the milestone verification
- commit only Task 10 files

## Maintenance Rule

These four root documents are part of the active development workflow and must be updated continuously as implementation progresses:

- `Prompt.md`
- `Plan.md`
- `Implement.md`
- `Documentation.md`
