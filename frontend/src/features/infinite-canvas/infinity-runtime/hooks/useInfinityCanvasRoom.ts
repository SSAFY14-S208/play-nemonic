'use client'

import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useRouter } from 'next/navigation'
import { toast } from 'sonner'
import {
  deleteInfiniteCanvasParticipantMe,
  postFileConfirm,
  postFilePresign,
  postInfiniteCanvasOutput,
  putFileToPresignedUrl,
} from '@/shared/apis'
import { useUserStore } from '@/shared/stores'
import type {
  InfiniteCanvasCursor,
  InfiniteCanvasCursorResponse,
  InfiniteCanvasJsonObject,
  InfiniteCanvasLock,
  InfiniteCanvasLockResponse,
  InfiniteCanvasOperation,
  InfiniteCanvasOperationRequest,
  InfiniteCanvasOpsAppliedResponse,
  InfiniteCanvasOutputSaveResponse,
  InfiniteCanvasParticipantResponse,
  InfiniteCanvasRealtimeEvent,
  InfiniteCanvasSimpleMessageResponse,
  InfiniteCanvasStateResponse,
} from '@/shared/types'
import { useInfinityRealtimeConnection } from './useInfinityRealtimeConnection'

const INFINITE_CANVAS_FILE_CONTENT_TYPE = 'image/png'
const INFINITE_CANVAS_FILE_PURPOSE = 'INFINITE_CANVAS'
const STALE_REVISION_MESSAGE = '캔버스 revision이 최신이 아닙니다.'
const MAX_OPERATIONS_PER_BATCH = 3

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function isStateResponse(value: unknown): value is InfiniteCanvasStateResponse {
  return (
    isRecord(value) &&
    typeof value.roomCode === 'string' &&
    Array.isArray(value.participants) &&
    Array.isArray(value.elements) &&
    typeof value.revision === 'number'
  )
}

function isParticipantResponse(value: unknown): value is InfiniteCanvasParticipantResponse {
  return isRecord(value) && typeof value.userUuid === 'string'
}

function isOpsAppliedResponse(value: unknown): value is InfiniteCanvasOpsAppliedResponse {
  return (
    isRecord(value) &&
    typeof value.roomCode === 'string' &&
    typeof value.revision === 'number' &&
    Array.isArray(value.operations)
  )
}

function isLockResponse(value: unknown): value is InfiniteCanvasLockResponse {
  return isRecord(value) && typeof value.roomCode === 'string' && typeof value.elementId === 'string'
}

function isCursorResponse(value: unknown): value is InfiniteCanvasCursorResponse {
  return isRecord(value) && typeof value.roomCode === 'string' && isRecord(value.cursor)
}

function isSimpleMessage(value: unknown): value is InfiniteCanvasSimpleMessageResponse {
  return isRecord(value)
}

function isStaleRevisionMessage(message: string) {
  return message.includes(STALE_REVISION_MESSAGE)
}

function createInitialRoomState(roomCode: string, userUuid: string): InfiniteCanvasStateResponse {
  const now = new Date().toISOString()

  return {
    roomCode,
    status: 'ACTIVE',
    ownerUserUuid: userUuid,
    me: null,
    participants: [],
    elements: [],
    operations: [],
    locks: {},
    viewport: null,
    maxParticipants: 0,
    revision: 0,
    createdAt: now,
    updatedAt: now,
  }
}

function applyOperationsToElements(
  currentElements: InfiniteCanvasJsonObject[],
  operations: InfiniteCanvasOperation[],
) {
  let nextElements = [...currentElements]

  for (const operation of operations) {
    if (operation.operationType === 'CLEAR_CANVAS') {
      nextElements = []
      continue
    }

    if (!operation.elementId) continue

    if (operation.operationType === 'DELETE_ELEMENT') {
      nextElements = nextElements.filter((element) => {
        if (!isRecord(element)) return true
        return element.id !== operation.elementId
      })
      continue
    }

    if (
      operation.operationType === 'CREATE_ELEMENT' ||
      operation.operationType === 'UPDATE_ELEMENT' ||
      operation.operationType === 'UPSERT_ELEMENT'
    ) {
      if (!operation.element) continue
      const nextElement = operation.element
      const existingIndex = nextElements.findIndex((element) => {
        if (!isRecord(element)) return false
        return element.id === operation.elementId
      })

      if (existingIndex >= 0) {
        nextElements = nextElements.map((element, elementIndex) =>
          elementIndex === existingIndex ? nextElement : element,
        )
      } else {
        nextElements = [...nextElements, nextElement]
      }
    }
  }

  return nextElements
}

async function uploadInfiniteCanvasOutput({
  roomCode,
  imageBlob,
  meta,
}: {
  roomCode: string
  imageBlob: Blob
  meta: Record<string, unknown> | null
}): Promise<InfiniteCanvasOutputSaveResponse> {
  const presigned = await postFilePresign({
    fileName: `infinite-canvas-${roomCode}-${Date.now()}.png`,
    contentType: INFINITE_CANVAS_FILE_CONTENT_TYPE,
    purpose: INFINITE_CANVAS_FILE_PURPOSE,
    byteSize: imageBlob.size,
  })

  await putFileToPresignedUrl({
    presignedUrl: presigned.presignedUrl,
    file: imageBlob,
    contentType: INFINITE_CANVAS_FILE_CONTENT_TYPE,
  })
  await postFileConfirm(presigned.fileId)

  return postInfiniteCanvasOutput(roomCode, {
    imageFileId: presigned.fileId,
    meta,
  })
}

export function useInfinityCanvasRoom(roomCode: string | null) {
  const router = useRouter()
  const userUuid = useUserStore((state) => state.userUuid)
  const [roomState, setRoomState] = useState<InfiniteCanvasStateResponse | null>(null)
  const [remoteCursors, setRemoteCursors] = useState<Record<string, InfiniteCanvasCursor>>({})
  const [isHydrating, setIsHydrating] = useState(true)
  const [isSavingOutput, setIsSavingOutput] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [operationQueueVersion, setOperationQueueVersion] = useState(0)
  const revisionRef = useRef(0)
  const pendingOperationsRef = useRef<InfiniteCanvasOperationRequest[]>([])
  const inFlightOperationsRef = useRef<InfiniteCanvasOperationRequest[] | null>(null)
  const isResolvingRevisionConflictRef = useRef(false)

  const bumpOperationQueue = useCallback(() => {
    setOperationQueueVersion((currentVersion) => currentVersion + 1)
  }, [])

  const hydrateRoom = useCallback(async () => {
    if (!roomCode || !userUuid) return null

    setIsHydrating(true)
    setErrorMessage(null)
    const nextState = createInitialRoomState(roomCode, userUuid)
    revisionRef.current = nextState.revision
    setRoomState(nextState)
    setIsHydrating(false)
    return nextState
  }, [roomCode, userUuid])

  const applyFullState = useCallback((nextState: InfiniteCanvasStateResponse) => {
    revisionRef.current = nextState.revision
    setRoomState(nextState)
  }, [])

  const handleRealtimeEvent = useCallback(
    (event: InfiniteCanvasRealtimeEvent) => {
      if (roomCode && event.roomCode !== roomCode) return

      if (
        event.type === 'STATE_SNAPSHOT' ||
        event.type === 'PARTICIPANT_CONNECTED' ||
        event.type === 'SNAPSHOT_UPDATED'
      ) {
        if (isStateResponse(event.data)) {
          applyFullState(event.data)
        }
        return
      }

      if (event.type === 'PARTICIPANT_LEFT') {
        if (isStateResponse(event.data)) {
          applyFullState(event.data)
          return
        }

        const leftUserUuid = isSimpleMessage(event.data) ? event.data.userUuid : null
        if (!leftUserUuid) return
        setRoomState((currentState) => {
          if (!currentState) return currentState
          return {
            ...currentState,
            participants: currentState.participants.filter(
              (participant) => participant.userUuid !== leftUserUuid,
            ),
          }
        })
        setRemoteCursors((currentCursors) => {
          const nextCursors = { ...currentCursors }
          delete nextCursors[leftUserUuid]
          return nextCursors
        })
        return
      }

      if (event.type === 'PARTICIPANT_UPDATED') {
        if (!isParticipantResponse(event.data)) return
        const nextParticipant = event.data
        setRoomState((currentState) => {
          if (!currentState) return currentState
          return {
            ...currentState,
            me:
              currentState.me?.userUuid === nextParticipant.userUuid
                ? nextParticipant
                : currentState.me,
            participants: currentState.participants.map((participant) =>
              participant.userUuid === nextParticipant.userUuid ? nextParticipant : participant,
            ),
          }
        })
        return
      }

      if (event.type === 'OPS_APPLIED') {
        if (!isOpsAppliedResponse(event.data)) return
        const appliedOperations = event.data
        revisionRef.current = appliedOperations.revision
        const inFlightOperations = inFlightOperationsRef.current
        if (inFlightOperations) {
          const inFlightOperationIds = new Set(
            inFlightOperations.map((operation) => operation.clientOperationId),
          )
          const acceptedInFlightOperation = appliedOperations.operations.some(
            (operation) =>
              operation.userUuid === userUuid &&
              inFlightOperationIds.has(operation.clientOperationId),
          )
          if (acceptedInFlightOperation || appliedOperations.operations.length === 0) {
            inFlightOperationsRef.current = null
            bumpOperationQueue()
          }
        }
        setRoomState((currentState) => {
          if (!currentState) return currentState
          return {
            ...currentState,
            elements: applyOperationsToElements(currentState.elements, appliedOperations.operations),
            operations: appliedOperations.operations,
            revision: appliedOperations.revision,
            updatedAt: event.occurredAt,
          }
        })
        return
      }

      if (event.type === 'LOCK_ACQUIRED' || event.type === 'LOCK_RELEASED') {
        if (!isLockResponse(event.data)) return
        const lockResponse = event.data
        setRoomState((currentState) => {
          if (!currentState) return currentState
          const nextLocks: Record<string, InfiniteCanvasLock> = { ...currentState.locks }
          if (lockResponse.lock) {
            nextLocks[lockResponse.elementId] = lockResponse.lock
          } else {
            delete nextLocks[lockResponse.elementId]
          }

          return {
            ...currentState,
            locks: nextLocks,
            updatedAt: event.occurredAt,
          }
        })
        return
      }

      if (event.type === 'CURSOR_UPDATED') {
        if (!isCursorResponse(event.data)) return
        const cursor = event.data.cursor
        if (cursor.userUuid === userUuid) return
        setRemoteCursors((currentCursors) => ({
          ...currentCursors,
          [cursor.userUuid]: cursor,
        }))
        return
      }

      if (event.type === 'CANVAS_CLOSED') {
        toast.info('무한 캔버스가 종료되었어요.')
        router.replace('/infinite-canvas')
        return
      }

      if (event.type === 'DUPLICATE_SESSION_CLOSED') {
        toast.info('다른 탭에서 접속되어 현재 연결이 종료되었어요.')
        router.replace('/infinite-canvas')
        return
      }

      if (event.type === 'ERROR') {
        const message = isSimpleMessage(event.data)
          ? event.data.message ?? '무한 캔버스 동기화 중 문제가 생겼어요.'
          : '무한 캔버스 동기화 중 문제가 생겼어요.'
        const inFlightOperations = inFlightOperationsRef.current
        inFlightOperationsRef.current = null

        if (isStaleRevisionMessage(message)) {
          if (inFlightOperations) {
            pendingOperationsRef.current = [
              ...inFlightOperations,
              ...pendingOperationsRef.current,
            ]
          }
          isResolvingRevisionConflictRef.current = true
          void hydrateRoom().finally(() => {
            isResolvingRevisionConflictRef.current = false
            bumpOperationQueue()
          })
          return
        }

        pendingOperationsRef.current = []
        isResolvingRevisionConflictRef.current = false
        setErrorMessage(message)
        toast.error(message)
        void hydrateRoom().finally(bumpOperationQueue)
      }
    },
    [applyFullState, bumpOperationQueue, roomCode, hydrateRoom, router, userUuid],
  )

  const realtime = useInfinityRealtimeConnection({
    enabled: Boolean(roomState && roomCode && userUuid),
    roomCode,
    onEvent: handleRealtimeEvent,
  })

  useEffect(() => {
    let cancelled = false

    void (async () => {
      if (!roomCode || !userUuid) {
        setIsHydrating(false)
        return
      }

      const nextState = await hydrateRoom()
      if (!cancelled && nextState) {
        revisionRef.current = nextState.revision
      }
    })()

    return () => {
      cancelled = true
    }
  }, [roomCode, hydrateRoom, userUuid])

  const sendOperations = useCallback(
    (operations: InfiniteCanvasOperationRequest[]) => {
      if (operations.length === 0) return false

      pendingOperationsRef.current = [...pendingOperationsRef.current, ...operations]
      bumpOperationQueue()
      return true
    },
    [bumpOperationQueue],
  )

  useEffect(() => {
    if (isResolvingRevisionConflictRef.current) return
    if (realtime.connectionStatus !== 'connected') return
    if (inFlightOperationsRef.current) return
    if (pendingOperationsRef.current.length === 0) return

    const pendingOperations = pendingOperationsRef.current.slice(0, MAX_OPERATIONS_PER_BATCH)
    pendingOperationsRef.current = pendingOperationsRef.current.slice(MAX_OPERATIONS_PER_BATCH)
    const sent = realtime.sendOperations({
      baseRevision: revisionRef.current,
      operations: pendingOperations,
    })
    if (!sent) {
      pendingOperationsRef.current = [...pendingOperations, ...pendingOperationsRef.current]
      return
    }
    inFlightOperationsRef.current = pendingOperations
  }, [bumpOperationQueue, operationQueueVersion, realtime])

  const saveOutput = useCallback(
    async (imageBlob: Blob, meta: Record<string, unknown> | null) => {
      if (!roomCode || isSavingOutput) return null

      setIsSavingOutput(true)
      setErrorMessage(null)
      try {
        const output = await uploadInfiniteCanvasOutput({
          roomCode,
          imageBlob,
          meta,
        })
        toast.success('선택한 영역을 갤러리에 저장했어요.')
        return output
      } catch {
        const message = '출력 이미지를 저장하지 못했어요.'
        setErrorMessage(message)
        toast.error(message)
        return null
      } finally {
        setIsSavingOutput(false)
      }
    },
    [roomCode, isSavingOutput],
  )

  const leaveCanvas = useCallback(async () => {
    if (!roomCode) return

    try {
      await deleteInfiniteCanvasParticipantMe(roomCode)
    } finally {
      router.replace('/infinite-canvas')
    }
  }, [roomCode, router])

  const participantsByUserUuid = useMemo(() => {
    const entries = roomState?.participants.map((participant) => [participant.userUuid, participant]) ?? []
    return Object.fromEntries(entries) as Record<string, InfiniteCanvasParticipantResponse>
  }, [roomState?.participants])

  const myUserUuid = roomState?.me?.userUuid ?? userUuid

  return {
    roomCode,
    inviteCode: roomState?.roomCode ?? roomCode,
    isHydrating,
    errorMessage,
    connectionStatus: realtime.connectionStatus,
    isSavingOutput,
    me: roomState?.me ?? null,
    participants: roomState?.participants ?? [],
    participantsByUserUuid,
    elements: roomState?.elements ?? [],
    locks: roomState?.locks ?? {},
    remoteCursors,
    maxParticipants: roomState?.maxParticipants ?? 0,
    revision: roomState?.revision ?? 0,
    myUserUuid,
    hydrateRoom,
    sendOperations,
    sendCursor: realtime.sendCursor,
    acquireLock: realtime.acquireLock,
    releaseLock: realtime.releaseLock,
    saveOutput,
    leaveCanvas,
  } as const
}
