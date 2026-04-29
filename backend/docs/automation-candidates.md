# Automation Candidates

This project can use Codex app automations after the team chooses schedules.

## Weekly Backend Harness Check

Purpose:

- Check that harness files still exist.
- Run the backend verification command.
- Summarize failures and next concrete action.

Suggested schedule:

- Weekly, Monday morning.

Suggested prompt:

```text
Inspect the backend harness health without editing files. Run the harness integrity check and the backend
verification command when available. Summarize whether the harness passes, list any failures with likely
causes, and suggest the next concrete action.
```

Commands the automation should run from the repository root:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\backend\scripts\check-harness.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\backend\scripts\verify.ps1
```

## Sprint Start Context Refresh

Purpose:

- Read root `AGENTS.md`, `backend/AGENTS.md`, `codex-current-state.md`, and recent ADRs.
- Suggest the next backend task based on current state.
- Report stale docs or missing verification.

Suggested schedule:

- At the start of each sprint.

## Pre-merge Harness Review

Purpose:

- Run `session-close.ps1`.
- Review changed files against `agent-review-checklist.md`.
- Summarize merge readiness and risk.

Suggested trigger:

- Manual request before merge or PR creation.
