'use client'

import { useCallback } from 'react'
import { useUserStore } from '@/shared/stores'
import type {
  DrawingLine, FlipbookClientMessage, FlipbookDrawingAssignment, FlipbookSessionSettings, FlipbookSessionSnapshot, } from '@/shared/types'
import { FLIPBOOK_ROOM_CODE, useFlipbookRealtimeStore } from '..'

import { createFlipbookRequestId } from '../utils'

export function useFlipbookRealtimeActions() {
  const userUuid = useUserStore((state) => state.userUuid)
  const nickname = useUserStore((state) => state.nickname)
  const connectionStatus = useFlipbookRealtimeStore((state) => state.connectionStatus)
  const roomId = useFlipbookRealtimeStore((state) => state.roomId)
  const outboundMessages = useFlipbookRealtimeStore((state) => state.outboundMessages)
  const enqueueRealtimeClientMessage = useFlipbookRealtimeStore(
    (state) => state.enqueueClientMessage,
  )
  const setRealtimeSessionSnapshot = useFlipbookRealtimeStore(
    (state) => state.setSessionSnapshot,
  )

  const enqueueClientMessage = useCallback(
    (message: FlipbookClientMessage) => {
      enqueueRealtimeClientMessage(message)
    },
    [enqueueRealtimeClientMessage],
  )

  const syncSessionSnapshot = useCallback(
    (sessionSnapshot: FlipbookSessionSnapshot) => {
      setRealtimeSessionSnapshot(sessionSnapshot)
    },
    [setRealtimeSessionSnapshot],
  )

  const enqueueCreateRoom = useCallback(() => {
    enqueueClientMessage({
      type: 'flipbook.room.create',
      requestId: createFlipbookRequestId('room-create'),
      payload: {
        userUuid,
        nickname: nickname ?? '나',
      },
    })
  }, [enqueueClientMessage, nickname, userUuid])

  const enqueueEnterRoom = useCallback(() => {
    enqueueClientMessage({
      type: 'flipbook.room.join',
      requestId: createFlipbookRequestId('room-join'),
      payload: {
        roomId,
        roomCode: FLIPBOOK_ROOM_CODE,
        userUuid,
        nickname: nickname ?? '나',
      },
    })
  }, [enqueueClientMessage, nickname, roomId, userUuid])

  const enqueueStartGame = useCallback(
    (settings: FlipbookSessionSettings) => {
      enqueueClientMessage({
        type: 'flipbook.game.start',
        requestId: createFlipbookRequestId('game-start'),
        payload: {
          roomId,
          settings,
        },
      })
    },
    [enqueueClientMessage, roomId],
  )

  const enqueueSettingsUpdate = useCallback(
    (settings: FlipbookSessionSettings) => {
      enqueueClientMessage({
        type: 'flipbook.settings.update',
        requestId: createFlipbookRequestId('settings-update'),
        payload: settings,
      })
    },
    [enqueueClientMessage],
  )

  const enqueueFrameSubmit = useCallback(
    ({
      assignment,
      lines,
    }: {
      assignment: FlipbookDrawingAssignment | null
      lines: DrawingLine[]
    }) => {
      enqueueClientMessage({
        type: 'flipbook.frame.submit',
        requestId: createFlipbookRequestId('frame-submit'),
        payload: {
          roomId,
          assignment,
          lines,
          submittedAt: new Date().toISOString(),
        },
      })
    },
    [enqueueClientMessage, roomId],
  )

  const enqueueLeaveRoom = useCallback(() => {
    enqueueClientMessage({
      type: 'flipbook.room.leave',
      requestId: createFlipbookRequestId('room-leave'),
      payload: {
        roomId,
        userUuid,
      },
    })
  }, [enqueueClientMessage, roomId, userUuid])

  return {
    connectionStatus,
    roomId,
    outboundMessages,
    syncSessionSnapshot,
    enqueueCreateRoom,
    enqueueEnterRoom,
    enqueueStartGame,
    enqueueSettingsUpdate,
    enqueueFrameSubmit,
    enqueueLeaveRoom,
  }
}
