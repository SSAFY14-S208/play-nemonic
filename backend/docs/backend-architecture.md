# Backend Architecture Convention

## 개요

백엔드는 업무 영역 또는 기능 단위로 최상위 패키지를 나누고, 각 패키지 내부를 계층별로 분리한다.

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
|   |-- controller/                 # Controller 계층
|   |-- service/                    # Service 계층
|   |-- repository/                 # Repository 계층
|   |-- entity/                     # JPA Entity, 영속성 Enum, 값 객체
|   `-- dto/                        # 요청/응답 DTO
|       |-- request/                # 클라이언트 요청 DTO
|       `-- response/               # 클라이언트 응답 DTO
|
`-- BackendApplication.java         # Spring Boot 메인 클래스
```

## 현재 코드 전환 규칙

현재 코드에는 `config`, `common` 같은 초기 공통 패키지 구조가 남아 있다.

- 새 기능은 목표 구조의 `<feature>/controller`, `<feature>/service`, `<feature>/repository`,
  `<feature>/entity`, `<feature>/dto/request`, `<feature>/dto/response`를 따른다.
- 기존 기능을 수정할 때 패키지 이동이 필요하면 작업 범위에 명시하고 테스트를 함께 갱신한다.
- 단순 기능 추가와 대규모 패키지 이동을 한 PR에 섞지 않는다.
- `global` 이동은 공통 설정/예외/응답 구조가 안정된 뒤 별도 리팩터링으로 처리한다.

## 계층별 역할

### controller

- `@RestController` 클래스가 위치한다.
- HTTP 요청을 받고, 요청 DTO를 검증하고, service 계층으로 위임한다.
- 비즈니스 로직을 포함하지 않는다.
- Entity를 직접 반환하지 않고 Response DTO 또는 공통 응답 형태로 반환한다.

### Swagger/OpenAPI

- Controller method parameter 이름은 반드시 명시해서 Swagger에 `arg0`, `arg1`, `arg2` 같은 컴파일러 생성 이름이 노출되지 않게 한다.
- path variable은 `@PathVariable("id")`, query parameter는 `@RequestParam(name = "page")`처럼 이름을 명시한다.
- 공통 헤더는 문자열을 반복하지 않고 `@RequestHeader(value = HeaderConstants.VALUE)` 또는 프로젝트의 헤더 상수를 사용한다.
- API 계약에 포함된 필수 path, query, header parameter는 `@Parameter`로 문서화해서 Swagger에 실제 계약과 같은 이름과 필수 여부가 보이게 한다.
- 예상 가능한 실패 응답은 `@ApiResponse` 예시를 추가한다. 실패 예시는 `success: false`를 보여야 하며 성공 DTO 예시를 재사용하지 않는다.
- 공통 `ApiResponse.errors` 스키마 예시는 특정 API의 필드 오류로 고정하지 않고 범용 예시로 유지한다. API별 검증 메시지는 operation-level 실패 예시에 둔다.
- 내부 예외 메시지, stack trace, access key, bucket 이름, secret 같은 민감 정보는 Swagger 예시에 노출하지 않는다.

### service

- service 패키지는 `<Feature>Service` 인터페이스와 `<Feature>ServiceImpl` 구현체로 구성한다.
- Controller는 구현체가 아니라 service 인터페이스에 의존한다.
- `@Service`는 인터페이스가 아니라 구현체에만 붙인다.
- 인터페이스에는 Controller가 사용하는 public use case method만 둔다.
- private helper나 구현 세부사항은 Impl에 둔다.

- `@Service` 클래스가 위치한다.
- 핵심 비즈니스 로직과 유스케이스 흐름을 처리한다.
- 트랜잭션 경계를 담당한다.
- 여러 Repository 호출이 필요한 작업은 이 계층에서 조합한다.

### repository

- `@Repository` 인터페이스와 데이터 접근 구현체가 위치한다.
- Spring Data JPA Repository, QueryDSL, JDBC 기반 접근 로직을 둔다.
- HTTP 요청/응답 DTO에 의존하지 않는다.

### entity

- `@Entity`, Enum, 값 객체 등 DB 영속성 모델이 위치한다.
- 기능 패키지가 도메인 경계이므로, 별도 `domain` 패키지를 만들지 않는다.
- 엔티티의 상태와 기본 규칙을 표현한다.
- 외부 API 응답 형식이나 웹 계층 관심사를 포함하지 않는다.

### dto

- 계층 간 데이터 전달에 사용하는 객체가 위치한다.
- `request/`: 클라이언트에서 서버로 들어오는 요청 DTO
- `response/`: 서버에서 클라이언트로 나가는 응답 DTO
- Request DTO에는 `@NotNull`, `@NotBlank`, `@Size`, `@Email` 등 검증 어노테이션을 적극 사용한다.

## 예시: community 기능

```text
community/
|-- controller/
|   `-- CommunityController.java
|-- service/
|   |-- CommunityService.java
|   `-- CommunityServiceImpl.java
|-- repository/
|   `-- CommunityRepository.java
|-- entity/
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

- `controller`는 `service`를 호출한다.
- `service`는 `repository`, `entity`, `dto`를 조합한다.
- `repository`는 `entity`를 다룬다.
- `entity`는 다른 계층에 의존하지 않는다.
- Controller에서 다른 도메인의 Repository를 직접 호출하지 않는다.
- 도메인 간 협력이 필요하면 각 도메인의 service 계층을 통해 처리한다.
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
|   |-- controller/
|   |-- service/
|   |-- repository/
|   `-- entity/
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
