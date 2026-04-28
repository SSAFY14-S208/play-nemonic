# Codex Memory Engineering

## 목적

대화 내용은 길어지면 압축되거나 새 세션에서 사라질 수 있다. 이 프로젝트는 중요한 기억을 대화 밖으로
꺼내서 repo 안에 저장한다.

## Memory Layers

### 1. Stable instruction memory

파일:

- repo-root `AGENTS.md`
- `backend/docs/backend-architecture.md`
- `backend/docs/codex-harness.md`
- `backend/docs/product-spec/`

역할:

- 항상 지켜야 하는 규칙
- 빌드, 테스트, 패키지 구조, 작업 방식
- 제품 의도, 사용자 흐름, 정책, 운영 규칙
- 팀 합의가 필요한 안정적인 지식

### 2. Current state memory

파일:

- `backend/docs/codex-current-state.md`

역할:

- 지금 어디까지 준비됐는지
- 다음에 이어서 할 작업
- 최근 검증 결과
- 알려진 위험과 TODO

사용법:

- 큰 작업이 끝나면 갱신한다.
- 새 세션을 시작할 때 `AGENTS.md`와 함께 먼저 읽는다.
- 대화 압축이나 새 스레드 이후 복구 지점으로 사용한다.

### 3. Decision memory

폴더:

- `backend/docs/decisions/`

역할:

- 왜 그런 선택을 했는지 기록한다.
- 나중에 같은 논쟁을 반복하지 않게 한다.
- 큰 구조 변경, 테스트 전략, 인프라 전략은 ADR로 남긴다.

### 4. Session handoff memory

파일:

- `backend/docs/session-handoff-template.md`

역할:

- 긴 작업을 멈추거나 다른 세션으로 넘길 때 사용한다.
- 수정 파일, 검증 결과, 남은 위험을 요약한다.

### 5. Executable memory

파일:

- `backend/scripts/format.ps1`
- `backend/scripts/verify.ps1`
- `backend/scripts/check-harness.ps1`
- `backend/scripts/session-close.ps1`

역할:

- 말로 된 기억을 실행 가능한 습관으로 만든다.
- “검증했다”의 의미를 명령으로 고정한다.

## When To Update Memory

다음 경우에는 문서 기억을 갱신한다.

- 새 아키텍처 규칙이 생겼다.
- 하네스 스크립트 동작이 바뀌었다.
- 반복해서 재발할 수 있는 환경 이슈를 발견했다.
- 큰 기능 작업이 끝났고 다음 작업자가 이어받아야 한다.
- 테스트 전략이나 패키지 구조 결정을 내렸다.

작은 오타 수정이나 단일 테스트 수정은 current state를 갱신하지 않아도 된다.

## Session Start Protocol

새 Codex 세션에서는 이렇게 시작한다.

```text
AGENTS.md와 backend/docs/codex-current-state.md를 먼저 읽고 이어서 작업해줘.
필요하면 backend/docs/backend-architecture.md와 backend/docs/codex-harness.md도 참고해줘.
```

## Session End Protocol

큰 작업이 끝나면 다음을 수행한다.

1. repo root에서 `backend/scripts/format.ps1` 실행
2. repo root에서 `backend/scripts/verify.ps1` 실행
3. `backend/docs/codex-current-state.md` 갱신 여부 판단
4. 설계 결정이 있으면 `backend/docs/decisions/`에 ADR 추가
5. 최종 응답에 변경 파일, 검증 결과, 남은 리스크 요약

자동화하려면 `backend/scripts/session-close.ps1`를 사용한다.
