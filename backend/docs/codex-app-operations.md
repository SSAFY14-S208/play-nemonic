# Codex App Operations

## 목적

이 문서는 Codex 앱의 스킬, 자동화, 서브에이전트를 이 백엔드 프로젝트에서 언제 사용할지 정리한다.

## Repo-local guidance first

현재 프로젝트의 1차 진입점은 루트의 `AGENTS.md`이고, 백엔드 작업의 1차 기준은 `backend/AGENTS.md`이다.

루트 `AGENTS.md`는 작업 영역을 안내하는 얇은 라우터이고, `backend/AGENTS.md`는 백엔드 전용 규칙을 담는다.
특정 규칙이 여러 저장소에서 반복해서 필요해지면 그때 Codex 스킬로 승격한다.

장기 기억은 `backend/docs/codex-memory.md`와 `backend/docs/codex-current-state.md`를 기준으로 관리한다.

## Skills

스킬은 반복적인 전문 작업을 Codex가 자동으로 떠올리게 만드는 전역 지침이다.

사용하기 좋은 경우:

- 여러 프로젝트에서 같은 백엔드 컨벤션을 반복 사용한다.
- 특정 작업에 매번 같은 절차, 참고 문서, 스크립트가 필요하다.
- 예: `nemonic-backend-development`, `spring-api-test-writer`, `flyway-migration-reviewer`

현재 상태:

- 지금은 repo-local `backend/AGENTS.md`, `backend/docs/backend-architecture.md`, `backend/docs/codex-harness.md`로 충분하다.
- 전역 스킬은 사용자 홈의 Codex 설정 영역에 생성되므로, 팀 합의 후 별도 작업으로 만든다.
- 이 repo의 스킬 후보는 `backend/docs/skills/nemonic-backend-development/SKILL.md`에 준비되어 있다.
- 전역 스킬로 사용하려면 이 폴더를 Codex skills 디렉터리로 설치한다.

## Automations

자동화는 시간 기반으로 반복 실행할 일이 있을 때 사용한다.

사용하기 좋은 경우:

- 매일 아침 `verify.ps1`를 실행해 기본 품질 상태를 확인한다.
- 매주 의존성, 테스트 실패, 문서 누락을 점검한다.
- 장시간 작업 중 일정 시간 뒤 이 스레드로 돌아와 진행 상황을 이어간다.

현재 상태:

- 아직 주기와 알림 목적이 정해지지 않았으므로 자동화를 만들지 않는다.
- 추천 후보는 `Weekly backend harness check`이다. 실행 내용은 `backend/scripts/check-harness.ps1`와
  `backend/scripts/verify.ps1` 결과를 요약하는 것이다.
- 자동화 후보는 `backend/docs/automation-candidates.md`에 기록한다.

## Subagents

서브에이전트는 큰 작업을 병렬로 나눠 진행할 때 사용한다. 별도 설치나 상시 설정 대상이 아니다.

사용하기 좋은 경우:

- 한 에이전트는 API 계층을 구현하고, 다른 에이전트는 테스트를 작성한다.
- 한 에이전트는 기존 코드 구조를 조사하고, 메인 에이전트는 즉시 수정 가능한 부분을 진행한다.
- 리뷰 전용 에이전트로 변경점의 위험과 누락 테스트를 확인한다.

운영 규칙:

- 서로 다른 파일 또는 모듈을 맡길 때만 병렬화한다.
- 같은 파일을 여러 에이전트가 동시에 수정하지 않는다.
- 최종 통합과 검증은 메인 에이전트가 책임진다.
- 서브에이전트가 만든 변경도 repo root에서 `backend/scripts/format.ps1`와 `backend/scripts/verify.ps1`를 통과해야 한다.

## Karpathy-style agent rules

공개된 `andrej-karpathy-skills` 저장소의 핵심은 코딩 에이전트가 추측, 과잉 구현, 광범위 수정, 검증 없는
완료를 줄이도록 행동 규칙을 주는 것이다.

이 프로젝트에서는 같은 방향을 다음 규칙으로 적용한다.

- 구현 전에 모호한 점과 가정을 드러낸다.
- 요청 범위를 넘는 기능이나 추상화를 만들지 않는다.
- 변경은 작고 추적 가능하게 유지한다.
- 성공 기준을 테스트와 명령으로 검증한다.
