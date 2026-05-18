'use client'

import { useCallback, useState, type RefObject } from 'react'
import type Konva from 'konva'
import { HTTPError, TimeoutError } from 'ky'
import { toast } from 'sonner'
import { ApiError, postInfiniteCanvasAiSticker } from '@/shared/apis'
import type { InfinityImage, InfinityObject } from '../constants'
import type { ApiResponse } from '@/shared/types'

interface UseInfinityAiStickerParams {
  roomCode: string
  stageRef: RefObject<Konva.Stage | null>
  scaleRef: RefObject<number>
  stagePosRef: RefObject<{ x: number; y: number }>
  addObject: (object: InfinityObject) => void
}

function getNumberValue(value: unknown, fallback: number) {
  return typeof value === 'number' && Number.isFinite(value) ? value : fallback
}

function getStringValue(value: unknown, fallback: string) {
  return typeof value === 'string' && value.trim().length > 0 ? value : fallback
}

function hasResponse(value: unknown): value is { response: Response } {
  return (
    typeof value === 'object' &&
    value !== null &&
    'response' in value &&
    value.response instanceof Response
  )
}

function getErrorMessage(value: unknown) {
  if (value instanceof Error && value.message.trim().length > 0) {
    return value.message
  }

  return null
}

async function getCreateErrorMessage(caughtError: unknown) {
  if (caughtError instanceof ApiError) {
    return caughtError.message
  }

  if (caughtError instanceof TimeoutError) {
    return 'AI 스티커 생성 시간이 길어지고 있어요. 잠시 후 다시 시도해주세요.'
  }

  if (caughtError instanceof HTTPError || hasResponse(caughtError)) {
    try {
      const response = (await caughtError.response.json()) as Partial<ApiResponse<unknown>>
      if (typeof response.message === 'string' && response.message.trim().length > 0) {
        return response.message
      }
    } catch {
      // 응답 본문이 JSON이 아니면 아래 기본 메시지를 사용한다.
    }

    return `AI 스티커 생성 요청에 실패했어요. (${caughtError.response.status})`
  }

  return getErrorMessage(caughtError) ?? 'AI 스티커를 생성하지 못했어요.'
}

export function useInfinityAiSticker({
  roomCode,
  stageRef,
  scaleRef,
  stagePosRef,
  addObject,
}: UseInfinityAiStickerParams) {
  const [isOpen, setIsOpen] = useState(false)
  const [isCreating, setIsCreating] = useState(false)

  const createStickerObject = useCallback(
    (element: Record<string, unknown>): InfinityImage => {
      const stage = stageRef.current
      const scale = scaleRef.current || 1
      const stagePosition = stagePosRef.current
      const width = getNumberValue(element.width, 220)
      const height = getNumberValue(element.height, 220)
      const centerX = stage
        ? (stage.width() / 2 - stagePosition.x) / scale
        : -width / 2
      const centerY = stage
        ? (stage.height() / 2 - stagePosition.y) / scale
        : -height / 2

      return {
        id: getStringValue(element.id, `ai-sticker-${Date.now()}`),
        type: 'image',
        x: centerX - width / 2,
        y: centerY - height / 2,
        width,
        height,
        src: getStringValue(element.src, ''),
        objectKey: typeof element.objectKey === 'string' ? element.objectKey : undefined,
        naturalWidth: getNumberValue(element.naturalWidth, width),
        naturalHeight: getNumberValue(element.naturalHeight, height),
        metadata:
          typeof element.metadata === 'object' && element.metadata !== null
            ? (element.metadata as Record<string, unknown>)
            : undefined,
      }
    },
    [scaleRef, stagePosRef, stageRef],
  )

  const createSticker = useCallback(
    async (prompt: string) => {
      if (!roomCode || isCreating) return false
      setIsCreating(true)
      try {
        const sticker = await postInfiniteCanvasAiSticker(roomCode, {
          prompt,
          style: 'sticker',
          width: 512,
          height: 512,
          transparentBackground: true,
        })
        const stickerObject = createStickerObject(sticker.element)
        if (!stickerObject.src) {
          toast.error('AI 스티커 이미지 주소를 확인하지 못했어요.')
          return false
        }

        addObject(stickerObject)
        setIsOpen(false)
        toast.success('AI 스티커를 캔버스에 추가했어요.')
        return true
      } catch (caughtError) {
        toast.error(await getCreateErrorMessage(caughtError))
        return false
      } finally {
        setIsCreating(false)
      }
    },
    [addObject, createStickerObject, isCreating, roomCode],
  )

  return {
    isOpen,
    isCreating,
    open: () => setIsOpen(true),
    close: () => setIsOpen(false),
    createSticker,
  }
}
