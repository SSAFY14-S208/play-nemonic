'use client'

import { useEffect, useState } from 'react'
import Image from 'next/image'
import { Download, Share2 } from 'lucide-react'
import type { FlipbookResultItemResponse } from '@/shared/types'
import { cn } from '@/shared/libs'
import { getDisplayImageUrl } from '@/shared/utils'
import { useFlipbookGifDownload } from '../hooks'

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
  const activeResult = resultItems[activeResultIndex] ?? null
  const displayGifUrl = getDisplayImageUrl(gifUrl) ?? gifUrl
  const gifDownload = useFlipbookGifDownload()
  const activeOwnerName =
    activeResult !== null
      ? (resultOwnerNames[activeResult.flipbookIndex ?? activeResultIndex] ??
        `작품 ${(activeResult.flipbookIndex ?? activeResultIndex) + 1}`)
      : '플립북'

  return (
    <section className="min-h-screen bg-flipbook-background px-6 py-10 text-flipbook-ink lg:px-12 lg:py-14">
      <div className="mx-auto w-full max-w-[1312px]">
        <div className="flex min-h-9 items-center justify-between gap-4">
          <div className="flex flex-wrap items-center gap-2">
            <span className="caption-b text-flipbook-deep">2026.04.28 ·</span>
            <span className="caption-b rounded-full bg-flipbook-light px-3 py-1 text-flipbook-ink">
              내 플립북 앨범
            </span>
          </div>
          <span className="caption-b text-flipbook-deep">
            {resultItems.length === 0 ? 0 : activeResultIndex + 1} / {resultCount}
          </span>
        </div>

        <div className="mt-5 grid gap-8 lg:grid-cols-[minmax(0,880px)_400px] lg:items-start">
          <section className="min-h-[716px] overflow-hidden rounded-[18px] bg-flipbook-paper px-5 py-6 shadow-[0_14px_28px_var(--color-flipbook-shadow)] md:px-7">
            <header className="mb-4 flex min-h-[60px] items-end justify-between gap-4">
              <div>
                <p className="caption-b text-flipbook-deep">완성된 GIF</p>
                <h1 className="h2-b mt-1 flex flex-wrap items-center gap-2 text-flipbook-ink">
                  <span className="rounded-full bg-flipbook-light px-3 py-0.5">
                    {activeOwnerName} 님의 작품
                  </span>
                  {activeResult && (
                    <span className="rounded-full bg-flipbook-result-soft px-3 py-0.5">
                      {activeResult.frames.length}프레임
                    </span>
                  )}
                </h1>
              </div>
            </header>

            <div className="relative h-[560px] overflow-hidden rounded-[14px] border-[1.5px] border-flipbook-light bg-white">
              {displayGifUrl ? (
                <ResultGif imageUrl={displayGifUrl} />
              ) : (
                <div className="body-l-b grid h-full place-items-center text-flipbook-deep">
                  결과 GIF 생성 중
                </div>
              )}
            </div>
          </section>

          <aside className="flex min-h-[716px] flex-col gap-4">
            <section className="rounded-[18px] border border-flipbook-light bg-flipbook-paper px-5 py-4">
              <p className="caption-b text-flipbook-deep">완성된 작품</p>
              <p className="caption-b mt-2 text-flipbook-muted">
                총 {resultCount}개의 플립북 중 {activeResultIndex + 1}번째 결과
              </p>
              <div className="mt-4 grid gap-2">
                {resultItems.map((resultItem, resultIndex) => {
                  const sortedFrames = [...resultItem.frames].sort(
                    (firstFrame, secondFrame) => firstFrame.frameIndex - secondFrame.frameIndex,
                  )
                  const normalizedFlipbookIndex = resultItem.flipbookIndex ?? resultIndex
                  const firstDrawer =
                    resultOwnerNames[normalizedFlipbookIndex] ??
                    sortedFrames.find((frame) => frame.frameIndex === 0)?.drawnByNickname ??
                    sortedFrames[0]?.drawnByNickname ??
                    '알 수 없음'
                  const isActiveResult = resultIndex === activeResultIndex

                  return (
                    <button
                      key={resultItem.artifactId}
                      type="button"
                      onClick={() => onSelectResult(resultIndex)}
                      className={cn(
                        'flex min-h-[68px] items-center gap-3 rounded-[14px] bg-flipbook-result-soft px-3.5 text-left',
                        isActiveResult &&
                          'border-[1.5px] border-flipbook-deep bg-flipbook-paper shadow-[0_4px_5px_var(--color-flipbook-shadow)]',
                      )}
                    >
                      <span
                        className="relative grid size-11 shrink-0 place-items-center overflow-hidden rounded-[10px] border border-flipbook-light bg-white"
                        aria-hidden
                      >
                        <ResultThumbnail
                          imageUrl={
                            resultItem.thumbnailUrl ||
                            resultItem.firstImageUrl ||
                            sortedFrames[0]?.imageUrl ||
                            null
                          }
                        />
                      </span>
                      <span className="min-w-0">
                        <span className="body-b block text-flipbook-ink">
                          작품 {(resultItem.flipbookIndex ?? resultIndex) + 1}
                        </span>
                        <span className="caption-m block truncate text-flipbook-deep">
                          시작: {firstDrawer}
                        </span>
                      </span>
                      <span className="caption-b ml-auto text-flipbook-deep">
                        {resultItem.frames.length}장
                      </span>
                    </button>
                  )
                })}
                {resultItems.length === 0 && (
                  <p className="caption-m rounded-[14px] bg-flipbook-result-soft px-3.5 py-4 text-flipbook-muted">
                    결과를 불러오는 중이에요
                  </p>
                )}
              </div>
            </section>

            <div className="mt-auto grid min-h-[60px] gap-3 sm:grid-cols-2">
              <button
                type="button"
                onClick={() => {
                  void gifDownload.downloadGif({
                    gifUrl: displayGifUrl,
                    fileName: `flipbook-${activeResult?.artifactId ?? activeResultIndex + 1}`,
                  })
                }}
                disabled={!displayGifUrl || gifDownload.isDownloadingGif}
                className="body-b inline-flex items-center justify-center gap-2 rounded-[14px] border-[1.5px] border-flipbook-light bg-flipbook-paper px-5 text-flipbook-ink disabled:opacity-45"
              >
                <Download className="size-4" aria-hidden />
                {gifDownload.isDownloadingGif ? '저장 중' : 'GIF 저장'}
              </button>
              <button
                type="button"
                onClick={() => {
                  if (!displayGifUrl || !navigator.share) return
                  void navigator.share({ title: '플립북', url: displayGifUrl })
                }}
                disabled={!displayGifUrl}
                className="body-b inline-flex items-center justify-center gap-2 rounded-[14px] border-[1.5px] border-flipbook-primary bg-flipbook-primary px-5 text-flipbook-ink shadow-[0_4px_10px_var(--color-flipbook-shadow)] disabled:opacity-45"
              >
                <Share2 className="size-4" aria-hidden />
                공유하기
              </button>
            </div>
            {gifDownload.gifDownloadError && (
              <p className="caption-b rounded-[12px] bg-flipbook-result-soft px-4 py-3 text-center text-flipbook-deep">
                {gifDownload.gifDownloadError}
              </p>
            )}
            {errorMessage && (
              <p className="caption-b rounded-[12px] bg-flipbook-result-soft px-4 py-3 text-center text-flipbook-deep">
                {errorMessage}
              </p>
            )}

            {canCloseRoom && (
              <button
                type="button"
                onClick={onCloseRoom}
                disabled={isBusy}
                className="body-b min-h-12 rounded-[14px] border-[1.5px] border-flipbook-deep bg-flipbook-paper px-5 text-flipbook-ink disabled:opacity-45"
              >
                {isBusy ? '종료 중' : '방 종료'}
              </button>
            )}

            <button
              type="button"
              onClick={onCreateAnother}
              className="caption-b self-center text-flipbook-muted"
            >
              새 플립북 만들기
            </button>
          </aside>
        </div>
      </div>
    </section>
  )
}

function ResultGif({ imageUrl }: { imageUrl: string }) {
  const [loadFailed, setLoadFailed] = useState(false)

  useEffect(() => {
    let cancelled = false

    ;(async () => {
      if (!cancelled) {
        setLoadFailed(false)
      }
    })()

    return () => {
      cancelled = true
    }
  }, [imageUrl])

  return (
    <div className="relative h-full w-full bg-white">
      {!loadFailed && (
        <Image
          src={imageUrl}
          alt="완성된 플립북 GIF"
          fill
          sizes="880px"
          unoptimized
          className="object-contain"
          onError={() => {
            setLoadFailed(true)
            console.warn('플립북 GIF 로딩에 실패했습니다.', imageUrl)
          }}
        />
      )}
      {loadFailed && (
        <div className="body-b grid h-full w-full place-items-center text-flipbook-deep">
          GIF 로딩 실패
        </div>
      )}
    </div>
  )
}

function ResultThumbnail({ imageUrl }: { imageUrl: string | null }) {
  const [loadFailed, setLoadFailed] = useState(false)
  const displayImageUrl = getDisplayImageUrl(imageUrl)

  useEffect(() => {
    let cancelled = false

    ;(async () => {
      if (!cancelled) {
        setLoadFailed(false)
      }
    })()

    return () => {
      cancelled = true
    }
  }, [displayImageUrl])

  if (!displayImageUrl || loadFailed) {
    return <span className="caption-b text-flipbook-deep">?</span>
  }

  return (
    <Image
      src={displayImageUrl}
      alt=""
      fill
      sizes="44px"
      unoptimized
      className="object-cover"
      onError={() => {
        setLoadFailed(true)
        console.warn('플립북 썸네일 이미지 로딩에 실패했습니다.', displayImageUrl)
      }}
    />
  )
}
