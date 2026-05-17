import { ApiError } from '@/shared/apis'
import type { ApiResponse } from '@/shared/types'

/**
 * ApiResponse<T> 봉투를 까서 data만 꺼내거나 ApiError로 throw하는 헬퍼.
 *
 * 왜 필요한가?
 *
 * 백엔드 응답은 모두 봉투 구조: { success, message, data, errors }.
 * 호출 측이 매번 다음을 반복하지 않도록 도메인 API 계층에서 한 번에 처리한다.
 *   1) success === false 체크 → 비즈니스 실패면 throw로 변환
 *   2) data 필드 추출 → 호출 측은 T만 다루면 됨
 *
 * 1번이 핵심 책임이다. ky는 4xx/5xx만 자동으로 throw하므로, 서버가 200으로
 * 응답하면서 본문에 success: false를 담아 보내는 케이스(검증 실패 등)는 그냥
 * 통과시킨다. apiUnwrap이 없으면 호출 측마다 if (!res.success) throw ...를
 * 빼먹지 않고 써야 하는데, 한 번이라도 까먹으면 success: false인 응답을
 * 정상값처럼 쓰는 버그가 난다. 이 체크를 도메인 API 함수 시그니처에 박아두는 게
 * apiUnwrap의 본질이다. data 추출은 그 김에 끼워 넣은 편의.
 *
 * 왜 ky의 afterResponse 훅에 넣지 않았는가?
 *   트랜스포트(apiClient)는 인증·봉투 같은 도메인 약속을 모르도록 두고, 봉투
 *   해석은 도메인 API 호출자(shared/apis/* + 본 헬퍼)의 책임으로 분리하기
 *   위함이다.
 *
 * 위치 결정: 봉투 변환 자체는 일반 Promise → Promise 변환 유틸이라 utils로
 * 분리했다. 단, 변환 결과로 던지는 에러 타입(ApiError)은 도메인 타입이라
 * shared/apis/apiError.ts에 두고 여기서 import해 쓴다.
 *
 * 사용 예:
 *   export const postAnonymousVerify = (userUuid: string) =>
 *     apiUnwrap(api.post<ApiResponse<AnonymousUserVerifyResponse>>(
 *       'users/anonymous/verify',
 *       { userUuid },
 *     ))
 */
export async function apiUnwrap<T>(promise: Promise<ApiResponse<T>>): Promise<T> {
  const response = await promise
  if (!response.success) throw new ApiError(response.message, response.errors, response.data)
  return response.data
}
