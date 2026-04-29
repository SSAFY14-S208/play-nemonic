# Contributing Guide

이 문서는 팀원이 처음 프로젝트를 받을 때 따라 할 개발 흐름을 정리한다.
상세 브랜치, 커밋, Jira 컨벤션은 `README.md`를 기준으로 한다.

## 1. 브랜치 전략

이 프로젝트는 Git Flow 기반으로 운영한다.

```text
be/feat/*, fe/feat/* -> be/dev, fe/dev, ai/dev -> dev
```

- `master` 브랜치는 사용하지 않는다.
- 백엔드 기능 브랜치는 `be/feat/<작업명>` 형식을 사용한다.
- 백엔드 리팩터링 브랜치는 `be/refactor/<작업명>` 형식을 사용한다.
- 브랜치명은 소문자 케밥케이스를 사용한다.

예시:

```text
be/feat/community-memo-create
be/fix/fortune-daily-limit
be/refactor/common-response
```

## 2. 커밋 메시지

커밋 메시지는 다음 형식을 따른다.

```text
[BE] <type>: <한글 제목>
```

주요 타입:

- `add`: 파일 단위 추가
- `feat`: 새로운 기능 추가 또는 기능 수정
- `fix`: 오류 수정
- `chore`: 빌드, 설정, 주석, 오타 등 코드 동작 외 변경
- `docs`: 문서 수정
- `delete`: 삭제
- `style`: 포맷, 코드 스타일 변경
- `refactor`: 리팩터링
- `test`: 테스트 추가 또는 변경

예시:

```text
[BE] feat: 커뮤니티 메모 생성 API 구현
[BE] fix: 운세 일일 제한 검증 오류 수정
[BE] docs: 백엔드 하네스 사용법 추가
```

## 3. Jira 규칙

- 프로젝트 키는 `S14P31S208`이다.
- 모든 작업은 Jira 이슈를 만든 뒤 진행한다.
- Task 제목에는 앞에 `[BE]` 같은 카테고리를 붙이지 않고 API 또는 작업 단위로 짧게 작성한다.
- 상태 흐름은 `TODO -> IN PROGRESS -> DONE`이다.
- `DONE`은 리뷰 승인 및 머지 완료 후 처리한다.

## 4. 백엔드 개발 시작

로컬 환경 파일을 만든다.

```powershell
Copy-Item .\backend\.env.example .\backend\.env
```

필요하면 `backend/.env` 값을 개인 로컬 환경에 맞게 수정한다.

로컬 의존성을 실행한다.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\backend\scripts\local-up.ps1
```

## 5. 검증 명령

빠른 테스트:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\backend\scripts\verify.ps1 -Fast
```

포맷 적용:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\backend\scripts\format.ps1
```

기본 검증:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\backend\scripts\verify.ps1
```

DB migration 검증:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\backend\scripts\verify-migration.ps1
```

최종 점검:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\backend\scripts\session-close.ps1
```

## 6. Merge Request 전 확인

- 관련 Jira 이슈를 MR에 연결했다.
- `backend/docs/product-spec/`의 관련 기획을 확인했다.
- API 변경 시 `backend/docs/api/*.http`를 갱신했다.
- DB 변경 시 Flyway migration을 추가했다.
- Flyway migration 변경 시 `verify-migration.ps1`가 통과했다.
- 테스트를 추가 또는 갱신했다.
- `format.ps1`와 `verify.ps1`가 통과했다.
- 비밀값, 로컬 캐시, `.env` 파일을 커밋하지 않았다.

## 7. 참고 문서

- `AGENTS.md`: AI/Codex 작업 지침
- `backend/docs/backend-architecture.md`: 백엔드 패키지 구조
- `backend/docs/codex-harness.md`: 하네스 구조
- `backend/docs/product-spec/`: 제품 기획서
- `backend/docs/codex-prompt-templates.md`: Codex 요청 템플릿
