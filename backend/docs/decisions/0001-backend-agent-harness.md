# 0001 Backend Agent Harness

Date: 2026-04-28

## Status

Accepted

## Context

Before feature development, the backend needs a stable way for Codex and humans to share project rules,
run the same checks, and recover context across sessions.

## Decision

Use a repo-local harness made of:

- `AGENTS.md` for primary agent instructions
- `backend/docs/*` for architecture, memory, workflow, and prompt templates
- `backend/scripts/format.ps1` and `backend/scripts/verify.ps1` for executable verification
- workspace-local Gradle, Maven, and Java tool caches
- `.http` files for API request examples
- test support annotations for common Spring test modes

## Consequences

- Positive: New Codex sessions can recover project context from files.
- Positive: Verification has a single canonical entrypoint.
- Positive: Agent behavior is less dependent on chat history.
- Negative: Docs must be maintained when project rules change.
- Follow-up: Add weekly automation if the team wants recurring harness health checks.
