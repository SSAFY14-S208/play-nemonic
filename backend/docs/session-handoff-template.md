# Session Handoff Template

Use this when pausing a long task, switching sessions, or asking another agent to continue.

```md
## Task
<What was being done?>

## Current State
- <What is complete?>
- <What is partially complete?>
- <What remains?>

## Files Changed
- `<path>`: <why it changed>
- `<path>`: <why it changed>

## Decisions Made
- <Decision and reason>

## Verification
- Command:
- Result:
- Notes:

## Known Risks
- <Risk or unresolved question>

## Next Step
<The single best next action>
```

## Minimal Handoff Prompt

```text
AGENTS.md and backend/docs/codex-current-state.md are the memory baseline.
Continue from this handoff:

<paste handoff>
```
