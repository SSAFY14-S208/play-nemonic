import type { FlipbookResultItemResponse } from '@/shared/types'
import type { FlipbookPrintParticipant } from '../components'

const DEFAULT_PRINT_ACCENT_COLORS = ['#f58c97', '#7ec6ad', '#f3c66f', '#96a8ee', '#c99be8', '#ef9a72']

export function toFlipbookPrintParticipants({
  resultItems,
  resultOwnerNames,
}: {
  resultItems: FlipbookResultItemResponse[]
  resultOwnerNames: string[]
}): FlipbookPrintParticipant[] {
  return resultItems.map((resultItem, resultIndex) => {
    const flipbookIndex = resultItem.flipbookIndex ?? resultIndex
    const sortedFrames = [...resultItem.frames].sort(
      (firstFrame, secondFrame) => firstFrame.frameIndex - secondFrame.frameIndex,
    )
    const ownerName =
      resultOwnerNames[flipbookIndex] ??
      sortedFrames.find((frame) => frame.frameIndex === 0)?.drawnByNickname ??
      sortedFrames[0]?.drawnByNickname ??
      `작품 ${flipbookIndex + 1}`
    const accentColor = DEFAULT_PRINT_ACCENT_COLORS[flipbookIndex % DEFAULT_PRINT_ACCENT_COLORS.length]
    const printedFrames = sortedFrames.map((frame) => ({
      id: `${resultItem.artifactId || flipbookIndex}-${frame.frameIndex}`,
      title: ownerName,
      frameNumber: frame.frameIndex + 1,
      imageUrl: frame.imageUrl,
      accentColor,
      outputMode: 'nemonic-print' as const,
    }))
    const frames = resultItem.gifUrl
      ? [
          ...printedFrames,
          {
            id: `${resultItem.artifactId || flipbookIndex}-gif`,
            title: `${ownerName} GIF`,
            frameNumber: printedFrames.length + 1,
            imageUrl: resultItem.gifUrl,
            accentColor,
            outputMode: 'gif-playback' as const,
          },
        ]
      : printedFrames

    return {
      id: resultItem.artifactId || `flipbook-result-${flipbookIndex}`,
      name: ownerName,
      firstStartedWorkId: resultItem.artifactId || String(flipbookIndex),
      firstStartedWorkTitle: ownerName,
      accentColor,
      frames,
    }
  })
}
