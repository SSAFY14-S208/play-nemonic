import { useMemo, useState } from 'react'

import type { FlipbookResultItemResponse } from '@/shared/types'

import { useFlipbookBgm } from '@/features/flipbook/hooks'
import { toFlipbookPrintParticipants } from '@/features/flipbook/utils'
import { useFlipbookResultActions } from './useFlipbookResultActions'
import { useFlipbookResultAutoCycle } from './useFlipbookResultAutoCycle'

interface UseFlipbookResultViewModelParams {
  resultItems: FlipbookResultItemResponse[]
  resultOwnerNames: string[]
  activeResultIndex: number
  canCloseRoom: boolean
  isBusy: boolean
  onSelectResult: (resultIndex: number) => void
  onReturnToLobby: () => void
}

export function useFlipbookResultViewModel({
  resultItems,
  resultOwnerNames,
  activeResultIndex,
  canCloseRoom,
  isBusy,
  onSelectResult,
  onReturnToLobby,
}: UseFlipbookResultViewModelParams) {
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

  const resultActionButtons = useMemo(
    () => [
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
    ],
    [
      canCloseRoom,
      isBusy,
      resultActions.canPostCommunity,
      resultActions.canSaveToLocal,
      resultActions.canShareExternal,
      resultActions.postToCommunity,
      resultActions.returnToLobby,
      resultActions.saveToLocalGallery,
      resultActions.shareExternal,
    ],
  )

  return {
    actionMessage: resultActions.actionMessage,
    audioRef,
    isBgmMuted,
    isHowToPlayModalOpen,
    isResultLoading: printParticipants.length === 0,
    printParticipants,
    resultActionButtons,
    openHowToPlayModal: () => setIsHowToPlayModalOpen(true),
    setIsHowToPlayModalOpen,
    setRevealedResultIndex,
    toggleFlipbookBgmMuted,
  }
}
