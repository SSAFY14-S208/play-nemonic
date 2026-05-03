# 0004. 익명 사용자 UUID 전달 헤더 통일

## Status

Accepted

## Context

익명 사용자 기반 API에서 서버가 발급한 UUID 전달 위치가 request body, query parameter, header로 섞여 있었다.
이 때문에 Swagger 테스트와 프론트 연동 시 API마다 UUID 입력 위치를 다시 확인해야 했고, body가 실제 비즈니스 데이터인지 식별자인지 구분하기 어려웠다.

## Decision

기존 익명 사용자를 식별해야 하는 API는 공통 요청 헤더 `X-Anonymous-User-UUID`를 사용한다.

- `POST /api/v1/users/anonymous`는 신규 UUID 발급 API이므로 이 헤더를 요구하지 않는다.
- User-Agent는 기존 정책대로 선택 헤더로 유지한다.
- `galleryId`, `fileId` 같은 리소스 식별자는 path variable로 유지한다.
- `page`, `size` 같은 조회 옵션은 query parameter로 유지한다.
- request body에는 닉네임, 생년월일, 파일 presign 요청 같은 비즈니스 데이터만 둔다.

## Consequences

- User, Gallery, Files API의 Swagger 화면에서 익명 사용자 UUID는 header parameter로 입력한다.
- 기존 클라이언트는 `userUuid` body/query 또는 `X-User-UUID` 대신 `X-Anonymous-User-UUID`를 보내도록 수정해야 한다.
- query parameter는 필터/페이징 의미로 더 명확해진다.
- request body schema에서 사용자 식별자가 빠져 API별 본문 구조가 단순해진다.
