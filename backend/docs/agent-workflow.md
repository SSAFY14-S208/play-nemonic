# Agent Workflow

This project uses a research-first, verification-driven workflow for non-trivial work.

## 1. Explore

- Read root `AGENTS.md`.
- Read `backend/AGENTS.md`.
- Read `backend/docs/codex-current-state.md`.
- Read feature code and tests before editing.
- For new packages, read `backend/docs/backend-architecture.md`.
- For product behavior, read the relevant file under `backend/docs/product-spec/`.

Use subagents for exploration only when the user explicitly asks for subagents or parallel agent work.

## 2. Plan

- State assumptions if requirements are ambiguous.
- Convert the request into acceptance criteria.
- Identify files and package boundaries.
- Pick the fastest useful verification command.

Skip a formal plan for obvious one-line changes.

## 3. Implement

- Make the smallest complete change.
- Keep package boundaries intact.
- Avoid speculative abstractions.
- Add or update tests alongside behavior changes.

## 4. Verify

From the repository root:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\backend\scripts\format.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\backend\scripts\verify.ps1
```

Use `-Fast` for quick test-only checks while iterating.

## 5. Commit Requests

When the user asks the agent to create or amend a commit:

1. Run formatting first.
2. Run the fastest relevant verification command.
3. Check git status.
4. Stage only the intended files.
5. Commit or amend with the requested commit message.

From the repository root:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\backend\scripts\format.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\backend\scripts\verify.ps1
```

Use `verify.ps1 -Fast` only when the user explicitly wants a fast commit loop or
when the change is small and the risk is low. If DB migrations changed, also run
`backend/scripts/verify-migration.ps1` when Docker is available.

If formatting changes files, include those formatting changes in the same commit
unless the user asks for a different split.

## 6. Handoff

- Summarize changed files.
- Report verification commands and results.
- Mention unresolved risks.
- Update `backend/docs/codex-current-state.md` for durable state changes.
- Add an ADR for significant decisions.
