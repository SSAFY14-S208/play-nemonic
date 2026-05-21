# 0015. GMS Prompt Activation State

Date: 2026-05-18

## Status

Accepted

## Context

Backoffice can store multiple GMS prompt templates for a feature, and frontend
needs to show which prompt is currently used. The previous implementation used
the latest non-deleted prompt by `updated_at DESC, id DESC`, so editing a draft
could implicitly change production fortune generation.

Concurrent administrators can also activate different prompt versions at nearly
the same time. The backend needs a deterministic rule that keeps exactly one
current prompt per feature type.

## Decision

Store prompt activation state on `gms_prompt_template`:

- `is_active`
- `activated_at`
- `activated_by`

Keep soft deletion separate: `deleted_at IS NULL` means the prompt is visible
and manageable, while `is_active = true` means it is the prompt currently used
by runtime generation.

Enforce one active non-deleted prompt per feature with a PostgreSQL partial
unique index:

```sql
CREATE UNIQUE INDEX uq_gms_prompt_active_feature
ON gms_prompt_template (feature_type)
WHERE deleted_at IS NULL AND is_active = TRUE;
```

Serialize activation requests with `gms_prompt_feature_state`. The activation
service ensures the feature row exists, locks it with `SELECT ... FOR UPDATE`,
deactivates the previous prompt for the same feature, activates the selected
prompt, and updates the feature state pointer in one transaction.

Fortune generation now reads the active `fortune` prompt. If no DB prompt is
active, it keeps the existing built-in fallback prompt.

## Consequences

- Positive: Draft prompt edits no longer become live just because they are the
  latest updated row.
- Positive: Concurrent activation requests are processed sequentially per
  feature type; the last committed activation becomes the current prompt.
- Positive: The partial unique index protects the invariant even if service
  code regresses.
- Tradeoff: There is still no separate prompt group/version table. Each
  `gms_prompt_template` row is treated as one manageable version, so prompt
  names remain globally unique.
