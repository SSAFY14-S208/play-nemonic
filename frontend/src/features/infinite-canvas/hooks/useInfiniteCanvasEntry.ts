'use client'

import { useRouter } from 'next/navigation'
import { useState, useTransition } from 'react'

import { ApiError, postInfiniteCanvas, postInvite } from '@/shared/apis'
import { useUserStore } from '@/shared/stores'

import { INFINITE_CANVAS_COLOR_OPTIONS } from '../constants'
import {
  buildInfiniteCanvasRoomPath,
  isInfiniteCanvasBoothType,
  normalizeInfiniteCanvasInviteCode,
} from '../utils'

const DEFAULT_SELECTED_COLOR = INFINITE_CANVAS_COLOR_OPTIONS[4].value

interface UseInfiniteCanvasEntryReturn {
  isUserReady: boolean
  isPending: boolean
  isInviteModalOpen: boolean
  selectedColor: string
  inviteCodeDraft: string
  errorMessage: string | null
  setSelectedColor: (color: string) => void
  setInviteCodeDraft: (inviteCode: string) => void
  createCanvas: () => void
  openInviteModal: () => void
  closeInviteModal: () => void
  joinByInviteCode: () => void
  clearError: () => void
}

export function useInfiniteCanvasEntry(): UseInfiniteCanvasEntryReturn {
  const router = useRouter()
  const userUuid = useUserStore((state) => state.userUuid)
  const nickname = useUserStore((state) => state.nickname)

  const [selectedColor, setSelectedColor] = useState(DEFAULT_SELECTED_COLOR)
  const [inviteCodeDraft, setInviteCodeDraft] = useState('')
  const [isInviteModalOpen, setIsInviteModalOpen] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [isPending, startTransition] = useTransition()

  const isUserReady = userUuid !== null

  const clearError = () => setErrorMessage(null)

  const openInviteModal = () => {
    if (!isUserReady || isPending) return
    setErrorMessage(null)
    setIsInviteModalOpen(true)
  }

  const closeInviteModal = () => {
    setErrorMessage(null)
    setIsInviteModalOpen(false)
  }

  const createCanvas = () => {
    if (!isUserReady || isPending) return

    setErrorMessage(null)
    startTransition(async () => {
      try {
        const canvasState = await postInfiniteCanvas({
          nickname: nickname?.trim() || null,
          color: selectedColor,
        })
        router.push(buildInfiniteCanvasRoomPath(canvasState.canvasId))
      } catch (caughtError) {
        const message =
          caughtError instanceof ApiError
            ? caughtError.message
            : '무한 캔버스 방 생성에 실패했어요'
        setErrorMessage(message)
      }
    })
  }

  const joinByInviteCode = () => {
    if (!isUserReady || isPending) return

    const inviteCode = normalizeInfiniteCanvasInviteCode(inviteCodeDraft)
    if (!inviteCode) {
      setErrorMessage('초대코드를 입력해주세요')
      return
    }

    setErrorMessage(null)
    startTransition(async () => {
      try {
        const invite = await postInvite(inviteCode)
        if (!isInfiniteCanvasBoothType(invite.boothType)) {
          setErrorMessage('무한 캔버스 초대코드가 아니에요')
          return
        }

        router.push(buildInfiniteCanvasRoomPath(invite.roomId))
      } catch (caughtError) {
        const message =
          caughtError instanceof ApiError
            ? caughtError.message
            : '무한 캔버스 방 입장에 실패했어요'
        setErrorMessage(message)
      }
    })
  }

  return {
    isUserReady,
    isPending,
    isInviteModalOpen,
    selectedColor,
    inviteCodeDraft,
    errorMessage,
    setSelectedColor,
    setInviteCodeDraft,
    createCanvas,
    openInviteModal,
    closeInviteModal,
    joinByInviteCode,
    clearError,
  }
}
