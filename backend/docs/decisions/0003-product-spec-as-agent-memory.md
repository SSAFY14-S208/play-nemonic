# 0003 Product Spec As Agent Memory

Date: 2026-04-28

## Status

Accepted

## Context

The product plan is long and covers community, multiplayer games, AI moderation, inventory,
backoffice, observability, and operating policy. Relying on chat history would make future Codex
sessions forget important product constraints.

## Decision

Store the product plan as repo-local AI-readable Markdown under `backend/docs/product-spec/`.
Split the document by product domain so agents can load only the relevant sections.

The product spec becomes durable project memory alongside `AGENTS.md`, architecture docs, ADRs,
and executable verification scripts.

## Consequences

- Positive: New sessions can recover product intent and policy without asking the user again.
- Positive: Feature implementation can reference the exact product domain document.
- Positive: Large product context does not need to be pasted into every prompt.
- Negative: Product docs must be updated when planning changes.
- Follow-up: Add API/DB ADRs when implementation decisions refine product policy.
