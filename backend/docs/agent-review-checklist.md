# Agent Review Checklist

Use this checklist before handing off substantial backend changes.

## Scope

- The changed files match the requested scope.
- There are no unrelated refactors.
- There are no drive-by formatting changes outside touched work.
- New packages follow `backend/docs/backend-architecture.md`.

## Behavior

- Acceptance criteria are implemented.
- Product behavior matches the relevant `backend/docs/product-spec/` document.
- Success and failure paths are covered.
- Controller code delegates business logic to application services.
- Entity objects are not returned directly from controllers.
- Request DTO validation annotations are present where needed.

## Tests

- Unit tests cover pure logic when applicable.
- HTTP or integration tests cover API behavior when applicable.
- Existing tests still pass.
- Test fixtures are in `src/test/java/com/nemonicworld/support` when shared.

## Harness

- `backend/scripts/format.ps1` passes from the repository root.
- `backend/scripts/verify.ps1` passes from the repository root.
- API `.http` examples are updated when API contracts change.
- `codex-current-state.md` or ADRs are updated when durable project knowledge changed.

## Risk

- Secrets are not committed.
- Local-only caches are ignored by Git and Docker.
- Migrations, config, and environment values are documented.
- Any remaining risk is called out in the final response.
