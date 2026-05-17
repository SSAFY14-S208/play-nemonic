'use client'

import { useRouter } from 'next/navigation'
import { useState, useTransition } from 'react'

import { ApiError, postInfiniteCanvas, postInvite } from '@/shared/apis'
import { DEFAULT_USER_NICKNAME } from '@/shared/constants'
import { useUserStore } from '@/shared/stores'

import { INFINITE_CANVAS_COLOR_OPTIONS } from '../constants'
import {
  buildInfiniteCanvasRoomPath,
  normalizeInfiniteCanvasInviteCode,
  resolveInfiniteCanvasInviteRoomPath,
  saveInfiniteCanvasCreatedRoomSnapshot,
} from '../utils'

const DEFAULT_SELECTED_COLOR = INFINITE_CANVAS_COLOR_OPTIONS[4].value

function hasConfiguredNickname(nickname: string | null) {
  return Boolean(nickname?.trim()) && nickname !== DEFAULT_USER_NICKNAME
}

interface UseInfiniteCanvasEntryReturn {
  isUserReady: boolean
  needsNicknameSetup: boolean
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

  const [selectedColor, setSelectedColor] = useState<string>(DEFAULT_SELECTED_COLOR)
  const [inviteCodeDraft, setInviteCodeDraftValue] = useState('')
  const [isInviteModalOpen, setIsInviteModalOpen] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [isPending, startTransition] = useTransition()

  const isUserReady = userUuid !== null
  const needsNicknameSetup = isUserReady && !hasConfiguredNickname(nickname)

  const clearError = () => setErrorMessage(null)

  const setInviteCodeDraft = (nextInviteCode: string) => {
    setInviteCodeDraftValue(nextInviteCode)
    setErrorMessage(null)
  }

  const openInviteModal = () => {
    if (isPending) return
    if (!isUserReady) {
      setErrorMessage('사용자 정보를 준비하는 중이에요. 잠시 후 다시 눌러주세요')
      return
    }
    setErrorMessage(null)
    setIsInviteModalOpen(true)
  }

  const closeInviteModal = () => {
    setErrorMessage(null)
    setIsInviteModalOpen(false)
  }

  const createCanvas = () => {
    if (isPending) return
    if (!isUserReady) {
      setErrorMessage('사용자 정보를 준비하는 중이에요. 잠시 후 다시 눌러주세요')
      return
    }
    if (!hasConfiguredNickname(useUserStore.getState().nickname)) return

    setErrorMessage(null)
    startTransition(async () => {
      try {
        const canvas = await postInfiniteCanvas({
          color: selectedColor,
        })
        saveInfiniteCanvasCreatedRoomSnapshot(canvas)
        router.push(buildInfiniteCanvasRoomPath(canvas.roomCode))
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
    if (isPending) return
    if (!isUserReady) {
      setErrorMessage('사용자 정보를 준비하는 중이에요. 잠시 후 다시 눌러주세요')
      return
    }
    if (!hasConfiguredNickname(useUserStore.getState().nickname)) return

    const inviteCode = normalizeInfiniteCanvasInviteCode(inviteCodeDraft)
    if (!inviteCode) {
      setErrorMessage('초대코드를 입력해주세요')
      return
    }

    setErrorMessage(null)
    startTransition(async () => {
      try {
        const invite = await postInvite(inviteCode)
        const roomPath = resolveInfiniteCanvasInviteRoomPath(invite)
        if (!roomPath) {
          setErrorMessage('무한 캔버스 초대코드가 아니에요')
          return
        }

        router.push(roomPath)
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
    needsNicknameSetup,
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
