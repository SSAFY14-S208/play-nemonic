/**
 * 백엔드 비즈니스 실패를 표현하는 커스텀 에러 클래스.
 *
 * 왜 표준 Error를 쓰지 않고 별도 클래스를 만들었는가?
 *
 * 1) 데이터 — Error는 message 한 줄만 담을 수 있다.
 *    백엔드는 실패 시 message(사람이 읽는 한 줄) 외에 errors(필드별 디테일)도 같이
 *    돌려준다. 예: { nickname: "10자 초과", userUuid: "존재하지 않음" }.
 *    이걸 보존해야 폼 인풋 아래에 필드별 빨간 메시지를 띄울 수 있어서, errors 필드를
 *    추가로 가진 클래스가 필요하다.
 *
 * 2) 분류 — catch 블록은 여러 종류의 에러가 한 군데로 떨어진다.
 *    ApiError(우리 백엔드 비즈니스 실패) / ky의 HTTPError(4xx·5xx) / TimeoutError /
 *    네트워크 끊김(TypeError) 등. 화면별로 분기 처리(필드 에러 표시 vs 토스트 vs
 *    재시도 버튼)를 해야 하는데, 표준 Error로만 던지면 다 instanceof Error가 true라
 *    구분이 불가능하다. 별도 클래스가 있어야 instanceof ApiError로 분기할 수 있다.
 *
 * 왜 export까지 하는가?
 *   instanceof는 클래스 이름이 아니라 prototype 객체 참조로 판정한다. catch 측이
 *   같은 ApiError 클래스를 import해서 써야 instanceof가 매칭된다. 다른 모듈에서
 *   똑같은 모양으로 재정의하면 prototype이 달라서 항상 false가 되고, 분기는 죽은
 *   코드가 된다.
 *
 * 왜 그냥 throw new Error("...")로 끝내지 않는가?
 *   message만 던지면 errors 맵이 사라지고, instanceof로 종류도 못 가린다. JS의
 *   throw/catch/instanceof 메커니즘이 prototype 기반으로 짜여 있어서, 분류 가능한
 *   에러를 만들려면 사실상 class extends Error가 유일한 표준 길이다(ky·axios·
 *   Prisma·Next.js 등 거의 모든 프로덕션 라이브러리가 같은 패턴).
 *
 * 사용 예:
 *   try {
 *     await patchAnonymousNickname(uuid, '망고')
 *   } catch (error) {
 *     if (error instanceof ApiError) {
 *       setNicknameError(error.errors?.nickname)
 *     }
 *   }
 *
 * 위치 결정: 이 클래스는 백엔드 응답 봉투(ApiResponse)와 직접 결합된 도메인 타입이라
 * `shared/apis/`에 둔다. 봉투 해제 함수(apiUnwrap)는 일반 변환 유틸이라
 * `shared/utils/`에 분리되어 있다.
 */
export class ApiError extends Error {
  readonly errors?: Record<string, string>
  readonly data?: unknown

  constructor(message: string, errors?: Record<string, string>, data?: unknown) {
    super(message)
    this.name = 'ApiError'
    this.errors = errors
    this.data = data
  }
}
