'use client'

import { useRouter } from 'next/navigation'
import { useState, useTransition } from 'react'

import { ApiError, postInfiniteCanvas } from '@/shared/apis'
import { useUserStore } from '@/shared/stores'

import { INFINITE_CANVAS_COLOR_OPTIONS } from '../constants'
import { buildInfiniteCanvasRoomPath } from '../utils'

const DEFAULT_SELECTED_COLOR = INFINITE_CANVAS_COLOR_OPTIONS[4].value

interface UseInfiniteCanvasEntryReturn {
  isUserReady: boolean
  isPending: boolean
  selectedColor: string
  errorMessage: string | null
  setSelectedColor: (color: string) => void
  createCanvas: () => void
  clearError: () => void
}

export function useInfiniteCanvasEntry(): UseInfiniteCanvasEntryReturn {
  const router = useRouter()
  const userUuid = useUserStore((state) => state.userUuid)
  const nickname = useUserStore((state) => state.nickname)

  const [selectedColor, setSelectedColor] = useState(DEFAULT_SELECTED_COLOR)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [isPending, startTransition] = useTransition()

  const isUserReady = userUuid !== null

  const clearError = () => setErrorMessage(null)

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

  return {
    isUserReady,
    isPending,
    selectedColor,
    errorMessage,
    setSelectedColor,
    createCanvas,
    clearError,
  }
}
