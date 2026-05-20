import type { CommunityMemoItemResponse } from '@/shared/types'

type CommunityMemoImageSource = Pick<
  CommunityMemoItemResponse,
  'memoImageUrl' | 'memoOriginalImageUrl' | 'memoThumbnailImageUrl'
>

export function isCommunityAnimatedImageUrl(imageUrl: string | null | undefined) {
  if (!imageUrl) return false
  return /^data:image\/gif[;,]/i.test(imageUrl) || /\.gif(?:[?#].*)?$/i.test(imageUrl)
}

export function getStaticCommunityImageUrl(
  ...imageUrls: Array<string | null | undefined>
) {
  return imageUrls.find((imageUrl) => imageUrl && !isCommunityAnimatedImageUrl(imageUrl)) ?? null
}

export function getStaticCommunityMemoImageUrl(memo: CommunityMemoImageSource) {
  return getStaticCommunityImageUrl(
    memo.memoThumbnailImageUrl,
    memo.memoImageUrl,
    memo.memoOriginalImageUrl,
  )
}
