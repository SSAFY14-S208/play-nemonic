'use client'

import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useRouter } from 'next/navigation'
import { HTTPError } from 'ky'
import { toast } from 'sonner'
import {
  ApiError,
  deleteInfiniteCanvasParticipantMe,
  getInfiniteCanvasState,
  patchInfiniteCanvasParticipantColor,
  postFileConfirm,
  postFilePresign,
  postInvite,
  postInfiniteCanvasOutput,
  putFileToPresignedUrl,
} from '@/shared/apis'
import { useUserStore } from '@/shared/stores'
import type {
  InfiniteCanvasCreateResponse,
  InfiniteCanvasCursor,
  InfiniteCanvasCursorResponse,
  InfiniteCanvasJsonObject,
  InfiniteCanvasLeaveResponse,
  InfiniteCanvasLock,
  InfiniteCanvasLockResponse,
  InfiniteCanvasOperationRequest,
  InfiniteCanvasOperationType,
  InfiniteCanvasOpsAppliedResponse,
  InfiniteCanvasOutputSaveResponse,
  InfiniteCanvasParticipantEventResponse,
  InfiniteCanvasParticipantResponse,
  InfiniteCanvasRealtimeEvent,
  InfiniteCanvasRevisionConflictResponse,
  InfiniteCanvasSimpleMessageResponse,
  InfiniteCanvasStateResponse,
} from '@/shared/types'
import { takeInfiniteCanvasCreatedRoomSnapshot } from '../../utils'
import { INFINITY_COLORS } from '../constants'
import { useInfinityRealtimeConnection } from './useInfinityRealtimeConnection'

const INFINITE_CANVAS_FILE_CONTENT_TYPE = 'image/png'
const INFINITE_CANVAS_FILE_PURPOSE = 'INFINITE_CANVAS'
const STALE_REVISION_MESSAGE = '캔버스 revision이 최신이 아닙니다.'
const UPDATE_CONFLICT_MESSAGE = '무한 캔버스 상태 갱신 충돌이 발생했습니다.'
const MAX_OPERATIONS_PER_BATCH = 20
const IN_FLIGHT_OPERATION_TIMEOUT_MS = 3500

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

function isParticipantEventResponse(value: unknown): value is InfiniteCanvasParticipantEventResponse {
  return (
    isRecord(value) &&
    typeof value.roomCode === 'string' &&
    Array.isArray(value.participants) &&
    typeof value.revision === 'number'
  )
}

function isParticipantResponse(value: unknown): value is InfiniteCanvasParticipantResponse {
  return isRecord(value) && typeof value.userUuid === 'string'
}

function isLeaveResponse(value: unknown): value is InfiniteCanvasLeaveResponse {
  return isRecord(value) && typeof value.roomCode === 'string' && typeof value.userUuid === 'string'
}

function isOpsAppliedResponse(value: unknown): value is InfiniteCanvasOpsAppliedResponse {
  return (
    isRecord(value) &&
    typeof value.roomCode === 'string' &&
    typeof value.revision === 'number' &&
    Array.isArray(value.operations)
  )
}

function isRevisionConflictResponse(value: unknown): value is InfiniteCanvasRevisionConflictResponse {
  return (
    isRecord(value) &&
    typeof value.roomCode === 'string' &&
    typeof value.baseRevision === 'number' &&
    typeof value.latestRevision === 'number' &&
    Array.isArray(value.missingOperations) &&
    typeof value.fullStateRequired === 'boolean'
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

function getRevisionConflictDetails(value: unknown) {
  if (!isRecord(value)) return null
  return isRevisionConflictResponse(value.details) ? value.details : null
}

function isStaleRevisionMessage(message: string) {
  return message.includes(STALE_REVISION_MESSAGE)
}

function isRetryableStateConflictMessage(message: string) {
  return message.includes(UPDATE_CONFLICT_MESSAGE)
}

function createFallbackParticipant(
  userUuid: string,
  nickname: string | null,
  now: string,
  host = false,
): InfiniteCanvasParticipantResponse {
  return {
    userUuid,
    nickname: nickname?.trim() || '나',
    color: INFINITY_COLORS[4],
    avatarUrl: null,
    host,
    connected: true,
    joinedAt: now,
    lastConnectedAt: now,
  }
}

function createInitialRoomState({
  roomCode,
  userUuid,
  nickname,
  snapshot,
}: {
  roomCode: string
  userUuid: string
  nickname: string | null
  snapshot: InfiniteCanvasCreateResponse | null
}): InfiniteCanvasStateResponse {
  const now = new Date().toISOString()
  const hostUserUuid = snapshot?.hostUserUuid ?? userUuid
  const participants =
    snapshot?.participants.length
      ? snapshot.participants.map((participant) => ({
          ...participant,
          host: participant.userUuid === hostUserUuid,
        }))
      : [createFallbackParticipant(userUuid, nickname, now, true)]
  const me =
    participants.find((participant) => participant.userUuid === userUuid) ??
    createFallbackParticipant(userUuid, nickname, now)

  return {
    roomCode,
    status: snapshot?.status ?? 'ACTIVE',
    hostUserUuid,
    me,
    participants: participants.some((participant) => participant.userUuid === me.userUuid)
      ? participants
      : [me, ...participants],
    elements: [],
    operations: [],
    locks: {},
    viewport: null,
    maxParticipants: snapshot?.maxParticipants ?? 0,
    revision: 0,
    createdAt: snapshot?.createdAt ?? now,
    updatedAt: now,
  }
}

interface CanvasElementMutation {
  operationId?: string | null
  clientOperationId?: string | null
  revision?: number | null
  operationType: InfiniteCanvasOperationType
  elementId?: string | null
  element?: InfiniteCanvasJsonObject | null
}

function getCanvasOperationKey(operation: CanvasElementMutation) {
  if (operation.clientOperationId && operation.clientOperationId.trim().length > 0) {
    return `client:${operation.clientOperationId}`
  }
  if (operation.operationId && operation.operationId.trim().length > 0) {
    return `server:${operation.operationId}`
  }
  if (typeof operation.revision === 'number') {
    return `revision:${operation.revision}`
  }
  return null
}

function getCanvasElementId(element: InfiniteCanvasJsonObject) {
  return typeof element.id === 'string' && element.id.trim().length > 0 ? element.id : null
}

function applyOperationsToElements(
  currentElements: InfiniteCanvasJsonObject[],
  operations: CanvasElementMutation[],
) {
  if (operations.length === 0) return currentElements

  const elementById = new Map<string, InfiniteCanvasJsonObject>()
  const elementOrder: string[] = []
  const anonymousElements: InfiniteCanvasJsonObject[] = []

  for (const element of currentElements) {
    const elementId = getCanvasElementId(element)
    if (!elementId) {
      anonymousElements.push(element)
      continue
    }

    if (!elementById.has(elementId)) {
      elementOrder.push(elementId)
    }
    elementById.set(elementId, element)
  }
  let shouldClearAnonymousElements = false

  for (const operation of operations) {
    if (operation.operationType === 'CLEAR_CANVAS') {
      elementById.clear()
      elementOrder.length = 0
      shouldClearAnonymousElements = true
      continue
    }

    if (!operation.elementId) continue

    if (operation.operationType === 'DELETE_ELEMENT') {
      elementById.delete(operation.elementId)
      continue
    }

    if (
      operation.operationType === 'CREATE_ELEMENT' ||
      operation.operationType === 'UPDATE_ELEMENT' ||
      operation.operationType === 'UPSERT_ELEMENT'
    ) {
      if (!operation.element) continue
      const nextElement = operation.element
      if (!elementById.has(operation.elementId)) {
        elementOrder.push(operation.elementId)
      }
      elementById.set(operation.elementId, nextElement)
    }
  }

  const orderedElements = elementOrder
    .map((elementId) => elementById.get(elementId))
    .filter((element): element is InfiniteCanvasJsonObject => Boolean(element))
  return shouldClearAnonymousElements ? orderedElements : [...anonymousElements, ...orderedElements]
}

function applyOptimisticOperationsToState(
  state: InfiniteCanvasStateResponse,
  operations: InfiniteCanvasOperationRequest[],
): InfiniteCanvasStateResponse {
  if (operations.length === 0) return state
  return {
    ...state,
    elements: applyOperationsToElements(state.elements, operations),
  }
}

function isElementUpsertOperation(operation: InfiniteCanvasOperationRequest) {
  return (
    operation.operationType === 'CREATE_ELEMENT' ||
    operation.operationType === 'UPDATE_ELEMENT' ||
    operation.operationType === 'UPSERT_ELEMENT'
  )
}

function compactPendingOperations(operations: InfiniteCanvasOperationRequest[]) {
  if (operations.length <= 1) return operations

  const compactedOperations: InfiniteCanvasOperationRequest[] = []
  const latestOperationIndexByElementId = new Map<string, number>()

  for (const operation of operations) {
    if (operation.operationType === 'CLEAR_CANVAS') {
      compactedOperations.length = 0
      latestOperationIndexByElementId.clear()
      compactedOperations.push(operation)
      continue
    }

    const elementId = operation.elementId?.trim()
    if (!elementId) {
      compactedOperations.push(operation)
      continue
    }

    const previousIndex = latestOperationIndexByElementId.get(elementId)
    if (previousIndex === undefined) {
      latestOperationIndexByElementId.set(elementId, compactedOperations.length)
      compactedOperations.push(operation)
      continue
    }

    if (
      operation.operationType === 'DELETE_ELEMENT' ||
      isElementUpsertOperation(operation)
    ) {
      compactedOperations[previousIndex] = operation
    } else {
      compactedOperations.push(operation)
    }
  }

  return compactedOperations
}

function canPatchStateFromRecentOperations(
  currentState: InfiniteCanvasStateResponse,
  nextState: InfiniteCanvasStateResponse,
) {
  if (nextState.revision < currentState.revision) return false
  if (nextState.revision === currentState.revision) return true
  if (nextState.operations.length === 0) return false

  return nextState.operations.some((operation) => operation.revision > currentState.revision)
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
  const nickname = useUserStore((state) => state.nickname)
  const [roomState, setRoomState] = useState<InfiniteCanvasStateResponse | null>(null)
  const [remoteCursors, setRemoteCursors] = useState<Record<string, InfiniteCanvasCursor>>({})
  const [isHydrating, setIsHydrating] = useState(true)
  const [isSavingOutput, setIsSavingOutput] = useState(false)
  const [isUpdatingProfile, setIsUpdatingProfile] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [operationQueueVersion, setOperationQueueVersion] = useState(0)
  const [hasPendingOperations, setHasPendingOperations] = useState(false)
  const revisionRef = useRef(0)
  const appliedElementsRevisionRef = useRef(0)
  const pendingOperationsRef = useRef<InfiniteCanvasOperationRequest[]>([])
  const inFlightOperationsRef = useRef<InfiniteCanvasOperationRequest[] | null>(null)
  const inFlightTimeoutRef = useRef<number | null>(null)
  const confirmedClientOperationIdsRef = useRef<Set<string>>(new Set())
  const appliedOperationKeysRef = useRef<Set<string>>(new Set())
  const isResolvingRevisionConflictRef = useRef(false)
  const pendingRemoteCursorUpdatesRef = useRef<Record<string, InfiniteCanvasCursor>>({})
  const remoteCursorFrameRef = useRef<number | null>(null)

  const bumpOperationQueue = useCallback(() => {
    setOperationQueueVersion((currentVersion) => currentVersion + 1)
  }, [])

  const syncPendingOperationState = useCallback(() => {
    setHasPendingOperations(
      pendingOperationsRef.current.length > 0 ||
        inFlightOperationsRef.current !== null ||
        isResolvingRevisionConflictRef.current,
    )
  }, [])

  const clearInFlightTimeout = useCallback(() => {
    if (inFlightTimeoutRef.current === null) return
    window.clearTimeout(inFlightTimeoutRef.current)
    inFlightTimeoutRef.current = null
  }, [])

  const requeueInFlightOperations = useCallback(() => {
    clearInFlightTimeout()
    const inFlightOperations = inFlightOperationsRef.current
    if (!inFlightOperations) return

    const unconfirmedInFlightOperations = inFlightOperations.filter(
      (operation) => !confirmedClientOperationIdsRef.current.has(operation.clientOperationId),
    )
    inFlightOperationsRef.current = null
    if (unconfirmedInFlightOperations.length > 0) {
      pendingOperationsRef.current = [
        ...unconfirmedInFlightOperations,
        ...pendingOperationsRef.current,
      ]
    }
    syncPendingOperationState()
    bumpOperationQueue()
  }, [bumpOperationQueue, clearInFlightTimeout, syncPendingOperationState])

  const scheduleRemoteCursorUpdate = useCallback((cursor: InfiniteCanvasCursor) => {
    pendingRemoteCursorUpdatesRef.current[cursor.userUuid] = cursor
    if (remoteCursorFrameRef.current !== null) return

    remoteCursorFrameRef.current = window.requestAnimationFrame(() => {
      remoteCursorFrameRef.current = null
      const pendingUpdates = pendingRemoteCursorUpdatesRef.current
      pendingRemoteCursorUpdatesRef.current = {}
      setRemoteCursors((currentCursors) => ({
        ...currentCursors,
        ...pendingUpdates,
      }))
    })
  }, [])

  useEffect(
    () => () => {
      clearInFlightTimeout()
      if (remoteCursorFrameRef.current !== null) {
        window.cancelAnimationFrame(remoteCursorFrameRef.current)
      }
    },
    [clearInFlightTimeout],
  )

  const getUnconfirmedOperations = useCallback(() => {
    const inFlightOperations = inFlightOperationsRef.current ?? []
    return [...inFlightOperations, ...pendingOperationsRef.current]
  }, [])

  const recordConfirmedOperations = useCallback((operations: CanvasElementMutation[]) => {
    for (const operation of operations) {
      if (!('clientOperationId' in operation)) continue
      const clientOperationId = operation.clientOperationId
      if (typeof clientOperationId === 'string' && clientOperationId.trim().length > 0) {
        confirmedClientOperationIdsRef.current.add(clientOperationId)
      }
    }
  }, [])

  const areOperationsConfirmed = useCallback((operations: InfiniteCanvasOperationRequest[]) => {
    return operations.every((operation) =>
      confirmedClientOperationIdsRef.current.has(operation.clientOperationId),
    )
  }, [])

  const getUnappliedOperations = useCallback((operations: CanvasElementMutation[]) => {
    return operations.filter((operation) => {
      const operationKey = getCanvasOperationKey(operation)
      return !operationKey || !appliedOperationKeysRef.current.has(operationKey)
    })
  }, [])

  const recordAppliedOperations = useCallback((operations: CanvasElementMutation[]) => {
    for (const operation of operations) {
      const operationKey = getCanvasOperationKey(operation)
      if (operationKey) {
        appliedOperationKeysRef.current.add(operationKey)
      }
    }
  }, [])

  const hydrateRoom = useCallback(async (options: { showLoading?: boolean } = {}) => {
    if (!roomCode || !userUuid) return null
    const showLoading = options.showLoading ?? true

    if (showLoading) {
      setIsHydrating(true)
    }
    setErrorMessage(null)

    try {
      let nextState = await getInfiniteCanvasState(roomCode)
      if (!nextState.me) {
        await postInvite(roomCode)
        nextState = await getInfiniteCanvasState(roomCode)
      }
      const optimisticOperations = getUnconfirmedOperations()
      const hydratedState = applyOptimisticOperationsToState(nextState, optimisticOperations)
      recordConfirmedOperations(nextState.operations)
      recordAppliedOperations(nextState.operations)
      revisionRef.current = nextState.revision
      appliedElementsRevisionRef.current = nextState.revision
      setRoomState(hydratedState)
      setIsHydrating(false)
      return hydratedState
    } catch (caughtError) {
      const snapshot = takeInfiniteCanvasCreatedRoomSnapshot(roomCode)
      if (snapshot) {
        const fallbackState = createInitialRoomState({
          roomCode,
          userUuid,
          nickname,
          snapshot,
        })
        revisionRef.current = fallbackState.revision
        appliedElementsRevisionRef.current = fallbackState.revision
        setRoomState(fallbackState)
        setIsHydrating(false)
        return fallbackState
      }

      const message =
        caughtError instanceof ApiError
          ? caughtError.message
          : '무한 캔버스 방 상태를 불러오지 못했어요.'
      setRoomState(null)
      revisionRef.current = 0
      appliedElementsRevisionRef.current = 0
      setErrorMessage(message)
      return null
    }
  }, [getUnconfirmedOperations, nickname, recordAppliedOperations, recordConfirmedOperations, roomCode, userUuid])

  const applyFullState = useCallback((nextState: InfiniteCanvasStateResponse) => {
    if (nextState.revision < revisionRef.current) return

    const unconfirmedOperations = getUnconfirmedOperations()
    recordConfirmedOperations(nextState.operations)
    revisionRef.current = nextState.revision
    setRoomState((currentState) => {
      const shouldPatchFromOperations =
        currentState !== null && canPatchStateFromRecentOperations(currentState, nextState)
      const remoteElements =
        shouldPatchFromOperations && currentState
          ? applyOperationsToElements(
              currentState.elements,
              getUnappliedOperations(
                nextState.operations.filter((operation) => operation.revision > currentState.revision),
              ),
            )
          : nextState.elements
      const stateWithRemoteElements = {
        ...nextState,
        elements: remoteElements,
      }
      const stateWithLocalOperations =
        unconfirmedOperations.length > 0
          ? applyOptimisticOperationsToState(stateWithRemoteElements, unconfirmedOperations)
          : stateWithRemoteElements

      return stateWithLocalOperations
    })
    recordAppliedOperations(nextState.operations)
    appliedElementsRevisionRef.current = nextState.revision
    setIsHydrating(false)
  }, [getUnappliedOperations, getUnconfirmedOperations, recordAppliedOperations, recordConfirmedOperations])

  const applyRevisionConflictDelta = useCallback((details: InfiniteCanvasRevisionConflictResponse) => {
    const unappliedOperations = getUnappliedOperations(details.missingOperations)
    revisionRef.current = details.latestRevision
    appliedElementsRevisionRef.current = details.latestRevision
    recordConfirmedOperations(details.missingOperations)
    recordAppliedOperations(unappliedOperations)
    setRoomState((currentState) => {
      if (!currentState) return currentState
      return {
        ...currentState,
        elements: applyOperationsToElements(currentState.elements, unappliedOperations),
        operations: details.missingOperations,
        revision: details.latestRevision,
        updatedAt: new Date().toISOString(),
      }
    })
  }, [getUnappliedOperations, recordAppliedOperations, recordConfirmedOperations])

  const resolveRevisionConflict = useCallback(
    (details: InfiniteCanvasRevisionConflictResponse | null, inFlightOperations: InfiniteCanvasOperationRequest[] | null) => {
      if (inFlightOperations) {
        pendingOperationsRef.current = [
          ...inFlightOperations,
          ...pendingOperationsRef.current,
        ]
      }

      if (details && !details.fullStateRequired) {
        applyRevisionConflictDelta(details)
        isResolvingRevisionConflictRef.current = false
        syncPendingOperationState()
        bumpOperationQueue()
        return
      }

      isResolvingRevisionConflictRef.current = true
      syncPendingOperationState()
      void hydrateRoom({ showLoading: false }).finally(() => {
        isResolvingRevisionConflictRef.current = false
        syncPendingOperationState()
        bumpOperationQueue()
      })
    },
    [applyRevisionConflictDelta, bumpOperationQueue, hydrateRoom, syncPendingOperationState],
  )

  const resolveRetryableStateConflict = useCallback(
    (inFlightOperations: InfiniteCanvasOperationRequest[] | null) => {
      if (inFlightOperations) {
        pendingOperationsRef.current = [
          ...inFlightOperations,
          ...pendingOperationsRef.current,
        ]
      }

      isResolvingRevisionConflictRef.current = true
      syncPendingOperationState()
      void hydrateRoom({ showLoading: false }).finally(() => {
        isResolvingRevisionConflictRef.current = false
        syncPendingOperationState()
        bumpOperationQueue()
      })
    },
    [bumpOperationQueue, hydrateRoom, syncPendingOperationState],
  )

  const handleRealtimeEvent = useCallback(
    (event: InfiniteCanvasRealtimeEvent) => {
      if (roomCode && event.roomCode !== roomCode) return

      if (event.type === 'STATE_SNAPSHOT' || event.type === 'SNAPSHOT_UPDATED') {
        if (isStateResponse(event.data)) {
          applyFullState(event.data)
        }
        return
      }

      if (event.type === 'PARTICIPANT_CONNECTED' || event.type === 'PARTICIPANT_DISCONNECTED') {
        if (!isParticipantEventResponse(event.data)) return
        const participantEvent = event.data
        setRoomState((currentState) => {
          if (!currentState) return currentState
          const nextMe = currentState.me
            ? participantEvent.participants.find(
                (participant) => participant.userUuid === currentState.me?.userUuid,
              ) ?? currentState.me
            : null

          return {
            ...currentState,
            hostUserUuid: participantEvent.hostUserUuid,
            me: nextMe,
            participants: participantEvent.participants,
            maxParticipants: participantEvent.maxParticipants,
            revision: participantEvent.revision,
            updatedAt: participantEvent.updatedAt,
          }
        })
        const changedParticipantUserUuid = participantEvent.changedParticipant?.userUuid
        if (event.type === 'PARTICIPANT_DISCONNECTED' && changedParticipantUserUuid) {
          setRemoteCursors((currentCursors) => {
            const nextCursors = { ...currentCursors }
            delete nextCursors[changedParticipantUserUuid]
            return nextCursors
          })
        }
        return
      }

      if (event.type === 'PARTICIPANT_LEFT') {
        if (isStateResponse(event.data)) {
          applyFullState(event.data)
          return
        }

        if (!isLeaveResponse(event.data)) return
        const leaveResponse = event.data
        setRoomState((currentState) => {
          if (!currentState) return currentState
          const nextHostUserUuid = leaveResponse.newHostUserUuid ?? currentState.hostUserUuid
          const remainingParticipants = currentState.participants
            .filter((participant) => participant.userUuid !== leaveResponse.userUuid)
            .map((participant) => ({
              ...participant,
              host: participant.userUuid === nextHostUserUuid,
            }))
          const nextMe = currentState.me
            ? remainingParticipants.find(
                (participant) => participant.userUuid === currentState.me?.userUuid,
              ) ?? null
            : null

          return {
            ...currentState,
            hostUserUuid: nextHostUserUuid,
            me: nextMe,
            participants: remainingParticipants,
            updatedAt: event.occurredAt,
          }
        })
        setRemoteCursors((currentCursors) => {
          const nextCursors = { ...currentCursors }
          delete nextCursors[leaveResponse.userUuid]
          return nextCursors
        })
        return
      }

      if (event.type === 'HOST_CHANGED') {
        if (!isLeaveResponse(event.data) || !event.data.newHostUserUuid) return
        const newHostUserUuid = event.data.newHostUserUuid
        setRoomState((currentState) => {
          if (!currentState) return currentState
          const participants = currentState.participants.map((participant) => ({
            ...participant,
            host: participant.userUuid === newHostUserUuid,
          }))

          return {
            ...currentState,
            hostUserUuid: newHostUserUuid,
            me: currentState.me
              ? participants.find((participant) => participant.userUuid === currentState.me?.userUuid) ??
                currentState.me
              : null,
            participants,
            updatedAt: event.occurredAt,
          }
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
        const unappliedOperations = getUnappliedOperations(appliedOperations.operations)
        const nextRevision = Math.max(revisionRef.current, appliedOperations.revision)
        revisionRef.current = nextRevision
        recordConfirmedOperations(appliedOperations.operations)
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
          if (acceptedInFlightOperation || areOperationsConfirmed(inFlightOperations)) {
            clearInFlightTimeout()
            inFlightOperationsRef.current = null
            syncPendingOperationState()
            bumpOperationQueue()
          }
        }

        if (
          unappliedOperations.length === 0 &&
          appliedOperations.revision <= appliedElementsRevisionRef.current
        ) {
          return
        }

        appliedElementsRevisionRef.current = Math.max(
          appliedElementsRevisionRef.current,
          appliedOperations.revision,
        )
        recordAppliedOperations(unappliedOperations)
        setRoomState((currentState) => {
          if (!currentState) return currentState
          const currentRevision = Math.max(currentState.revision, appliedOperations.revision)
          return {
            ...currentState,
            elements: applyOperationsToElements(currentState.elements, unappliedOperations),
            operations: appliedOperations.operations,
            revision: currentRevision,
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
        scheduleRemoteCursorUpdate(cursor)
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
        const revisionConflictDetails = getRevisionConflictDetails(event.data)
        const inFlightOperations = inFlightOperationsRef.current
        clearInFlightTimeout()
        inFlightOperationsRef.current = null

        if (isStaleRevisionMessage(message)) {
          resolveRevisionConflict(revisionConflictDetails, inFlightOperations)
          return
        }

        if (isRetryableStateConflictMessage(message)) {
          resolveRetryableStateConflict(inFlightOperations)
          return
        }

        pendingOperationsRef.current = []
        isResolvingRevisionConflictRef.current = false
        syncPendingOperationState()
        setErrorMessage(message)
        toast.error(message)
        void hydrateRoom({ showLoading: false }).finally(bumpOperationQueue)
      }
    },
    [applyFullState, areOperationsConfirmed, bumpOperationQueue, clearInFlightTimeout, getUnappliedOperations, roomCode, hydrateRoom, recordAppliedOperations, recordConfirmedOperations, resolveRetryableStateConflict, resolveRevisionConflict, router, scheduleRemoteCursorUpdate, syncPendingOperationState, userUuid],
  )

  const realtime = useInfinityRealtimeConnection({
    enabled: Boolean(roomCode && userUuid),
    roomCode,
    onEvent: handleRealtimeEvent,
  })
  const connectionStatus = realtime.connectionStatus
  const requestStateSync = realtime.requestStateSync
  const sendRealtimeOperations = realtime.sendOperations
  const previousConnectionStatusRef = useRef(realtime.connectionStatus)

  useEffect(() => {
    const previousConnectionStatus = previousConnectionStatusRef.current
    const nextConnectionStatus = connectionStatus
    previousConnectionStatusRef.current = nextConnectionStatus

    if (!roomCode || !userUuid) return
    if (nextConnectionStatus !== 'connected') return
    if (previousConnectionStatus === 'connected') return

    const syncRequested = requestStateSync()
    if (!syncRequested) {
      window.setTimeout(() => {
        void hydrateRoom({ showLoading: false })
      }, 0)
    }
  }, [connectionStatus, hydrateRoom, requestStateSync, roomCode, userUuid])

  useEffect(() => {
    let cancelled = false

    void (async () => {
      if (!roomCode || !userUuid) {
        setIsHydrating(false)
        return
      }

      const nextState = await hydrateRoom({ showLoading: true })
      if (!cancelled && nextState) {
        revisionRef.current = nextState.revision
      }
    })()

    return () => {
      cancelled = true
    }
  }, [roomCode, hydrateRoom, userUuid])

  useEffect(() => {
    if (!roomCode || !userUuid) return
    if (roomState) return
    if (connectionStatus === 'idle' || connectionStatus === 'connecting') return

    if (connectionStatus === 'connected' || connectionStatus === 'reconnecting') return

    const message = '무한 캔버스 방 상태를 불러오지 못했어요.'
    window.setTimeout(() => {
      setErrorMessage(message)
      setIsHydrating(false)
    }, 0)
  }, [connectionStatus, roomCode, roomState, userUuid])

  const sendOperations = useCallback(
    (operations: InfiniteCanvasOperationRequest[]) => {
      if (operations.length === 0) return false

      pendingOperationsRef.current = [...pendingOperationsRef.current, ...operations]
      syncPendingOperationState()
      bumpOperationQueue()
      return true
    },
    [bumpOperationQueue, syncPendingOperationState],
  )

  useEffect(() => {
    if (isResolvingRevisionConflictRef.current) return
    if (connectionStatus !== 'connected') return
    if (inFlightOperationsRef.current) return
    if (pendingOperationsRef.current.length === 0) return

    const compactedPendingOperations = compactPendingOperations(pendingOperationsRef.current)
    const pendingOperations = compactedPendingOperations.slice(0, MAX_OPERATIONS_PER_BATCH)
    pendingOperationsRef.current = compactedPendingOperations.slice(MAX_OPERATIONS_PER_BATCH)
    const sent = sendRealtimeOperations({
      baseRevision: revisionRef.current,
      operations: pendingOperations,
    })
    if (!sent) {
      pendingOperationsRef.current = [...pendingOperations, ...pendingOperationsRef.current]
      syncPendingOperationState()
      return
    }
    inFlightOperationsRef.current = pendingOperations
    clearInFlightTimeout()
    inFlightTimeoutRef.current = window.setTimeout(() => {
      requeueInFlightOperations()
    }, IN_FLIGHT_OPERATION_TIMEOUT_MS)
    syncPendingOperationState()
    bumpOperationQueue()
  }, [bumpOperationQueue, clearInFlightTimeout, connectionStatus, operationQueueVersion, requeueInFlightOperations, sendRealtimeOperations, syncPendingOperationState])

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

  const updateMyColor = useCallback(
    async (color: string) => {
      const normalizedColor = color.trim()
      if (!roomCode || !normalizedColor || isUpdatingProfile) return false

      setIsUpdatingProfile(true)
      setErrorMessage(null)
      try {
        const updatedParticipant = await patchInfiniteCanvasParticipantColor(roomCode, {
          color: normalizedColor,
        })
        setRoomState((currentState) => {
          if (!currentState) return currentState
          return {
            ...currentState,
            me:
              currentState.me?.userUuid === updatedParticipant.userUuid
                ? updatedParticipant
                : currentState.me,
            participants: currentState.participants.map((participant) =>
              participant.userUuid === updatedParticipant.userUuid
                ? updatedParticipant
                : participant,
            ),
            updatedAt: new Date().toISOString(),
          }
        })
        toast.success('참여자 색상을 변경했어요.')
        return true
      } catch (caughtError) {
        const message =
          caughtError instanceof ApiError
            ? caughtError.message
            : '참여자 색상 변경에 실패했어요.'
        setErrorMessage(message)
        toast.error(message)
        return false
      } finally {
        setIsUpdatingProfile(false)
      }
    },
    [isUpdatingProfile, roomCode],
  )

  const leaveCanvas = useCallback(async () => {
    if (!roomCode) return

    try {
      await deleteInfiniteCanvasParticipantMe(roomCode)
    } catch (caughtError) {
      if (!(caughtError instanceof HTTPError) || caughtError.response.status !== 404) {
        throw caughtError
      }
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
    connectionStatus,
    isSavingOutput,
    isUpdatingProfile,
    me: roomState?.me ?? null,
    participants: roomState?.participants ?? [],
    participantsByUserUuid,
    elements: roomState?.elements ?? [],
    operations: roomState?.operations ?? [],
    locks: roomState?.locks ?? {},
    remoteCursors,
    hasPendingOperations,
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
    updateMyColor,
  } as const
}
