// System Parameters 도메인 (OpenAPI: tag "System Parameters")

/**
 * `value` 필드가 객체로 올 때의 페이로드.
 *
 * 세 가지 shape이 한 인터페이스에 optional로 섞여 있다:
 *   - 단일값: `{ value: number, description, unit }`
 *   - 범위:   `{ min: number, max: number, description, unit }`
 *   - enum:   `{ default: number, allowed: number[], description, unit }`
 *
 * 어느 필드를 쓸지는 frontend 메타(`features/admin/content-parameters/constants.ts`)가
 * 결정한다. 백엔드 키별로 다른 인터페이스를 만들지 않고 optional 한 벌로 두는 이유는,
 * 백엔드가 동일한 envelope으로 응답하기 때문이다.
 */
export interface SystemParameterValue {
  description?: string
  unit?: string
  value?: number
  min?: number
  max?: number
  default?: number
  allowed?: number[]
}

export interface SystemParameterUpdatedBy {
  id: number
  nickname: string
}

export interface SystemParameterResponse {
  id: number
  key: string
  /**
   * 백엔드는 `value`를 두 가지 형태로 보낸다:
   *   - 객체: `{ value | min/max | default/allowed, unit, description }` — 미수정 또는 범위/enum.
   *   - 문자열: 단일값 파라미터가 수정된 직후 `"50"` 같은 raw 숫자 문자열로 떨어짐.
   * 호출 측은 boundary에서 객체 형태로 정규화한 뒤 사용한다 (useContentParameters의 normalizeValue 참고).
   */
  value: SystemParameterValue | string
  updatedBy: SystemParameterUpdatedBy | null
  createdAt: string
  updatedAt: string
}

export interface SystemParameterListResponse {
  items: SystemParameterResponse[]
  totalElements: number
}

/**
 * PATCH /backoffice/system-parameters — 필드 기반 partial update.
 *
 * Swagger 스펙(2026-05-12 확인): 식별자는 id가 아니라 **camelCase 필드명**.
 * 예시:
 *   {
 *     "communityMaxMemoCount": { "value": 55, "unit": "count", "description": "..." },
 *     "relayRoomParticipantLimit": { "min": 3, "max": 8, "unit": "people", "description": "..." }
 *   }
 *
 * 각 필드는 모두 optional — 수정할 항목만 포함하면 된다. 단, 각 value 객체 안의
 * `unit`/`description`은 백엔드가 같이 받기를 기대하므로 함께 보낸다 (생략 시 string으로
 * 저장되어 GET 응답이 깨짐).
 *
 * GET 응답의 `key`(`community.max_memo_count` dotted snake)와 PATCH의 필드명
 * (`communityMaxMemoCount` camelCase)이 다르다 — `constants.ts`의 `patchKey`로 매핑.
 */
export interface SystemParameterBulkUpdateRequest {
  communityMaxMemoCount?: SystemParameterValue
  relayRoomParticipantLimit?: SystemParameterValue
  relayRoomTimeLimitSeconds?: SystemParameterValue
  relayReconnectGraceSeconds?: SystemParameterValue
  flipbookRoomParticipantLimit?: SystemParameterValue
  flipbookRoomTimeLimitSeconds?: SystemParameterValue
  flipbookMinFramesPerFlipbook?: SystemParameterValue
  flipbookReconnectGraceSeconds?: SystemParameterValue
  fortuneDailyLimit?: SystemParameterValue
  csInquiryUnresolvedAlertThresholdHours?: SystemParameterValue
}

export type SystemParameterPatchKey = keyof SystemParameterBulkUpdateRequest

export interface SystemParameterListParams {
  keyword?: string
}
