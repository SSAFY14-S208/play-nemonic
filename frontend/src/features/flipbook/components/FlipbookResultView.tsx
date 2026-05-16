'use client'

import { useEffect, useMemo, useState } from 'react'
import Image from 'next/image'
import { Download, Loader2, RotateCcw, Share2, X } from 'lucide-react'

import type { FlipbookResultItemResponse } from '@/shared/types'
import { getDisplayImageUrl } from '@/shared/utils'

import { useFlipbookGifDownload } from '../hooks'
import { toFlipbookPrintParticipants } from '../utils'
import {
  FlipbookPrintResultStage,
  type FlipbookPrintFrame,
  type FlipbookPrintParticipant,
} from './result-print'

interface FlipbookResultViewProps {
  resultItems: FlipbookResultItemResponse[]
  resultOwnerNames: string[]
  activeResultIndex: number
  gifUrl: string | null
  resultCount: number
  canCloseRoom: boolean
  isBusy: boolean
  errorMessage: string | null
  onSelectResult: (resultIndex: number) => void
  onCloseRoom: () => void
  onCreateAnother: () => void
}

export default function FlipbookResultView({
  resultItems,
  resultOwnerNames,
  activeResultIndex,
  gifUrl,
  resultCount,
  canCloseRoom,
  isBusy,
  errorMessage,
  onSelectResult,
  onCloseRoom,
  onCreateAnother,
}: FlipbookResultViewProps) {
  const printParticipants = useMemo(
    () => toFlipbookPrintParticipants({ resultItems, resultOwnerNames }),
    [resultItems, resultOwnerNames],
  )
  const activeResult = resultItems[activeResultIndex] ?? resultItems[0] ?? null
  const displayGifUrl = getDisplayImageUrl(gifUrl) ?? gifUrl
  const gifDownload = useFlipbookGifDownload()
  const isResultLoading = printParticipants.length === 0

  return (
    <section className="relative min-h-screen overflow-hidden bg-[#fff7ed]">
      <FlipbookPrintResultStage
        participants={printParticipants}
        activeParticipantIndex={activeResultIndex}
        onSelectParticipant={onSelectResult}
        renderPaper={(frame, frameIndex, participant) => (
          <FlipbookPrintedArtwork
            frame={frame}
            frameIndex={frameIndex}
            participant={participant}
          />
        )}
      />

      {isResultLoading && (
        <div className="absolute left-1/2 top-1/2 z-[120] grid -translate-x-1/2 -translate-y-1/2 justify-items-center gap-3 rounded-[8px] border border-white/70 bg-white/82 px-8 py-6 text-center shadow-[0_18px_40px_rgb(120_80_80_/_16%)] backdrop-blur-md">
          <Loader2 className="size-7 animate-spin text-[#e56883]" aria-hidden />
          <div>
            <p className="body-b text-[#332222]">결과를 불러오는 중이에요</p>
            <p className="caption-m mt-1 text-[#c07182]">
              완성된 작품 {resultCount}개를 정리하고 있어요
            </p>
          </div>
        </div>
      )}

      <div className="absolute left-4 right-4 top-[calc(4.75rem+env(safe-area-inset-top))] z-[120] flex flex-wrap justify-center gap-2 sm:left-auto sm:right-6 sm:top-6 sm:justify-end">
        <button
          type="button"
          onClick={() => {
            void gifDownload.downloadGif({
              gifUrl: displayGifUrl,
              fileName: `flipbook-${activeResult?.artifactId ?? activeResultIndex + 1}`,
            })
          }}
          disabled={!displayGifUrl || gifDownload.isDownloadingGif}
          className="caption-b inline-flex min-h-10 items-center gap-2 rounded-full border border-white/80 bg-white/80 px-4 text-[#5d3b38] shadow-[0_8px_18px_rgb(120_80_80_/_12%)] backdrop-blur-md disabled:opacity-45"
        >
          {gifDownload.isDownloadingGif ? (
            <Loader2 className="size-4 animate-spin" aria-hidden />
          ) : (
            <Download className="size-4" aria-hidden />
          )}
          GIF 저장
        </button>
        <button
          type="button"
          onClick={() => {
            if (!displayGifUrl || !navigator.share) return
            void navigator.share({ title: '플립북', url: displayGifUrl })
          }}
          disabled={!displayGifUrl}
          className="caption-b inline-flex min-h-10 items-center gap-2 rounded-full border border-white/80 bg-white/80 px-4 text-[#5d3b38] shadow-[0_8px_18px_rgb(120_80_80_/_12%)] backdrop-blur-md disabled:opacity-45"
        >
          <Share2 className="size-4" aria-hidden />
          공유
        </button>
        <button
          type="button"
          onClick={onCreateAnother}
          className="caption-b inline-flex min-h-10 items-center gap-2 rounded-full border border-white/80 bg-white/80 px-4 text-[#5d3b38] shadow-[0_8px_18px_rgb(120_80_80_/_12%)] backdrop-blur-md"
        >
          <RotateCcw className="size-4" aria-hidden />
          새 플립북
        </button>
        {canCloseRoom && (
          <button
            type="button"
            onClick={onCloseRoom}
            disabled={isBusy}
            className="caption-b inline-flex min-h-10 items-center gap-2 rounded-full border border-[#ff8aa4]/70 bg-[#fff0f4]/88 px-4 text-[#b84e66] shadow-[0_8px_18px_rgb(226_128_154_/_16%)] backdrop-blur-md disabled:opacity-45"
          >
            <X className="size-4" aria-hidden />
            {isBusy ? '종료 중' : '방 종료'}
          </button>
        )}
      </div>

      {(errorMessage || gifDownload.gifDownloadError) && (
        <p className="caption-b absolute bottom-28 left-4 right-4 z-[120] rounded-full bg-white/86 px-5 py-3 text-center text-[#b84e66] shadow-[0_8px_18px_rgb(120_80_80_/_14%)] backdrop-blur-md sm:bottom-6 sm:left-1/2 sm:right-auto sm:-translate-x-1/2">
          {errorMessage ?? gifDownload.gifDownloadError}
        </p>
      )}

      {printParticipants.length > 0 && (
        <div className="absolute inset-x-3 bottom-3 z-[120] grid gap-2 rounded-[18px] border border-white/80 bg-white/86 p-3 shadow-[0_14px_30px_rgb(120_80_80_/_16%)] backdrop-blur-md md:hidden">
          <p className="caption-b text-[#b84e66]">작품 선택</p>
          <div className="flex gap-2 overflow-x-auto pb-1">
            {printParticipants.map((participant, participantIndex) => {
              const isActiveParticipant = participantIndex === activeResultIndex

              return (
                <button
                  key={participant.id}
                  type="button"
                  onClick={() => onSelectResult(participantIndex)}
                  className={`caption-b min-h-10 shrink-0 rounded-full border px-4 ${
                    isActiveParticipant
                      ? 'border-[#ff8aa4] bg-[#fff0f4] text-[#b84e66]'
                      : 'border-[#eadfd2] bg-white text-[#5d3b38]'
                  }`}
                >
                  {participant.name}
                </button>
              )
            })}
          </div>
        </div>
      )}
    </section>
  )
}

function FlipbookPrintedArtwork({
  frame,
  participant,
}: {
  frame: FlipbookPrintFrame
  frameIndex: number
  participant: FlipbookPrintParticipant
}) {
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
}: {
  frame: FlipbookPrintFrame
  participant: FlipbookPrintParticipant
}) {
  return (
    <div className="grid h-full w-full place-items-center bg-[#fffefa] p-8 text-center">
      <div>
        <span
          className="mx-auto grid size-14 place-items-center rounded-full text-[18px] font-bold text-white shadow-[0_8px_16px_rgb(40_40_40_/_12%)]"
          style={{ backgroundColor: frame.accentColor ?? participant.accentColor ?? '#f58c97' }}
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
