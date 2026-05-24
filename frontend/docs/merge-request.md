# GitLab MR Guide

## When to Use This File

Read this file when asked to suggest a GitLab MR (Merge Request) title and description based on the current branch's work.

---

## Analysis Process

When asked to suggest an MR title and description, follow these steps:

1. **Fetch branch commits** — `git log {base-branch}..HEAD --oneline` to see all commits on this branch
2. **List changed files** — `git diff {base-branch}..HEAD --name-only` to see what files changed
3. **Read diff** — `git diff {base-branch}..HEAD` to understand the actual changes
4. **Detect UI changes** — if changed files include `*.tsx`, `*.css`, or design token files, treat as UI change
5. **Write title and description** — follow the formats below

> If the base branch is unknown, use `main` or `master`.

---

## MR Title Format

```
[FE] {one-line Korean summary of the work}
```

- `[FE]` prefix is mandatory.
- No `type` prefix — unlike commit messages, MR titles omit the type.
- Summary is in Korean: one sentence capturing the core of the branch's work.
- Describe purpose, not file names or function names.

**Examples:**
```
[FE] 배럴 import 규칙 정리 및 경로 정규화
[FE] 로고 파일 교체
[FE] 포춘 모달 닫기 기능 추가
[FE] 릴레이 드로잉 히스토리 되돌리기 구현
```

---

## MR Description Template

Fill in each section based on the branch's commits and diff, then output the result.
Keep all HTML comments (`<!-- -->`) exactly as-is — they are hidden when GitLab renders the MR and serve as author guidelines.

```markdown
## 📄 요약 (Summary)
<!-- 이번 PR에서 어떤 작업을 했는지 간단하게 설명해주세요. -->
{2–4 Korean sentences summarizing what was done and why, based on commits and diff}

<br>

## 🔗 관련 이슈 (Related Issue)
<!-- 본 PR과 관련된 Jira 이슈 번호를 모두 적어주세요. -->
- Closes S14P31S208-{issue number}

<br>

## ✨ 주요 변경 사항 (Key Changes)
<!-- 이번 PR에서 중점적으로 봐야 할 변경 사항을 목록으로 작성해주세요. -->
<!-- 리스트 형식으로 작성해주세요. -->
{3–7 Korean bullet points describing key changes by purpose, not by file name}

<br>

## 📸 스크린샷 (Screenshots)
<!-- UI 변경사항이 있다면 스크린샷을 첨부해주세요. (없다면 생략) -->
{if UI changes detected: output "스크린샷을 첨부해주세요." / if none: output "(없음)"}

<br>

## 🙏 리뷰어에게 (To the Reviewer)
<!-- 리뷰어가 특별히 신경 써서 봐주었으면 하는 부분이나, 테스트 시 참고할 사항이 있다면 알려주세요. -->
<!-- 최대한 작성해 주시되 없으면 X라고 써주세요. -->
{Korean bullet points on what reviewers should focus on, or X if nothing special}
```

---

## Section Writing Rules

### 📄 요약 (Summary)

- Explain **what and why** was done on this branch.
- Cover the whole scope in 2–4 sentences.
- Use technical terms where needed; avoid listing file paths.

### 🔗 관련 이슈 (Related Issue)

- The Jira issue number cannot be inferred — output `S14P31S208-{이슈 번호}` as a placeholder.
- If the branch name contains an issue number (e.g. `fe/feat/S14P31S208-42-fortune`), extract and fill it automatically.

### ✨ 주요 변경 사항 (Key Changes)

- List **essential changes only** as bullets, derived from changed files and commits.
- Describe by feature or purpose — never by file name.
- 3–7 bullets is the right length.

**Example:**
```
- 배럴 import 경로를 리소스 폴더 배럴(`../hooks`, `../utils`) 기준으로 정규화
- 외부 소비자의 feature 내부 파일 직접 참조를 public 배럴로 일괄 수정
- `_infra/` 파일 직접 import 예외 규칙 적용
```

### 📸 스크린샷 (Screenshots)

- If `*.tsx`, CSS, or design token files changed → output `"UI 변경 사항이 있습니다. 스크린샷을 첨부해주세요."`
- If no UI changes → output `(없음)`

### 🙏 리뷰어에게 (To the Reviewer)

- Write context the reviewer might miss, potential side effects, or things to verify during testing.
- If nothing notable, output `X`.

---

## Full Output Example

```
**Title:**
[FE] 배럴 import 규칙 정리 및 경로 정규화

**Description:**
## 📄 요약 (Summary)
<!-- 이번 PR에서 어떤 작업을 했는지 간단하게 설명해주세요. -->
feature 내부 구현 파일의 import 경로를 프로젝트 배럴 규칙에 맞게 정규화했습니다.
외부 소비자는 public 배럴만, feature 내부는 리소스 폴더 배럴(`../hooks`, `../utils`) 또는 평면 파일을 사용하도록 일괄 수정했습니다.

<br>

## 🔗 관련 이슈 (Related Issue)
<!-- 본 PR과 관련된 Jira 이슈 번호를 모두 적어주세요. -->
- Closes S14P31S208-{이슈 번호}

<br>

## ✨ 주요 변경 사항 (Key Changes)
<!-- 이번 PR에서 중점적으로 봐야 할 변경 사항을 목록으로 작성해주세요. -->
<!-- 리스트 형식으로 작성해주세요. -->
- 외부 소비자의 feature 내부 파일 직접 참조를 public 배럴(`@/features/relay-drawing`)로 수정
- feature 내부 훅 import를 리소스 폴더 배럴(`../hooks`)로 정규화
- `_infra/` 직접 import 예외 규칙 적용

<br>

## 📸 스크린샷 (Screenshots)
<!-- UI 변경사항이 있다면 스크린샷을 첨부해주세요. (없다면 생략) -->
(없음)

<br>

## 🙏 리뷰어에게 (To the Reviewer)
<!-- 리뷰어가 특별히 신경 써서 봐주었으면 하는 부분이나, 테스트 시 참고할 사항이 있다면 알려주세요. -->
<!-- 최대한 작성해 주시되 없으면 X라고 써주세요. -->
- 기존 배럴 우회 경로가 남아 있지 않은지 전체적으로 확인해주세요.
```
