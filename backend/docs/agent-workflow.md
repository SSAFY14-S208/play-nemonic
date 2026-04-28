# Agent Workflow

This project uses a research-first, verification-driven workflow for non-trivial work.

## 1. Explore

- Read `AGENTS.md`.
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

## 5. Handoff

- Summarize changed files.
- Report verification commands and results.
- Mention unresolved risks.
- Update `backend/docs/codex-current-state.md` for durable state changes.
- Add an ADR for significant decisions.
