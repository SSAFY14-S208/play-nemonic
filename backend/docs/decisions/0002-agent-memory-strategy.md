# 0002 Agent Memory Strategy

Date: 2026-04-28

## Status

Accepted

## Context

Conversation memory is useful but not durable. Long sessions can be compacted, and new sessions may not have
the full prior conversation. The project needs a durable memory model that does not depend on a single thread.

## Decision

Use layered repo memory:

- stable instructions in `AGENTS.md`
- current handoff state in `backend/docs/codex-current-state.md`
- durable decisions in `backend/docs/decisions/`
- reusable prompts in `backend/docs/codex-prompt-templates.md`
- executable memory in `backend/scripts/`

The current state file is updated only after meaningful multi-step work or durable context changes.
ADRs are added for decisions that future contributors should not rediscover.

## Consequences

- Positive: New sessions can resume with minimal explanation.
- Positive: Decisions are searchable and reviewable.
- Positive: Context compaction is less risky.
- Negative: Over-updating current state can create noisy docs churn.
- Follow-up: Periodically review current state and ADRs for staleness.
