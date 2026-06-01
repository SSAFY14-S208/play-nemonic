'use client'

import { HowToPlayModal } from '@/shared/components'
import type { FlipbookResultItemResponse } from '@/shared/types'

import { FlipbookPrintResultStage } from '@/features/flipbook/components/result-print'
import { FLIPBOOK_HOW_TO_PLAY_PANELS, FLIPBOOK_SOUND_PATHS } from '@/features/flipbook/constants'
import { useFlipbookResultViewModel } from './hooks'
import {
  MobileResultSelector,
  PrintedArtwork,
  ResultActionButtons,
  ResultActionMessage,
  ResultLoadingOverlay,
  ResultTopControls,
} from './sections'

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
  const {
    actionMessage,
    audioRef,
    isBgmMuted,
    isHowToPlayModalOpen,
    isResultLoading,
    openHowToPlayModal,
    printParticipants,
    resultActionButtons,
    setIsHowToPlayModalOpen,
    setRevealedResultIndex,
    toggleFlipbookBgmMuted,
  } = useFlipbookResultViewModel({
    resultItems,
    resultOwnerNames,
    activeResultIndex,
    canCloseRoom,
    isBusy,
    onSelectResult,
    onReturnToLobby,
  })

  return (
    <section className="relative min-h-[100svh] overflow-hidden bg-[#fff7ed]">
      <audio ref={audioRef} src={FLIPBOOK_SOUND_PATHS.entranceBgm} preload="auto" loop aria-hidden />

      <ResultTopControls
        isBgmMuted={isBgmMuted}
        onOpenHowToPlay={openHowToPlayModal}
        onToggleBgmMuted={toggleFlipbookBgmMuted}
      />

      <FlipbookPrintResultStage
        participants={printParticipants}
        activeParticipantIndex={activeResultIndex}
        onSelectParticipant={onSelectResult}
        onParticipantRevealComplete={setRevealedResultIndex}
        renderPaper={(frame, _frameIndex, participant) => (
          <PrintedArtwork
            frame={frame}
            participant={participant}
          />
        )}
      />

      {isResultLoading && <ResultLoadingOverlay resultCount={resultCount} />}

      <ResultActionButtons actionButtons={resultActionButtons} />

      <ResultActionMessage message={errorMessage ?? actionMessage} />

      <MobileResultSelector
        participants={printParticipants}
        activeParticipantIndex={activeResultIndex}
        onSelectParticipant={onSelectResult}
      />
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
