# Architecture Decision Records

This folder stores durable engineering decisions.

Use an ADR when a decision affects future work, such as:

- package structure
- test strategy
- local infrastructure
- authentication/security design
- database migration strategy
- agent harness and memory strategy

Create a new ADR from `TEMPLATE.md` or use:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\new-decision.ps1 -Title "Decision title"
```
