'use client'

import { useEffect } from 'react'

import { useRelayDrawingStore } from '@/features/relay-drawing/stores'

import { useRelayDrawingAssignment } from './useRelayDrawingAssignment'
import { useRelayDrawingSubmission } from './useRelayDrawingSubmission'

interface UseRelayDrawingGameReturn {
  submitDrawing: () => Promise<void>
  isSubmitting: boolean
  isSubmitted: boolean
  submittedCount: number
  totalCount: number
  hintImageUrl: string | null
}

export function useRelayDrawingGame(): UseRelayDrawingGameReturn {
  const roomCode = useRelayDrawingStore((state) => state.roomCode)
  const roomStatus = useRelayDrawingStore((state) => state.roomStatus)
  const isSubmitting = useRelayDrawingStore((state) => state.isSubmitting)
  const isSubmitted = useRelayDrawingStore((state) => state.isSubmitted)
  const submittedCount = useRelayDrawingStore((state) => state.submittedCount)
  const totalCount = useRelayDrawingStore((state) => state.totalCount)
  const hintImageUrl = useRelayDrawingStore((state) => state.hintImageUrl)
  const partFetchTrigger = useRelayDrawingStore((state) => state.partFetchTrigger)
  const pendingAutoSubmitTrigger = useRelayDrawingStore(
    (state) => state.pendingAutoSubmitTrigger,
  )

  useRelayDrawingAssignment({ roomCode, roomStatus, partFetchTrigger })
  const { submitDrawing } = useRelayDrawingSubmission()

  useEffect(() => {
    if (pendingAutoSubmitTrigger === 0) return
    void submitDrawing()
  }, [pendingAutoSubmitTrigger, submitDrawing])

  return {
    submitDrawing,
    isSubmitting,
    isSubmitted,
    submittedCount,
    totalCount,
    hintImageUrl,
  }
}
