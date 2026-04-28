---
name: nemonic-backend-development
description: Backend development workflow for the Nemonic Spring Boot repository. Use when working on this repository's backend APIs, package structure, tests, harness scripts, memory docs, ADRs, prompt templates, or Codex agent workflow. The skill directs Codex to use repo-local AGENTS.md, backend architecture conventions, current-state memory, verification scripts, and handoff routines.
---

# Nemonic Backend Development

## Start

1. Read repo-root `AGENTS.md`.
2. Read `backend/docs/codex-current-state.md`.
3. For package or API work, read `backend/docs/backend-architecture.md`.
4. For product behavior, read the relevant file under `backend/docs/product-spec/`.
5. For non-trivial work, follow `backend/docs/agent-workflow.md`.

## Implement

- Keep changes scoped to the request.
- Follow feature-layered packages for new backend code.
- Keep controllers thin.
- Use request/response DTOs instead of returning entities.
- Add or update tests for behavior changes.
- Add ADRs for durable decisions.
- Keep API, DB, and event behavior aligned with `backend/docs/product-spec/`.

## Verify

Run from the repository root:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\backend\scripts\format.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\backend\scripts\verify.ps1
```

For final handoff, prefer:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\backend\scripts\session-close.ps1
```

## Memory

- Update `backend/docs/codex-current-state.md` after meaningful multi-step work.
- Use `backend/docs/decisions/` for decisions future agents should not rediscover.
- Use `backend/docs/session-handoff-template.md` when pausing long work.

## Subagents

Use subagents only when the user explicitly asks for parallel agent work. Assign disjoint file scopes and let the main agent integrate and verify.
