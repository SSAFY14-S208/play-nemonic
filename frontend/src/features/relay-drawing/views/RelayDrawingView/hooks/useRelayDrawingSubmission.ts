'use client'

import { useCallback } from 'react'
import { HTTPError } from 'ky'

import { postRelayRoomSubmission } from '@/shared/apis'
import { completeFunnelStep } from '@/shared/libs'
import { useUserStore } from '@/shared/stores'

import type { RelayRoundKey } from '@/features/relay-drawing/constants'
import { useRelayDrawingStore } from '@/features/relay-drawing/stores'
import { relayToast } from '@/features/relay-drawing/utils'

import { useRelayDrawingCapture } from './useRelayDrawingCapture'

export function useRelayDrawingSubmission() {
  const { captureCanvasBlob, captureHintBlob } = useRelayDrawingCapture()

  const submitDrawing = useCallback(async () => {
    const store = useRelayDrawingStore.getState()

    if (store.isSubmitting || store.isSubmitted) return
    if (!store.roomCode || store.canvasIndex === null || !store.currentPart) return

    const submittingRoundKey = store.activeRoundKey
    store.setIsSubmitting(true)

    try {
      const [drawingImage, hintImage] = await Promise.all([
        captureCanvasBlob(),
        captureHintBlob(),
      ])

      if (!drawingImage) {
        store.setIsSubmitting(false)
        relayToast.error('그림을 이미지로 변환하지 못했습니다.')
        return
      }

      const response = await postRelayRoomSubmission({
        roomCode: store.roomCode,
        canvasIndex: store.canvasIndex,
        part: store.currentPart,
        drawingImage,
        hintImage: hintImage ?? undefined,
      })

      markCurrentUserSubmitted(submittingRoundKey)
      useRelayDrawingStore.getState().updateSubmissionProgress(
        response.submittedCount,
        response.totalCount,
      )
      completeFunnelStep('drawing', 4, {
        content_type: 'relay',
        room_id: store.roomCode,
      })
    } catch (error) {
      if (error instanceof HTTPError && error.response.status === 409) {
        markCurrentUserSubmitted(submittingRoundKey)
        return
      }

      useRelayDrawingStore.getState().setIsSubmitting(false)
      relayToast.error('제출에 실패했습니다. 다시 시도해주세요.')
    }
  }, [captureCanvasBlob, captureHintBlob])

  return { submitDrawing }
}

function markCurrentUserSubmitted(roundKey: RelayRoundKey) {
  useRelayDrawingStore.getState().markSubmitted(roundKey)

  const currentUserUuid = useUserStore.getState().userUuid
  if (currentUserUuid) {
    useRelayDrawingStore.getState().addSubmittedUserUuid(currentUserUuid)
  }
}
