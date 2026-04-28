# Backend Architecture Convention

## 개요

백엔드는 기능, 즉 도메인 단위로 패키지를 구성하고 각 기능 패키지 내부를 계층별로 분리한다.

현재 루트 패키지는 `com.nemonicworld`이다. 새 기능은 이 문서의 목표 구조를 따른다.
기존 코드의 패키지 이동은 기능 개발과 섞지 않고, 별도 리팩터링 작업으로 점진적으로 진행한다.

## 목표 패키지 구조

```text
src/main/java/com/nemonicworld/
|
|-- global/                         # 공통 설정, 보안, 예외, 응답, 유틸
|   |-- config/                     # Security, CORS, Redis, Swagger 등 설정
|   |-- exception/                  # 공통 예외 및 전역 예외 처리
|   |-- common/                     # 공통 응답, 공통 유틸리티
|   `-- auth/                       # JWT, 인증 필터, 현재 사용자 조회 등 인프라성 인증/인가
|
|-- <feature>/                      # 기능 또는 도메인 패키지
|   |-- api/                        # Controller 계층
|   |-- application/                # Service 계층
|   |-- dao/                        # Repository 계층
|   |-- domain/                     # Entity, Enum, 값 객체
|   `-- dto/                        # 요청/응답 DTO
|       |-- request/                # 클라이언트 요청 DTO
|       `-- response/               # 클라이언트 응답 DTO
|
`-- BackendApplication.java         # Spring Boot 메인 클래스
```

## 현재 코드 전환 규칙

현재 코드에는 `config`, `common`, `auth.controller`, `community.controller` 같은 초기 패키지 구조가 남아 있다.

- 새 기능은 목표 구조의 `<feature>/api`, `<feature>/application`, `<feature>/dao`, `<feature>/domain`,
  `<feature>/dto/request`, `<feature>/dto/response`를 따른다.
- 기존 기능을 수정할 때 패키지 이동이 필요하면 작업 범위에 명시하고 테스트를 함께 갱신한다.
- 단순 기능 추가와 대규모 패키지 이동을 한 PR에 섞지 않는다.
- `global` 이동은 공통 설정/예외/응답 구조가 안정된 뒤 별도 리팩터링으로 처리한다.

## 계층별 역할

### api

- `@RestController` 클래스가 위치한다.
- HTTP 요청을 받고, 요청 DTO를 검증하고, application 계층으로 위임한다.
- 비즈니스 로직을 포함하지 않는다.
- Entity를 직접 반환하지 않고 Response DTO 또는 공통 응답 형태로 반환한다.

### application

- `@Service` 클래스가 위치한다.
- 핵심 비즈니스 로직과 유스케이스 흐름을 처리한다.
- 트랜잭션 경계를 담당한다.
- 여러 Repository 호출이 필요한 작업은 이 계층에서 조합한다.

### dao

- `@Repository` 인터페이스와 데이터 접근 구현체가 위치한다.
- Spring Data JPA Repository, QueryDSL, JDBC 기반 접근 로직을 둔다.
- HTTP 요청/응답 DTO에 의존하지 않는다.

### domain

- `@Entity`, Enum, 값 객체 등 도메인 모델이 위치한다.
- 도메인 상태와 기본 규칙을 표현한다.
- 외부 API 응답 형식이나 웹 계층 관심사를 포함하지 않는다.

### dto

- 계층 간 데이터 전달에 사용하는 객체가 위치한다.
- `request/`: 클라이언트에서 서버로 들어오는 요청 DTO
- `response/`: 서버에서 클라이언트로 나가는 응답 DTO
- Request DTO에는 `@NotNull`, `@NotBlank`, `@Size`, `@Email` 등 검증 어노테이션을 적극 사용한다.

## 예시: community 기능

```text
community/
|-- api/
|   `-- CommunityController.java
|-- application/
|   `-- CommunityService.java
|-- dao/
|   `-- CommunityRepository.java
|-- domain/
|   |-- Community.java
|   `-- CommunityStatus.java
`-- dto/
    |-- request/
    |   |-- CommunityCreateRequest.java
    |   `-- CommunityUpdateRequest.java
    `-- response/
        |-- CommunityDetailResponse.java
        `-- CommunityListResponse.java
```

## 네이밍 규칙

| 계층 | 네이밍 패턴 | 예시 |
| --- | --- | --- |
| Controller | `<Feature>Controller` | `CommunityController` |
| Service | `<Feature>Service` | `CommunityService` |
| Repository | `<Feature>Repository` | `CommunityRepository` |
| Entity | `<Feature>` | `Community` |
| Request DTO | `<Feature><Action>Request` | `CommunityCreateRequest` |
| Response DTO | `<Feature><Detail>Response` | `CommunityDetailResponse` |

## 의존성 규칙

- `api`는 `application`을 호출한다.
- `application`은 `dao`, `domain`, `dto`를 조합한다.
- `dao`는 `domain`을 다룬다.
- `domain`은 다른 계층에 의존하지 않는다.
- Controller에서 다른 도메인의 Repository를 직접 호출하지 않는다.
- 도메인 간 협력이 필요하면 각 도메인의 application 계층을 통해 처리한다.
- 공통 응답, 공통 예외, 보안 인프라 등은 `global` 아래로 모은다.

## 응답과 예외 규칙

- API 응답은 기존 `ApiResponse` 스타일을 따른다.
- Entity를 Controller 응답으로 직접 반환하지 않는다.
- 실패 응답은 전역 예외 처리기를 통해 일관된 형식으로 변환한다.
- 도메인별 예외가 필요하면 의미 있는 이름을 사용하고, 공통 HTTP 변환은 global exception에서 처리한다.

## 테스트 패키지 구조

테스트는 운영 코드 구조를 최대한 반영한다.

```text
src/test/java/com/nemonicworld/
|-- support/                        # 테스트 공통 애노테이션, fixture, helper
|-- <feature>/
|   |-- api/
|   |-- application/
|   |-- dao/
|   `-- domain/
```

- Spring context 통합 테스트는 `@IntegrationTest`를 사용한다.
- Random port HTTP 통합 테스트는 `@HttpIntegrationTest`를 사용한다.
- fixture builder와 test helper는 `support` 패키지에 둔다.
- 기능 변경 시 성공 케이스와 주요 실패 케이스를 함께 검증한다.

## 주의 사항

- 새 추상화는 실제 중복이나 복잡도를 줄일 때만 추가한다.
- 단일 사용처를 위해 mapper, facade, adapter를 미리 만들지 않는다.
- 기능 개발 PR에서 무관한 패키지 이동, 포맷 변경, 이름 변경을 섞지 않는다.
- 패키지 구조를 바꾸는 경우 import, 테스트, API 요청 샘플을 함께 갱신한다.
