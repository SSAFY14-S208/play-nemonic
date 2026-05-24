# Commit Message Guide

## When to Use This File

Read this file when asked to suggest commit messages for staged changes, current working tree changes, or any git-related review task.

---

## Commit Format

```
[FE] {type} : {subject in Korean}
- bullet describing change 1
- bullet describing change 2
```

- `[FE]` prefix is mandatory — all frontend commits use this tag.
- `{type}` must be one of the types in the table below.
- Subject is written in Korean — one concise phrase describing what changed and why.
- Each bullet describes one logical unit of change.

### Type Definitions

| Type | When to use |
|------|-------------|
| `feat` | New feature added |
| `fix` | Bug fix |
| `refactor` | Code restructure with no behavior change |
| `design` | UI or style change with no logic change |
| `chore` | Build config, dependencies, environment files, scripts |
| `docs` | Documentation files (README, docs/*.md, CLAUDE.md, etc.) |
| `perf` | Performance improvement |
| `test` | Test added or updated |

---

## Analysis Process

When asked to suggest a commit message, follow these steps:

1. **Inspect changes** — `git status` + `git diff` (or `git diff --staged`) to see changed files and content
2. **Group by concern** — group changed files by "why did this change"
3. **Decide split** — apply the splitting rules below
4. **Write suggestion** — output single or multi-commit format based on the result

---

## Commit Splitting Rules

Split into multiple commits when any of the following is true:

| Condition | Example |
|-----------|---------|
| `feat` and `fix` coexist | New feature + unrelated bug fix |
| `feat` and `refactor` coexist | New component + restructuring existing code |
| `chore` mixed with code changes | Package added + feature implemented |
| `docs` mixed with code changes | README updated + component changed |
| `feat` changes span unrelated domains | Fortune feature + gallery feature changed together |
| Same file changed for different purposes | Bug fix that also includes a separate refactor |

If the changes share one purpose (one type + one domain), keep them as a single commit.

---

## Output Format

### Single commit

```
[FE] feat : 포춘 모달 닫기 버튼 추가
- FortuneModal에 닫기 버튼 컴포넌트 추가
- fortuneStore에 closeModal 액션 추가
- 닫기 시 active 상태 false로 초기화
```

### Multiple commits

When two or more commits are needed, output with numbered headers:

```
# 첫 번째 커밋
[FE] {type} : {제목}
- 내용

# 두 번째 커밋
[FE] {type} : {제목}
- 내용
```

**Example:**

```
# 첫 번째 커밋
[FE] refactor : 릴레이 드로잉 훅 폴더화
- useRelayDrawing.ts를 hooks/ 폴더로 이동
- hooks/index.ts 배럴 추가

# 두 번째 커밋
[FE] feat : 릴레이 드로잉 히스토리 되돌리기 기능 추가
- useRelayDrawingHistory 훅 추가
- 되돌리기 버튼 컴포넌트 추가
- RelayDrawingPage에 되돌리기 버튼 연결
```

---

## Naming Rules for Subject

- End with an action noun: "추가", "개선", "수정", "제거", "적용", "폴더화"
- Describe purpose or feature — never use raw file names, function names, or component names
- No unnecessary particles or honorifics

| Bad | Good |
|-----|------|
| `FortuneModal.tsx 수정` | `포춘 모달 닫기 버튼 추가` |
| `버그 고침` | `포춘 모달 중복 열기 버그 수정` |
| `코드 정리했습니다` | `릴레이 드로잉 훅 폴더화` |
| `update` | `공유 페이지 OG 메타데이터 개선` |
