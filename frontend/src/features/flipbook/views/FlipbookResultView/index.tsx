'use client'

import { useEffect, useMemo, useState } from 'react'
import Image from 'next/image'
import { Loader2 } from 'lucide-react'

import { HowToPlayModal, PhoneLauncherButton } from '@/shared/components'
import type { FlipbookResultItemResponse } from '@/shared/types'
import { getDisplayImageUrl } from '@/shared/utils'

import { FlipbookPrintResultStage, type FlipbookPrintFrame, type FlipbookPrintParticipant } from '@/features/flipbook/components/result-print'
import { FLIPBOOK_HOW_TO_PLAY_PANELS, FLIPBOOK_SOUND_PATHS } from '@/features/flipbook/constants'
import { useFlipbookBgm } from '@/features/flipbook/hooks'
import { toFlipbookPrintParticipants } from '@/features/flipbook/utils'
import { useFlipbookResultActions, useFlipbookResultAutoCycle } from './hooks'

const FLIPBOOK_RESULT_CONTROL_IMAGES = {
  howToPlay: '/images/flipbook-entrance-scene/how-to-play-button.png',
  soundOn: '/images/flipbook-entrance-scene/sound-on-button.png',
  soundMuted: '/images/flipbook-entrance-scene/sound-muted-button.png',
} as const

const RESULT_ACTION_BUTTONS_IMAGE_SRC = '/images/flipbook-result/result-action-buttons.png'
const RESULT_ACTION_BUTTONS_IMAGE_WIDTH = 733
const RESULT_ACTION_BUTTONS_IMAGE_HEIGHT = 70
const RESULT_ACTION_BUTTONS_ASPECT_RATIO = `${RESULT_ACTION_BUTTONS_IMAGE_WIDTH} / ${RESULT_ACTION_BUTTONS_IMAGE_HEIGHT}`

interface FlipbookResultViewProps {
  resultItems: FlipbookResultItemResponse[]
  resultOwnerNames: string[]
  activeResultIndex: number
  resultCount: number
  canCloseRoom: boolean
  isBusy: boolean
  errorMessage: string | null
  onSelectResult: (resultIndex: number) => void
  onReturnToLobby: () => void
}

export default function FlipbookResultView({
  resultItems,
  resultOwnerNames,
  activeResultIndex,
  resultCount,
  canCloseRoom,
  isBusy,
  errorMessage,
  onSelectResult,
  onReturnToLobby,
}: FlipbookResultViewProps) {
  const [isHowToPlayModalOpen, setIsHowToPlayModalOpen] = useState(false)
  const [revealedResultIndex, setRevealedResultIndex] = useState<number | null>(null)
  const { audioRef, isBgmMuted, toggleFlipbookBgmMuted } = useFlipbookBgm({
    shouldStart: true,
  })
  const printParticipants = useMemo(
    () => toFlipbookPrintParticipants({ resultItems, resultOwnerNames }),
    [resultItems, resultOwnerNames],
  )
  const activeResult = resultItems[activeResultIndex] ?? resultItems[0] ?? null
  const resultActions = useFlipbookResultActions({
    activeResult,
    activeResultIndex,
    resultOwnerNames,
    onReturnToLobby,
  })
  useFlipbookResultAutoCycle({
    enabled: true,
    resultCount: resultItems.length,
    activeResultIndex,
    revealedResultIndex,
    onSelectResult,
  })
  const isResultLoading = printParticipants.length === 0
  const resultActionButtons = [
    {
      id: 'local-gallery',
      label: '저장하기',
      left: '1.77%',
      width: '22.78%',
      disabled: !resultActions.canSaveToLocal,
      onClick: () => {
        void resultActions.saveToLocalGallery()
      },
    },
    {
      id: 'community-post',
      label: '커뮤니티 게시',
      left: '25.92%',
      width: '24.15%',
      disabled: !resultActions.canPostCommunity,
      onClick: resultActions.postToCommunity,
    },
    {
      id: 'external-share',
      label: '외부 공유',
      left: '51.43%',
      width: '21.69%',
      disabled: !resultActions.canShareExternal,
      onClick: () => {
        void resultActions.shareExternal()
      },
    },
    {
      id: 'return-to-lobby',
      label: '로비로 돌아가기',
      left: '74.62%',
      width: '23.47%',
      disabled: canCloseRoom && isBusy,
      onClick: resultActions.returnToLobby,
    },
  ]

  return (
    <section className="relative min-h-[100svh] overflow-hidden bg-[#fff7ed]">
      <audio ref={audioRef} src={FLIPBOOK_SOUND_PATHS.entranceBgm} preload="auto" loop aria-hidden />

      <div className="absolute left-4 top-[calc(env(safe-area-inset-top)+1rem)] z-[120] flex items-center gap-2 sm:left-6 sm:top-6">
        <FlipbookResultIconButton
          imageSrc={FLIPBOOK_RESULT_CONTROL_IMAGES.howToPlay}
          label="게임 설명"
          onClick={() => setIsHowToPlayModalOpen(true)}
        />
        <FlipbookResultIconButton
          imageSrc={isBgmMuted ? FLIPBOOK_RESULT_CONTROL_IMAGES.soundMuted : FLIPBOOK_RESULT_CONTROL_IMAGES.soundOn}
          label={isBgmMuted ? '배경음악 켜기' : '배경음악 음소거'}
          pressed={isBgmMuted}
          onClick={toggleFlipbookBgmMuted}
        />
        <PhoneLauncherButton className="size-14 sm:size-[clamp(54px,4.6vw,70px)]" />
      </div>

      <FlipbookPrintResultStage
        participants={printParticipants}
        activeParticipantIndex={activeResultIndex}
        onSelectParticipant={onSelectResult}
        onParticipantRevealComplete={setRevealedResultIndex}
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

      <div className="absolute left-1/2 top-[calc(4.75rem+env(safe-area-inset-top))] z-[120] w-[min(733px,calc(100vw-2rem))] -translate-x-1/2 sm:left-auto sm:right-6 sm:top-6 sm:translate-x-0">
        <div
          className="relative w-full"
          style={{ aspectRatio: RESULT_ACTION_BUTTONS_ASPECT_RATIO }}
        >
          <Image
            src={RESULT_ACTION_BUTTONS_IMAGE_SRC}
            alt=""
            fill
            priority
            draggable={false}
            unoptimized
            sizes={`(max-width: 640px) calc(100vw - 2rem), ${RESULT_ACTION_BUTTONS_IMAGE_WIDTH}px`}
            className="select-none object-contain"
            aria-hidden
          />
          {resultActionButtons.map((actionButton) => (
            <button
              key={actionButton.id}
              type="button"
              aria-label={actionButton.label}
              title={actionButton.label}
              onClick={actionButton.onClick}
              disabled={actionButton.disabled}
              className="absolute top-0 h-full rounded-full text-transparent transition hover:bg-white/10 active:bg-black/5 disabled:cursor-not-allowed disabled:bg-white/45 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white focus-visible:ring-offset-2 focus-visible:ring-offset-[#ff3f7e]"
              style={{ left: actionButton.left, width: actionButton.width }}
            >
              <span className="sr-only">{actionButton.label}</span>
            </button>
          ))}
        </div>
      </div>

      {(errorMessage || resultActions.actionMessage) && (
        <p className="caption-b absolute bottom-[calc(7.75rem+env(safe-area-inset-bottom))] left-4 right-4 z-[120] rounded-full bg-white/86 px-5 py-3 text-center text-[#b84e66] shadow-[0_8px_18px_rgb(120_80_80_/_14%)] backdrop-blur-md sm:bottom-6 sm:left-1/2 sm:right-auto sm:-translate-x-1/2">
          {errorMessage ?? resultActions.actionMessage}
        </p>
      )}

      {printParticipants.length > 0 && (
        <div className="absolute inset-x-3 bottom-[calc(0.75rem+env(safe-area-inset-bottom))] z-[120] grid max-h-[28svh] gap-2 rounded-[18px] border border-white/80 bg-white/86 px-3 pb-[calc(0.25rem+env(safe-area-inset-bottom))] pt-3 shadow-[0_14px_30px_rgb(120_80_80_/_16%)] backdrop-blur-md md:hidden">
          <p className="caption-b text-[#b84e66]">작품 선택</p>
          <div className="flex snap-x snap-mandatory gap-2 overflow-x-auto pb-1">
            {printParticipants.map((participant, participantIndex) => {
              const isActiveParticipant = participantIndex === activeResultIndex

              return (
                <button
                  key={participant.id}
                  type="button"
                  onClick={() => onSelectResult(participantIndex)}
                  className={`caption-b min-h-11 max-w-48 shrink-0 snap-start truncate rounded-full border px-4 ${
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
      <HowToPlayModal
        open={isHowToPlayModalOpen}
        onOpenChange={setIsHowToPlayModalOpen}
        panels={FLIPBOOK_HOW_TO_PLAY_PANELS}
        title="플립북 게임 설명"
        subtitle="이전 프레임을 힌트로 보며 조금씩 바꿔 그려 움직이는 플립북을 만들어요."
        accentColor="#ff7182"
      />
    </section>
  )
}

function FlipbookResultIconButton({
  imageSrc,
  label,
  pressed,
  onClick,
}: {
  imageSrc: string
  label: string
  pressed?: boolean
  onClick: () => void
}) {
  return (
    <button
      type="button"
      aria-label={label}
      aria-pressed={pressed}
      title={label}
      className="relative grid size-14 place-items-center transition duration-150 hover:-translate-y-0.5 active:translate-y-px active:scale-95 focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-flipbook-primary sm:size-[clamp(54px,4.6vw,70px)]"
      onClick={onClick}
    >
      <Image
        src={imageSrc}
        alt=""
        width={67}
        height={70}
        sizes="70px"
        className="h-full w-auto object-contain"
      />
      <span className="sr-only">{label}</span>
    </button>
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
