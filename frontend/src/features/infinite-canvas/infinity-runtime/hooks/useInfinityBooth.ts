'use client'

import { useRouter } from 'next/navigation'
import { useState, useTransition } from 'react'
import { ApiError, postInfiniteCanvasCanvas, postInvite } from '@/shared/apis'
import { DEFAULT_USER_NICKNAME } from '@/shared/constants'
import { useUserStore } from '@/shared/stores'
import { INFINITY_COLORS } from '../constants'
import { saveInfiniteCanvasCreatedRoomSnapshot } from '../../utils'

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

  const hasConfiguredNickname = () => getConfiguredNickname() !== null

  const navigateToCanvas = (roomCode: string) => {
    router.push(`/infinite-canvas/${roomCode}`)
  }

  const createCanvas = () => {
    if (!isUserReady || isPending || !hasConfiguredNickname()) return

    setError(null)
    startTransition(async () => {
      try {
        const canvas = await postInfiniteCanvasCanvas({
          color: selectedColor,
        })
        saveInfiniteCanvasCreatedRoomSnapshot(canvas)
        navigateToCanvas(canvas.roomCode)
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
    if (!isUserReady || isPending || !hasConfiguredNickname()) return
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
