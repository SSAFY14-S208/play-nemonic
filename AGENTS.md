# Repository Agent Guide

This repository currently contains the backend service in `backend/`.
Use this guide as the default operating manual for Codex and other coding agents.

## Stack

- Java 21
- Spring Boot 3.5.x
- Gradle wrapper
- PostgreSQL for local/runtime persistence
- Redis for runtime infrastructure
- H2 for the default test profile
- Flyway, JPA, Spring Security, Validation, Actuator
- Spotless and Checkstyle for formatting and style gates

## Important Paths

- Backend module: `backend/`
- Main Java sources: `backend/src/main/java`
- Test Java sources: `backend/src/test/java`
- Test profile: `backend/src/test/resources/application-test.yaml`
- Local environment sample: `backend/.env.example`
- Local infrastructure compose file: `docker-compose.local.yml`
- Team contribution guide: `CONTRIBUTING.md`
- GitLab backend MR template: `.gitlab/merge_request_templates/backend.md`
- API request harness: `backend/docs/api/`
- Verification scripts: `backend/scripts/`
- Backend architecture convention: `backend/docs/backend-architecture.md`
- Codex app usage guide: `backend/docs/codex-app-operations.md`
- Codex prompt templates: `backend/docs/codex-prompt-templates.md`
- Codex memory strategy: `backend/docs/codex-memory.md`
- Current handoff state: `backend/docs/codex-current-state.md`
- Agent workflow: `backend/docs/agent-workflow.md`
- Review checklist: `backend/docs/agent-review-checklist.md`
- Architecture decisions: `backend/docs/decisions/`
- Product specification: `backend/docs/product-spec/`
- Skill candidate: `backend/docs/skills/nemonic-backend-development/SKILL.md`
- Automation candidates: `backend/docs/automation-candidates.md`

## Canonical Commands

Run commands from the repository root unless a script says otherwise.

```powershell
Push-Location .\backend
.\gradlew.bat test
.\gradlew.bat spotlessCheck
.\gradlew.bat check
.\gradlew.bat build
Pop-Location
```

Preferred verification wrapper:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\backend\scripts\verify.ps1
```

The verification wrapper sets `GRADLE_USER_HOME` to
`backend/.gradle-user-home` when the variable is not already defined. This keeps
Gradle wrapper downloads and caches inside the workspace for sandboxed agents.
The wrapper also uses `backend/.m2-repository` for Maven-resolved formatter
dependencies used by Spotless and `backend/.tool-home` as an isolated Java
tool home for sandboxed executions.

Fast test-only verification:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\backend\scripts\verify.ps1 -Fast
```

Apply formatting:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\backend\scripts\format.ps1
```

Full build verification:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\backend\scripts\verify.ps1 -Build
```

Harness integrity check:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\backend\scripts\check-harness.ps1
```

Session close routine:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\backend\scripts\session-close.ps1
```

## Local Infrastructure

Create `backend/.env` from `backend/.env.example`, then start dependencies from the
repository root through the helper script:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\backend\scripts\local-up.ps1
```

Stop local dependencies:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\backend\scripts\local-down.ps1
```

The helper scripts intentionally do not remove Docker volumes.

## Testing Rules

- Use the `test` Spring profile for automated tests.
- Keep tests deterministic and independent.
- Prefer unit tests for pure logic.
- Prefer web slice tests for controller request/response behavior.
- Use integration tests for wiring, configuration, and cross-layer behavior.
- Add or update tests when behavior changes.
- Keep test fixtures in `backend/src/test/java/com/nemonicworld/support`.

## Memory Rules

- Treat this file and `backend/docs/codex-current-state.md` as the baseline for new sessions.
- Update `backend/docs/codex-current-state.md` after meaningful multi-step work or durable context changes.
- Add an ADR under `backend/docs/decisions/` for decisions future contributors should not rediscover.
- Use `backend/docs/session-handoff-template.md` when pausing or transferring a long task.
- Keep durable memory concise; do not record every small edit.

## Development Rules

- Keep changes scoped to the requested feature or fix.
- Follow `CONTRIBUTING.md` for branch, commit, Jira, and MR conventions.
- Follow `backend/docs/backend-architecture.md` for new packages and feature layout.
- Follow existing package boundaries before creating new abstractions.
- Do not hard-code secrets or environment-specific values.
- Use `ApiResponse` for API response bodies where the existing API style does.
- Prefer constructor injection for Spring components.
- Keep controller methods thin; move business logic into services as the domain grows.
- Run the verification wrapper before handing work back.

## Agent Behavior Rules

These rules adapt the Karpathy-style coding agent guidance for this repository.

- State assumptions before changing code when requirements are ambiguous.
- Choose the smallest implementation that satisfies the acceptance criteria.
- Do not add speculative abstractions, options, or future-proofing.
- Touch only files that are directly connected to the requested change.
- Preserve existing style and ownership boundaries.
- Mention unrelated cleanup opportunities instead of applying drive-by refactors.
- Convert requests into verifiable success criteria whenever possible.
- Keep working until the relevant verification command passes or the blocker is clear.
- For substantial work, follow `backend/docs/agent-workflow.md`.
- For product behavior, read the relevant file under `backend/docs/product-spec/` before implementing.
- Before handoff, use `backend/docs/agent-review-checklist.md`.

## Agent Task Template

Use `backend/docs/codex-prompt-templates.md` for copy-and-paste prompts.
For backend implementation work, this is the minimum structure:

```md
## Goal
Short description of the behavior to implement.

## Scope
- Files, packages, or API areas that may be changed.

## Constraints
- Existing patterns to keep.
- Things that must not be changed.

## Acceptance Criteria
- Observable behavior.
- Error cases.
- Required tests.
- Verification command that must pass.
```

## Environment Notes

If PowerShell profile loading causes command noise, run PowerShell commands with
`-NoProfile`.

If Git reports dubious repository ownership in this environment, configure the
repository as a safe directory for the active user:

```powershell
git config --global --add safe.directory C:/SSAFY/mango-project/be
```
