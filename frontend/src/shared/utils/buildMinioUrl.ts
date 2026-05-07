import { runtime } from '@/shared/config'

/**
 * MinIO object key를 브라우저에서 접근 가능한 전체 URL로 조합한다.
 *
 * 백엔드 API 응답의 `thumbnailUrl`, `contentUrl` 등은 MinIO object key
 * (예: `relay/results/.../thumbnail.png`)로 내려오므로, 렌더링 전에 반드시
 * 이 함수로 전체 URL을 구성해야 한다.
 *
 * @example buildMinioUrl('relay/results/abc/thumbnail.png')
 *   → 'https://k14s208.p.ssafy.io:9443/nemonic/relay/results/abc/thumbnail.png'
 */
export function buildMinioUrl(objectKey: string): string {
  return `${runtime.minioUrl}/${runtime.minioBucket}/${objectKey}`
}
