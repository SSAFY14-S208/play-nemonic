'use client'

import { useEffect, useState } from 'react'
import Image from 'next/image'

import type {
  FlipbookPrintFrame,
  FlipbookPrintParticipant,
} from '@/features/flipbook/components/result-print'
import { getDisplayImageUrl } from '@/shared/utils'

interface PrintedArtworkProps {
  frame: FlipbookPrintFrame
  participant: FlipbookPrintParticipant
}

export function PrintedArtwork({
  frame,
  participant,
}: PrintedArtworkProps) {
  const [loadFailed, setLoadFailed] = useState(false)
  const displayImageUrl = getDisplayImageUrl(frame.imageUrl)
  const isGifPlaybackFrame = frame.outputMode === 'gif-playback'

  useEffect(() => {
    let cancelled = false

    void (async () => {
      if (!cancelled) {
        setLoadFailed(false)
      }
    })()

    return () => {
      cancelled = true
    }
  }, [displayImageUrl])

  return (
    <div className="relative h-full w-full overflow-hidden bg-[#fffefa]">
      {displayImageUrl && !loadFailed ? (
        <Image
          src={displayImageUrl}
          alt={
            isGifPlaybackFrame
              ? `${participant.name} 완성 GIF`
              : `${participant.name} ${frame.frameNumber}번째 그림`
          }
          fill
          sizes="(max-width: 768px) 80vw, 748px"
          unoptimized
          className={isGifPlaybackFrame ? 'object-contain p-[2%]' : 'object-contain p-[4%]'}
          onError={() => {
            setLoadFailed(true)
            console.warn('플립북 결과 이미지 로딩에 실패했습니다.', displayImageUrl)
          }}
        />
      ) : (
        <BlankArtworkFallback frame={frame} participant={participant} />
      )}
    </div>
  )
}

function BlankArtworkFallback({
  frame,
  participant,
}: PrintedArtworkProps) {
  return (
    <div className="grid h-full w-full place-items-center bg-[#fffefa] p-8 text-center">
      <div>
        <span
          className="mx-auto grid size-14 place-items-center rounded-full text-[18px] font-bold text-white shadow-[0_8px_16px_rgb(40_40_40_/_12%)]"
          style={{
            backgroundColor: frame.accentColor ?? participant.accentColor ?? '#f58c97',
          }}
        >
          {frame.frameNumber}
        </span>
        <p className="h3-b mt-4 text-[#332222]">{frame.title}</p>
        <p className="caption-b mt-3 rounded-full bg-[#eef6e8] px-3 py-1 text-[#54704d]">
          이미지 준비 중
        </p>
      </div>
    </div>
  )
}
