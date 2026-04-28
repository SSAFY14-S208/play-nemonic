# Codex Prompt Templates

이 문서는 Codex 앱에서 백엔드 작업을 요청할 때 사용할 프롬프트 템플릿이다.

좋은 요청은 Codex에게 “무엇을 바꿀지”보다 “어떤 상태가 성공인지”를 알려준다.
가능하면 `Goal`, `Scope`, `Constraints`, `Acceptance Criteria`, `Verification`을 채워서 요청한다.

검증 명령은 기본적으로 repository root에서 실행한다.

## 기본 기능 구현

```md
## Goal
<구현할 기능을 한 문장으로 적기>

## Context
- 관련 Jira/이슈:
- 관련 API:
- 참고 문서:

## Scope
- 변경 가능한 패키지/파일:
- 새로 추가할 계층:
- 건드리지 말아야 할 영역:

## Constraints
- `backend/docs/backend-architecture.md` 구조를 따른다.
- Controller에는 비즈니스 로직을 넣지 않는다.
- Entity를 API 응답으로 직접 반환하지 않는다.
- 기존 `ApiResponse` 응답 스타일을 유지한다.
- 불필요한 추상화나 미래 대비 코드를 추가하지 않는다.

## Acceptance Criteria
- <성공 케이스 1>
- <성공 케이스 2>
- <실패/예외 케이스>
- 관련 테스트를 추가하거나 갱신한다.

## Verification
- `powershell -NoProfile -ExecutionPolicy Bypass -File .\backend\scripts\format.ps1`
- `powershell -NoProfile -ExecutionPolicy Bypass -File .\backend\scripts\verify.ps1`
```

## API 구현

```md
## Goal
<엔드포인트> API를 구현해줘.

## API Contract
- Method:
- Path:
- Request body:
- Response body:
- Success status:
- Error cases:

## Scope
- `<feature>/api`
- `<feature>/application`
- `<feature>/dao`
- `<feature>/domain`
- `<feature>/dto/request`
- `<feature>/dto/response`
- 관련 테스트
- `backend/docs/api/*.http`

## Constraints
- `backend/docs/backend-architecture.md`의 feature 패키지 구조를 따른다.
- Request DTO에는 필요한 validation annotation을 붙인다.
- Response DTO로 응답한다.
- Entity를 Controller에서 직접 반환하지 않는다.
- Controller는 Service에 위임한다.

## Acceptance Criteria
- 정상 요청 시 기대 응답을 반환한다.
- validation 실패 시 일관된 실패 응답을 반환한다.
- 주요 실패 케이스 테스트가 있다.
- `.http` 요청 샘플이 추가 또는 갱신된다.

## Verification
- `powershell -NoProfile -ExecutionPolicy Bypass -File .\backend\scripts\verify.ps1`
```

## 버그 수정

```md
## Goal
<버그 증상>을 수정해줘.

## Reproduction
1. <재현 단계>
2. <재현 단계>
3. <현재 잘못된 결과>

## Expected Behavior
- <기대 결과>

## Scope
- 의심되는 패키지/파일:
- 관련 테스트:

## Constraints
- 먼저 실패하는 테스트나 재현 가능한 검증을 만든다.
- 수정 범위는 버그 원인과 직접 관련된 코드로 제한한다.
- 관련 없는 리팩터링은 하지 않는다.

## Acceptance Criteria
- 기존 버그를 재현하는 테스트가 추가된다.
- 수정 후 해당 테스트가 통과한다.
- 기존 테스트가 깨지지 않는다.

## Verification
- `powershell -NoProfile -ExecutionPolicy Bypass -File .\backend\scripts\verify.ps1`
```

## 리팩터링

```md
## Goal
<리팩터링 목적>을 달성해줘.

## Current Problem
- <현재 구조의 문제>
- <개선해야 하는 이유>

## Scope
- 변경 가능한 파일/패키지:
- 변경 금지 영역:

## Constraints
- 외부 API 동작은 바꾸지 않는다.
- 리팩터링과 기능 추가를 섞지 않는다.
- 패키지 이동이 있으면 테스트와 import를 함께 갱신한다.
- 변경 전후 검증 명령이 통과해야 한다.

## Acceptance Criteria
- 동작은 동일하다.
- 구조가 `backend/docs/backend-architecture.md`와 더 가까워진다.
- 관련 테스트가 유지 또는 보강된다.

## Verification
- 변경 전 가능하면 `backend/scripts/verify.ps1 -Fast`를 실행한다.
- 변경 후 `backend/scripts/format.ps1`와 `backend/scripts/verify.ps1`를 실행한다.
```

## 테스트 추가

```md
## Goal
<대상 기능/계층>의 테스트를 추가해줘.

## Scope
- 테스트 대상:
- 추가할 테스트 종류:
  - Unit test:
  - Web slice test:
  - Integration test:

## Constraints
- Spring context가 필요 없는 로직은 unit test로 작성한다.
- HTTP 통합 테스트에는 `@HttpIntegrationTest`를 사용한다.
- Spring context 통합 테스트에는 `@IntegrationTest`를 사용한다.
- fixture/helper는 `src/test/java/com/nemonicworld/support`에 둔다.

## Acceptance Criteria
- 정상 케이스 테스트가 있다.
- 주요 실패 케이스 테스트가 있다.
- 테스트 이름은 의도를 설명한다.

## Verification
- `powershell -NoProfile -ExecutionPolicy Bypass -File .\backend\scripts\verify.ps1 -Fast`
```

## 코드 리뷰

```md
이 변경사항을 리뷰해줘.

## Review Focus
- 버그 가능성
- 아키텍처 컨벤션 위반
- 테스트 누락
- 보안/인증/인가 위험
- 과한 추상화 또는 불필요한 변경

## Constraints
- 발견사항을 심각도 순서로 정리해줘.
- 파일과 라인을 함께 알려줘.
- 문제가 없으면 없다고 말하고 남은 리스크만 적어줘.
```

## 큰 작업을 서브에이전트로 나누기

```md
이 작업은 서브에이전트를 사용해서 병렬로 진행해줘.

## Goal
<큰 작업 목표>

## Work Split
- Agent A: <읽기/조사 또는 특정 파일 영역>
- Agent B: <구현할 독립 영역>
- Main Agent: 통합, 충돌 해결, 최종 검증

## Constraints
- 서로 같은 파일을 동시에 수정하지 않게 나눠줘.
- 각 서브에이전트의 write scope를 명확히 정해줘.
- 최종 검증은 메인 에이전트가 `backend/scripts/verify.ps1`로 수행해줘.

## Acceptance Criteria
- 각 작업 단위가 독립적으로 완료된다.
- 통합 후 전체 테스트가 통과한다.
```

## 자동화 요청

```md
Codex 앱 자동화를 만들어줘.

## Goal
<반복해서 확인할 일>

## Schedule
- 주기:
- 시간대:
- 시작 시점:

## Scope
- 대상 workspace:
- 실행할 검증:
- 결과 요약 방식:

## Constraints
- 실패하면 원인과 다음 행동을 요약해줘.
- 성공하면 핵심 결과만 짧게 알려줘.
```

## 추천 짧은 프롬프트

작은 작업은 아래처럼 짧게 요청해도 된다.

```md
`backend/docs/backend-architecture.md`를 기준으로 <기능명> API를 구현해줘.
Controller에는 비즈니스 로직을 넣지 말고, Request/Response DTO와 테스트를 포함해줘.
마지막에 `backend/scripts/format.ps1`와 `backend/scripts/verify.ps1`를 실행해서 결과를 알려줘.
```

```md
이 버그를 먼저 테스트로 재현한 뒤 수정해줘: <버그 설명>.
수정 범위는 원인과 직접 관련된 파일로 제한하고, 마지막에 `backend/scripts/verify.ps1`를 실행해줘.
```

## 새 세션 시작

```md
AGENTS.md와 backend/docs/codex-current-state.md를 먼저 읽고 이어서 작업해줘.
새 패키지를 만들거나 구조를 바꾸면 backend/docs/backend-architecture.md를 따라줘.
제품 동작은 backend/docs/product-spec/ 아래의 관련 문서를 먼저 읽고 구현해줘.
작업이 끝나면 backend/docs/agent-review-checklist.md 기준으로 점검하고,
필요하면 backend/docs/codex-current-state.md 또는 ADR을 갱신해줘.
```

## 세션 종료 / 핸드오프

```md
긴 작업을 마무리해줘.

## Required Actions
- backend/scripts/session-close.ps1 실행
- 변경 파일 요약
- 검증 결과 요약
- 남은 리스크 정리
- durable memory 변경이 필요하면 codex-current-state.md 또는 ADR 갱신
```
