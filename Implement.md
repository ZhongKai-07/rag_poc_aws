# Implementation Instructions

## Working Rules

- `Plan.md` and the referenced migration plan are the only execution truth sources.
- Execute tasks in order.
- Do not skip forward.
- Do not silently redesign scope.
- If a plan adjustment is unavoidable, make the smallest possible adjustment and explain it first.

## Milestone Workflow

For every milestone:

1. Write or update the milestone test first.
2. Run the milestone verification command and confirm the expected failure.
3. Implement the smallest code change needed.
4. Re-run the milestone verification command until it passes.
5. Check `git status`.
6. Commit only the files relevant to that milestone.
7. Update `Documentation.md`.

## Verification Rules

- Every completed milestone must have an explicit verification command recorded in `Plan.md`.
- Never claim a task is complete without rerunning its verification command.
- If a verification fails, stop and fix before starting the next milestone.
- In this environment, if Maven needs to resolve newly added plugins or dependencies, add `-Dmaven.repo.local=$env:USERPROFILE\.m2\repository` to avoid the broken default local repository path.

## Diff Control

- Keep each milestone diff scoped to the files named by the plan.
- Do not refactor unrelated code while working on a milestone.
- Do not modify the frontend unless the user explicitly changes requirements.
- Do not touch the Python backend except for reading it as baseline reference.

## Documentation Rules

- Keep `Prompt.md`, `Plan.md`, `Implement.md`, and `Documentation.md` current as progress changes.
- Treat documentation updates as part of the implementation, not optional follow-up work.
- Record any temporary workaround or environment constraint immediately.
- If a later milestone replaces a placeholder from an earlier milestone, note that replacement in `Documentation.md`.
- Before ending a work session, make sure milestone status and next-step notes are up to date.
- If a temporary bridge is needed to preserve a completed milestone while the next milestone is still pending, document the bridge and remove it in the planned cutover task instead of hiding it.

## Repository Hygiene

- Do not commit `backend-java/target/`.
- Keep `.gitignore` protecting Maven build output.
- Preserve the existing Python `api/` backend.

## Runtime Notes

- Current Java target is temporarily `17`.
- Current local test profile does not require a manually configured PostgreSQL instance.
- The Java backend test profile now has a complete Spring bean graph for API/application/domain/infrastructure wiring.
- Full frontend-facing local runtime still depends on real environment connectivity for PostgreSQL/OpenSearch/Bedrock/BDA.
- New dependency resolution currently needs the user `.m2` repository override because the default Maven local repository path points to an unavailable `D:\download\Maven\localRepository`.
- Task 10 moved API DTO mapping fully back into controllers; the remaining startup gap is now about production bean wiring and local storage support, not placeholder application contracts.
