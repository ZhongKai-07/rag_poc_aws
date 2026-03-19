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

## Repository Hygiene

- Do not commit `backend-java/target/`.
- Keep `.gitignore` protecting Maven build output.
- Preserve the existing Python `api/` backend.

## Runtime Notes

- Current Java target is temporarily `17`.
- Current local test profile does not require a manually configured PostgreSQL instance.
- The Java backend is not considered runnable for frontend use until real application-service wiring is complete.
