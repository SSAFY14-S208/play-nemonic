// 브라우저 다운로드 트리거 유틸 — Blob을 임시 object URL로 만들어 보이지 않는
// <a download> 클릭으로 OS 저장 다이얼로그를 띄운다.

// 파일명에 쓰면 안 되는 OS 공통 금지 문자를 underscore로 치환. 빈 결과면
// fallback으로 'download' 사용.
export function sanitizeDownloadFilename(name: string): string {
  const sanitized = name.replace(/[\\/:*?"<>|]+/g, '_').trim()
  return sanitized || 'download'
}

// Blob의 MIME 타입에서 이미지 확장자를 추론. 기본값은 png — 백엔드가 Content-Type을
// 설정하지 않거나 jpeg로 내려보내는 경우라도 정적 이미지는 png로 저장한다.
// 플립북 같은 애니메이션만 gif로 구분.
export function inferImageExtensionFromBlob(blob: Blob, fallback = 'png'): string {
  const mimeType = blob.type.toLowerCase()
  if (mimeType.includes('gif')) return 'gif'
  return fallback
}

export function downloadBlob(blob: Blob, filename: string): void {
  if (typeof window === 'undefined') return

  const objectUrl = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = objectUrl
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)

  // 브라우저가 다운로드를 시작할 여유를 두고 URL 해제. revoke 즉시 호출하면
  // 일부 브라우저에서 다운로드가 취소되는 경우가 있다.
  window.setTimeout(() => URL.revokeObjectURL(objectUrl), 1000)
}
