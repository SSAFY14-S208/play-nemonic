const COMMUNITY_MEMO_SHARE_PURPOSE = 'community_memo_share'
const COMMUNITY_MEMO_ARTIFACT_KIND = 'community_memo'

interface ShareTokenPayload {
  purpose?: string
  artifactKind?: string
}

export type ShareSearchParams = Record<string, string | string[] | undefined>

function decodeShareTokenPayload(shareToken: string): ShareTokenPayload | null {
  const [encodedPayload] = shareToken.trim().split('.')
  if (!encodedPayload) return null

  try {
    const normalizedPayload = encodedPayload.replace(/-/g, '+').replace(/_/g, '/')
    const paddedPayload = normalizedPayload.padEnd(
      Math.ceil(normalizedPayload.length / 4) * 4,
      '=',
    )
    const decodedPayload = Buffer.from(paddedPayload, 'base64').toString('utf8')
    const payload = JSON.parse(decodedPayload) as unknown

    if (!payload || typeof payload !== 'object') return null

    return payload as ShareTokenPayload
  } catch {
    return null
  }
}

function isCommunityMemoShareToken(shareToken: string) {
  const payload = decodeShareTokenPayload(shareToken)

  return (
    payload?.purpose === COMMUNITY_MEMO_SHARE_PURPOSE ||
    payload?.artifactKind === COMMUNITY_MEMO_ARTIFACT_KIND
  )
}

function getFirstSearchParam(value: string | string[] | undefined) {
  if (Array.isArray(value)) return value[0]
  return value
}

export function getShareTokenFromSearchParams(searchParams: ShareSearchParams) {
  return (
    getFirstSearchParam(searchParams.share_token) ||
    getFirstSearchParam(searchParams.shareToken)
  )?.trim()
}

export function getShareRedirectPath(shareToken: string | null | undefined) {
  if (!shareToken) return '/'

  if (isCommunityMemoShareToken(shareToken)) {
    return '/community-canvas'
  }

  return '/'
}
