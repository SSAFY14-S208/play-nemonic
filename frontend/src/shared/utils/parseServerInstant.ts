// 서버가 timezone 표기 없이 보내는 ISO-8601 문자열을 안전하게 Date로 파싱한다.
//
// 백엔드(Spring)가 LocalDateTime을 직렬화하면 "2026-05-07T11:01:07"처럼
// timezone suffix가 없는 문자열이 내려온다. JavaScript의 new Date()는 이 경우
// 브라우저 로컬 timezone으로 해석해버려, 서버가 UTC clock으로 도는 환경에서는
// (Date)와 (실제 의도된 시각)이 timezone 차이만큼 어긋난다.
//
// 이 프로젝트의 백엔드는 UTC clock에서 동작하므로, timezone suffix가 없는
// 문자열은 "Z"를 붙여 UTC로 강제 해석한다. 이미 Z나 ±HH:MM 오프셋이 붙어
// 있으면 그대로 둔다.
//
// 영구 해결은 백엔드가 Instant/OffsetDateTime을 사용해 timezone을 포함시키는
// 것이다. 이 헬퍼는 그때까지의 어댑터.

const HAS_TZ_SUFFIX = /Z$|[+-]\d{2}:?\d{2}$/

export function parseServerInstant(iso: string): Date {
  return new Date(HAS_TZ_SUFFIX.test(iso) ? iso : `${iso}Z`)
}
