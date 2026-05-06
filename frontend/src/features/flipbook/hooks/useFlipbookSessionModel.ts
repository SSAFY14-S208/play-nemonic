'use client'

import { useMemo } from 'react'
import type { DrawingLine, FlipbookSessionSettings } from '@/shared/types'
import type { FlipbookStep } from '../constants'
import type { FlipbookFrame } from '../types'
import {
  compactFlipbookFrames,
  createFlipbookAssignment,
  createFlipbookSessionSnapshot,
  toFlipbookFramePayloads,
} from '../utils'

export function useFlipbookSessionModel({
  activeRoundIndex,
  currentFrameLines,
  currentParticipantUserUuid,
  currentStep,
  frames,
  roomId,
  roundCount,
  settings,
}: {
  activeRoundIndex: number
  currentFrameLines: DrawingLine[]
  currentParticipantUserUuid: string
  currentStep: FlipbookStep
  frames: FlipbookFrame[]
  roomId: string | null
  roundCount: number
  settings: FlipbookSessionSettings
}) {
  const compactedFrames = useMemo(() => compactFlipbookFrames(frames), [frames])
  const previousFrameLines = useMemo(
    () =>
      activeRoundIndex > 0 && compactedFrames.length > 0
        ? compactedFrames[compactedFrames.length - 1].lines
        : [],
    [activeRoundIndex, compactedFrames],
  )
  const progressText = `${Math.min(activeRoundIndex + 1, roundCount)}/${roundCount}`
  const activeAssignment = useMemo(
    () =>
      createFlipbookAssignment({
        activeRoundIndex,
        currentStep,
        currentParticipantUserUuid,
        previousFrameLines,
      }),
    [activeRoundIndex, currentParticipantUserUuid, currentStep, previousFrameLines],
  )
  const completedFramePayloads = useMemo(
    () => toFlipbookFramePayloads(compactedFrames),
    [compactedFrames],
  )
  const sessionSnapshot = useMemo(
    () =>
      createFlipbookSessionSnapshot({
        activeAssignment,
        completedFramePayloads,
        currentFrameLines,
        currentStep,
        roomId,
        settings,
      }),
    [activeAssignment, completedFramePayloads, currentFrameLines, currentStep, roomId, settings],
  )

  return {
    compactedFrames,
    previousFrameLines,
    progressText,
    activeAssignment,
    sessionSnapshot,
  }
}
