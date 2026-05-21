// PII 새니타이저 — 로그 이벤트에서 개인식별정보를 제거한다.
// 스펙 08B §12 보안/PII 규칙을 따른다.

const MESSAGE_CAP = 1024
const STACK_CAP = 4096
const UTM_TERM_CAP = 50

// 동적 세그먼트를 placeholder로 치환
const PATH_PATTERNS: [RegExp, string][] = [
  [/\/share\/[^/]+/g, '/share/:token'],
  [/\/flipbook\/rooms\/[^/]+/g, '/flipbook/rooms/:roomCode'],
  [/\/relay-drawing\/rooms\/[^/]+/g, '/relay-drawing/rooms/:roomCode'],
  [/\/gallery\/\d+/g, '/gallery/:galleryId'],
  [/\/community\/memos\/\d+/g, '/community/memos/:memoId'],
  [/\/invite\/[^/]+/g, '/invite/:code'],
]

// 시크릿 패턴 — JWT, Bearer, sk-* 등
const SECRET_PATTERN =
  /\b(Bearer\s+)\S+|(\?|&)(token|code|key|secret|password|access_token|refresh_token)=[^&\s]+|\beyJ[A-Za-z0-9_-]{10,}\b|\bsk-[A-Za-z0-9]{10,}\b/gi

// 이메일·전화번호 패턴
const EMAIL_PATTERN = /[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}/g
const PHONE_PATTERN = /\b0\d{1,2}[-.\s]?\d{3,4}[-.\s]?\d{4}\b/g

// Authorization 헤더 값 패턴
const AUTH_HEADER_PATTERN = /Authorization:\s*\S+/gi

export function sanitizePath(path: string): string {
  let result = path
  for (const [pattern, replacement] of PATH_PATTERNS) {
    result = result.replace(pattern, replacement)
  }
  return result
}

export function sanitizeReferrer(referrer: string): string {
  if (!referrer) return ''
  try {
    const url = new URL(referrer)
    return url.origin
  } catch {
    return ''
  }
}

function truncateWithMarker(text: string, cap: number): string {
  if (text.length <= cap) return text
  return text.slice(0, cap) + '…[truncated]'
}

function maskSecrets(text: string): string {
  return text
    .replace(SECRET_PATTERN, '***')
    .replace(AUTH_HEADER_PATTERN, 'Authorization: ***')
}

export function sanitizeError(error: {
  type?: string
  message?: string
  stack?: string
}): { type: string; message: string; stack?: string } {
  const type = error.type ?? 'Error'
  const message = truncateWithMarker(
    maskSecrets(error.message ?? ''),
    MESSAGE_CAP,
  )
  const stack = error.stack
    ? truncateWithMarker(maskSecrets(error.stack), STACK_CAP)
    : undefined

  return { type, message, stack }
}

export function sanitizeUtmTerm(term: string): string {
  if (!term) return ''
  let result = term.slice(0, UTM_TERM_CAP)
  result = result.replace(EMAIL_PATTERN, '***')
  result = result.replace(PHONE_PATTERN, '***')
  return result
}
