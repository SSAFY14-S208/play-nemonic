'use client'

import { useMemo, useState } from 'react'

import { HowToPlayModal } from '@/shared/components'
import type { FlipbookResultItemResponse } from '@/shared/types'

import { FlipbookPrintResultStage } from '@/features/flipbook/components/result-print'
import { FLIPBOOK_HOW_TO_PLAY_PANELS, FLIPBOOK_SOUND_PATHS } from '@/features/flipbook/constants'
import { useFlipbookBgm } from '@/features/flipbook/hooks'
import { toFlipbookPrintParticipants } from '@/features/flipbook/utils'
import { useFlipbookResultActions, useFlipbookResultAutoCycle } from './hooks'
import {
  MobileResultSelector,
  PrintedArtwork,
  ResultActionButtons,
  ResultActionMessage,
  ResultLoadingOverlay,
  ResultTopControls,
  type ResultActionButton,
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
  const resultActionButtons: ResultActionButton[] = [
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

      <ResultTopControls
        isBgmMuted={isBgmMuted}
        onOpenHowToPlay={() => setIsHowToPlayModalOpen(true)}
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

      <ResultActionMessage message={errorMessage ?? resultActions.actionMessage} />

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
