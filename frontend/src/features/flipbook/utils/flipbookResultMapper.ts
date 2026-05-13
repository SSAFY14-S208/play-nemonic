import type {
  FlipbookResultFrameResponse,
  FlipbookResultItemResponse,
} from '@/shared/types'
import type { FlipbookFrame } from '../types'

export function getResultFrames(result: FlipbookResultItemResponse | null): FlipbookFrame[] {
  if (!result) return []

  const sortedFrames = [...result.frames].sort(
    (firstFrame, secondFrame) => firstFrame.frameIndex - secondFrame.frameIndex,
  )
  const singleFrameFallbackImageUrl =
    sortedFrames.length === 1
      ? selectFirstImageUrl(result.firstImageUrl, result.thumbnailUrl)
      : ''

  return sortedFrames.map((frame) => ({
    id: `${result.flipbookIndex ?? 'result'}-${frame.frameIndex}`,
    index: frame.frameIndex,
    drawnByUserUuid: frame.drawnByUserUuid,
    drawnBy: frame.drawnByNickname,
    participantAvatar: '🙂',
    lines: [],
    imageUrl: selectFirstImageUrl(frame.imageUrl, singleFrameFallbackImageUrl),
  }))
}

export function getNormalizedResultItems(
  results: FlipbookResultItemResponse[],
  participantCount: number,
) {
  const resultItemsByFlipbookIndex = new Map<number, FlipbookResultItemResponse[]>()

  results.forEach((result, resultIndex) => {
    const normalizedFlipbookIndex = result.flipbookIndex ?? resultIndex
    const resultItems = resultItemsByFlipbookIndex.get(normalizedFlipbookIndex) ?? []
    resultItems.push(result)
    resultItemsByFlipbookIndex.set(normalizedFlipbookIndex, resultItems)
  })

  const duplicateFlipbookIndexes = Array.from(resultItemsByFlipbookIndex.entries())
    .filter(([, resultItems]) => resultItems.length > 1)
    .map(([flipbookIndex]) => flipbookIndex)

  const normalizedResultItems = Array.from(resultItemsByFlipbookIndex.entries())
    .sort(
      ([firstFlipbookIndex], [secondFlipbookIndex]) =>
        firstFlipbookIndex - secondFlipbookIndex,
    )
    .map(([flipbookIndex, resultItems]) => mergeResultItemGroup(flipbookIndex, resultItems))

  if (duplicateFlipbookIndexes.length > 0) {
    console.warn('플립북 결과에 중복 flipbookIndex가 있어 작품별로 병합했습니다.', {
      duplicateFlipbookIndexes,
      rawResultLength: results.length,
      normalizedResultLength: normalizedResultItems.length,
    })
  }

  if (normalizedResultItems.length > 0 && normalizedResultItems.length !== participantCount) {
    console.warn('플립북 병합 결과 수와 참여자 수가 일치하지 않습니다.', {
      normalizedResultLength: normalizedResultItems.length,
      participantCount,
    })
  }

  return normalizedResultItems.slice(0, Math.max(1, participantCount))
}

function hasImageUrl(imageUrl: string | null | undefined) {
  return Boolean(imageUrl?.trim())
}

function selectFirstImageUrl(...imageUrls: Array<string | null | undefined>) {
  return imageUrls.find(hasImageUrl) ?? ''
}

function shouldReplaceResultFrame(
  currentFrame: FlipbookResultFrameResponse,
  nextFrame: FlipbookResultFrameResponse,
) {
  return !hasImageUrl(currentFrame.imageUrl) && hasImageUrl(nextFrame.imageUrl)
}

function mergeResultItemGroup(
  flipbookIndex: number,
  resultItems: FlipbookResultItemResponse[],
): FlipbookResultItemResponse {
  const [baseResultItem] = resultItems
  const framesByFrameIndex = new Map<number, FlipbookResultFrameResponse>()

  resultItems.forEach((resultItem) => {
    resultItem.frames.forEach((frame) => {
      const currentFrame = framesByFrameIndex.get(frame.frameIndex)

      if (!currentFrame || shouldReplaceResultFrame(currentFrame, frame)) {
        framesByFrameIndex.set(frame.frameIndex, frame)
      }
    })
  })

  const frames = Array.from(framesByFrameIndex.values()).sort(
    (firstFrame, secondFrame) => firstFrame.frameIndex - secondFrame.frameIndex,
  )
  const frameImageUrls = frames.map((frame) => frame.imageUrl)
  const firstImageUrl = selectFirstImageUrl(
    ...resultItems.map((resultItem) => resultItem.firstImageUrl),
    ...frameImageUrls,
  )
  const thumbnailUrl = selectFirstImageUrl(
    ...resultItems.map((resultItem) => resultItem.thumbnailUrl),
    firstImageUrl,
    ...frameImageUrls,
  )

  return {
    ...baseResultItem,
    flipbookIndex,
    thumbnailUrl,
    firstImageUrl,
    gifUrl: selectFirstImageUrl(...resultItems.map((resultItem) => resultItem.gifUrl)),
    frames,
  }
}
