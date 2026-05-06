import type {
  DrawingLine,
  FlipbookDrawingAssignment,
  FlipbookFramePayload,
  FlipbookSessionSettings,
  FlipbookSessionSnapshot,
} from '@/shared/types'
import {
  FLIPBOOK_PARTICIPANTS,
  FLIPBOOK_ROOM_CODE,
  FLIPBOOK_TOPIC,
  type FlipbookStep,
  type FlipbookTimeLimitSeconds,
} from '../constants'
import type { FlipbookFrame } from '../types'

const DEMO_FLIPBOOK_ID = 'demo-flipbook'

export function compactFlipbookFrames(frames: FlipbookFrame[]) {
  return frames
    .filter((frame) => frame.lines.length > 0)
    .map((frame, frameIndex) => ({ ...frame, index: frameIndex }))
}

export function createFlipbookRequestId(actionName: string) {
  return `${actionName}-${Date.now()}-${crypto.randomUUID()}`
}

export function createFlipbookSettings({
  minimumRoundCount,
  participantCount,
  roundCount,
  selectedTimeLimitSeconds,
}: {
  minimumRoundCount: number
  participantCount: number
  roundCount: number
  selectedTimeLimitSeconds: FlipbookTimeLimitSeconds
}): FlipbookSessionSettings {
  return {
    timeLimitSeconds: selectedTimeLimitSeconds,
    roundCount,
    minimumRoundCount,
    frameCountPerFlipbook: participantCount * roundCount,
  }
}

export function toFlipbookFramePayloads(frames: FlipbookFrame[]): FlipbookFramePayload[] {
  return frames.map((frame) => ({
    frameId: frame.id,
    index: frame.index,
    flipbookId: DEMO_FLIPBOOK_ID,
    drawnByUserUuid: frame.drawnByUserUuid,
    drawnByNickname: frame.drawnBy,
    lines: frame.lines,
    isEmpty: frame.lines.length === 0,
  }))
}

export function createFlipbookAssignment({
  activeRoundIndex,
  currentStep,
  currentParticipantUserUuid,
  previousFrameLines,
}: {
  activeRoundIndex: number
  currentStep: FlipbookStep
  currentParticipantUserUuid: string
  previousFrameLines: DrawingLine[]
}): FlipbookDrawingAssignment | null {
  if (currentStep !== 'drawing') return null

  return {
    roundIndex: activeRoundIndex,
    frameId: `frame-${activeRoundIndex + 1}`,
    flipbookId: DEMO_FLIPBOOK_ID,
    drawingUserUuid: currentParticipantUserUuid,
    onionSkinFrameId: previousFrameLines.length > 0 ? `frame-${activeRoundIndex}` : null,
    onionSkinLines: previousFrameLines,
    deadlineAt: null,
  }
}

export function createFlipbookSessionSnapshot({
  activeAssignment,
  completedFramePayloads,
  currentFrameLines,
  currentStep,
  roomId,
  settings,
}: {
  activeAssignment: FlipbookDrawingAssignment | null
  completedFramePayloads: FlipbookFramePayload[]
  currentFrameLines: DrawingLine[]
  currentStep: FlipbookStep
  roomId: string | null
  settings: FlipbookSessionSettings
}): FlipbookSessionSnapshot {
  return {
    roomId,
    roomCode: FLIPBOOK_ROOM_CODE,
    topic: FLIPBOOK_TOPIC,
    phase: currentStep,
    participants: FLIPBOOK_PARTICIPANTS.map((participant, participantIndex) => ({
      userUuid: participant.userUuid,
      nickname: participant.name,
      avatar: participant.avatar,
      joinedOrder: participantIndex,
      isHost: participant.isHost === true,
      connectionStatus: 'online',
    })),
    settings,
    activeAssignment,
    currentFrameLines,
    completedFrames: completedFramePayloads,
    result:
      currentStep === 'result'
        ? {
            flipbookId: DEMO_FLIPBOOK_ID,
            frames: completedFramePayloads,
            gifUrl: '/api/mock/flipbook/flipbook_uuid.gif',
          }
        : null,
    serverSyncedAt: null,
  }
}
