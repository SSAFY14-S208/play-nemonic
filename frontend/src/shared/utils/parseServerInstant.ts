// 서버가 timezone 표기 없이 보내는 ISO-8601 문자열을 한국시간 기준으로 파싱한다.
//
// 백엔드(Spring)가 LocalDateTime을 직렬화하면 "2026-05-07T11:01:07"처럼
// timezone suffix가 없는 문자열이 내려온다. 이 값은 서비스 기준 시간대인 KST
// wall-clock으로 보고 +09:00 오프셋을 붙여 해석한다. 이미 Z나 ±HH:MM 오프셋이
// 붙어 있으면 서버가 명시한 instant를 그대로 사용한다.

const DATE_ONLY = /^\d{4}-\d{2}-\d{2}$/
const HAS_TZ_SUFFIX = /Z$|[+-]\d{2}:?\d{2}$/i
const KOREA_TIME_ZONE_OFFSET = '+09:00'

export const KOREA_TIME_ZONE = 'Asia/Seoul'

export function parseServerInstant(iso: string): Date {
  if (DATE_ONLY.test(iso)) {
    return new Date(`${iso}T00:00:00${KOREA_TIME_ZONE_OFFSET}`)
  }

  return new Date(HAS_TZ_SUFFIX.test(iso) ? iso : `${iso}${KOREA_TIME_ZONE_OFFSET}`)
}

export function formatKoreanDateTime(
  value: string | null | undefined,
  options: Intl.DateTimeFormatOptions,
) {
  if (!value) return '—'
  const date = parseServerInstant(value)
  if (Number.isNaN(date.getTime())) return value

  return new Intl.DateTimeFormat('ko-KR', {
    timeZone: KOREA_TIME_ZONE,
    ...options,
  }).format(date)
}
