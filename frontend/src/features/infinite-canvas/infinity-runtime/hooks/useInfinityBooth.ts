'use client'

import { useRouter } from 'next/navigation'
import { useState, useTransition } from 'react'
import {
  ApiError,
  patchInfiniteCanvasParticipantMe,
  postInfiniteCanvasCanvas,
  postInvite,
} from '@/shared/apis'
import { DEFAULT_USER_NICKNAME } from '@/shared/constants'
import { useUserStore } from '@/shared/stores'
import { INFINITY_COLORS } from '../constants'

interface UseInfinityBoothReturn {
  isUserReady: boolean
  needsNicknameSetup: boolean
  isPending: boolean
  error: string | null
  selectedColor: string
  setSelectedColor: (color: string) => void
  createCanvas: () => void
  joinCanvas: (inviteCode: string) => void
  clearError: () => void
}

export function useInfinityBooth(): UseInfinityBoothReturn {
  const router = useRouter()
  const userUuid = useUserStore((state) => state.userUuid)
  const nickname = useUserStore((state) => state.nickname)
  const [isPending, startTransition] = useTransition()
  const [error, setError] = useState<string | null>(null)
  const [selectedColor, setSelectedColor] = useState<string>(INFINITY_COLORS[4])

  const isUserReady = userUuid !== null
  const needsNicknameSetup =
    !nickname || nickname.trim() === '' || nickname === DEFAULT_USER_NICKNAME

  const getConfiguredNickname = () => {
    const currentNickname = useUserStore.getState().nickname
    if (!currentNickname || currentNickname.trim() === '' || currentNickname === DEFAULT_USER_NICKNAME) {
      return null
    }
    return currentNickname.trim()
  }

  const navigateToCanvas = (canvasId: string) => {
    router.push(`/infinite-canvas/${canvasId}`)
  }

  const createCanvas = () => {
    const configuredNickname = getConfiguredNickname()
    if (!isUserReady || isPending || !configuredNickname) return

    setError(null)
    startTransition(async () => {
      try {
        const canvas = await postInfiniteCanvasCanvas({
          nickname: configuredNickname,
          color: selectedColor,
        })
        navigateToCanvas(canvas.canvasId)
      } catch (caughtError) {
        const message =
          caughtError instanceof ApiError
            ? caughtError.message
            : '캔버스 생성에 실패했어요'
        setError(message)
      }
    })
  }

  const joinCanvas = (rawInviteCode: string) => {
    const configuredNickname = getConfiguredNickname()
    if (!isUserReady || isPending || !configuredNickname) return
    const inviteCode = rawInviteCode.trim().toUpperCase()

    if (!inviteCode) {
      setError('초대코드를 입력해주세요')
      return
    }

    setError(null)
    startTransition(async () => {
      try {
        const invite = await postInvite(inviteCode)
        if (invite.boothType !== 'infinite_canvas' && invite.boothType !== 'infinite-canvas') {
          setError('무한 캔버스 초대코드가 아니에요')
          return
        }
        await patchInfiniteCanvasParticipantMe(invite.roomId, {
          nickname: configuredNickname,
          color: selectedColor,
        })
        navigateToCanvas(invite.roomId)
      } catch (caughtError) {
        const message =
          caughtError instanceof ApiError
            ? caughtError.message
            : '캔버스 입장에 실패했어요'
        setError(message)
      }
    })
  }

  return {
    isUserReady,
    needsNicknameSetup,
    isPending,
    error,
    selectedColor,
    setSelectedColor,
    createCanvas,
    joinCanvas,
    clearError: () => setError(null),
  }
}
