'use client'

import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react'
import type Konva from 'konva'
import { Camera, Copy, Link2, LogOut } from 'lucide-react'
import { toast } from 'sonner'
import { useInfinityAiSticker, useInfinityDrawing, type useInfinityCanvasRoom } from '../hooks'
import { isInfinityObject, toInfinityObjects, type InfinityObject } from '..'

import { INFINITY_CANVAS_BACKGROUND_LAYER_ID, InfinityCanvasStage, type InfinityLockedElementView, type InfinityRemoteDraftObjectView, type InfinityRemoteCursorView } from './InfinityCanvasStage'
import {
  InfinityCaptureOverlay,
  type InfinityCaptureRatio,
  type InfinityCaptureRect,
} from './InfinityCaptureOverlay'
import { InfinityParticipantsPanel } from './InfinityParticipantsPanel'
import { InfinityPrintRevealOverlay } from './InfinityPrintRevealOverlay'
import { InfinityAiStickerModal } from './InfinityAiStickerModal'
import { InfinityTextEditor } from './InfinityTextEditor'
import { InfinityToolPanel } from './InfinityToolPanel'

type InfinityCanvasRoom = ReturnType<typeof useInfinityCanvasRoom>
const CURSOR_SEND_INTERVAL_MS = 80
const DRAFT_SEND_INTERVAL_MS = 50
const CURSOR_MIN_DISTANCE = 3
const REMOTE_DRAFT_RETENTION_MS = 3500
const REMOTE_DRAFT_CONFIRMED_RETENTION_MS = 650
const REMOTE_DRAFT_EXPIRY_REFRESH_THRESHOLD_MS = REMOTE_DRAFT_RETENTION_MS / 2

interface InfinityStageViewProps {
  room: InfinityCanvasRoom
}

interface StageSize {
  width: number
  height: number
}

interface RetainedRemoteDraft {
  draft: InfinityRemoteDraftObjectView
  expiresAt: number
  visibleSince: number | null
}

function createObjectMap(objects: ReturnType<typeof toInfinityObjects>) {
  return new Map(objects.map((object) => [object.id, object]))
}

function objectMapValues(objectMap: Map<string, InfinityObject>) {
  return [...objectMap.values()]
}

const objectSignatureCache = new WeakMap<InfinityObject, string>()

function getInfinityObjectSignature(object: InfinityObject) {
  const cachedSignature = objectSignatureCache.get(object)
  if (cachedSignature) return cachedSignature

  const signature = JSON.stringify(object)
  objectSignatureCache.set(object, signature)
  return signature
}

function areInfinityObjectsEqual(firstObject: InfinityObject, secondObject: InfinityObject) {
  if (firstObject === secondObject) return true
  return getInfinityObjectSignature(firstObject) === getInfinityObjectSignature(secondObject)
}

function areInfinityObjectListsEqual(firstObjects: InfinityObject[], secondObjects: InfinityObject[]) {
  if (firstObjects.length !== secondObjects.length) return false

  const secondObjectMap = createObjectMap(secondObjects)
  return firstObjects.every((firstObject) => {
    const secondObject = secondObjectMap.get(firstObject.id)
    if (!secondObject) return false
    return areInfinityObjectsEqual(firstObject, secondObject)
  })
}

function mergeServerObjectsWithLocalPending({
  previousServerObjects,
  serverObjects,
  localObjects,
}: {
  previousServerObjects: Map<string, InfinityObject>
  serverObjects: InfinityObject[]
  localObjects: InfinityObject[]
}) {
  const serverObjectMap = createObjectMap(serverObjects)
  const mergedObjectMap = createObjectMap(serverObjects)

  for (const localObject of localObjects) {
    const serverObject = serverObjectMap.get(localObject.id)
    if (!serverObject) {
      mergedObjectMap.set(localObject.id, localObject)
      continue
    }

    const previousServerObject = previousServerObjects.get(localObject.id)
    const localObjectChangedFromPreviousServer =
      !previousServerObject ||
      !areInfinityObjectsEqual(previousServerObject, localObject)

    if (
      localObjectChangedFromPreviousServer &&
      !areInfinityObjectsEqual(serverObject, localObject)
    ) {
      mergedObjectMap.set(localObject.id, localObject)
    }
  }

  return objectMapValues(mergedObjectMap)
}

function getPayloadNickname(payload: Record<string, unknown> | null) {
  const nickname = payload?.nickname
  if (typeof nickname !== 'string') return null
  const trimmedNickname = nickname.trim()
  return trimmedNickname.length > 0 ? trimmedNickname : null
}

function getPayloadColor(payload: Record<string, unknown> | null) {
  const color = payload?.color
  if (typeof color !== 'string') return null
  const trimmedColor = color.trim()
  return trimmedColor.length > 0 ? trimmedColor : null
}

function getDisplayNickname(
  participant: { nickname: string } | null | undefined,
  payload: Record<string, unknown> | null,
) {
  const payloadNickname = getPayloadNickname(payload)
  const participantNickname = participant?.nickname?.trim()
  if (participantNickname && !participantNickname.startsWith('참여자-')) {
    return participantNickname
  }

  return payloadNickname ?? participantNickname ?? '참여자'
}

function getPayloadDraftObjects(payload: Record<string, unknown> | null) {
  const draftObjects = payload?.draftObjects
  if (Array.isArray(draftObjects)) {
    return draftObjects.filter(isInfinityObject)
  }

  const draftObject = payload?.draftObject
  return isInfinityObject(draftObject) ? [draftObject] : []
}

function hashUserUuid(userUuid: string) {
  let hash = 0
  for (let index = 0; index < userUuid.length; index++) {
    hash = (hash * 31 + userUuid.charCodeAt(index)) | 0
  }
  return Math.abs(hash)
}

function getDraftObjectSignature(object: InfinityObject) {
  if (object.type === 'line') {
    const lastPoint = object.points.at(-1)
    return [
      object.id,
      object.type,
      object.color,
      object.strokeWidth,
      object.points.length,
      lastPoint?.x.toFixed(1) ?? '',
      lastPoint?.y.toFixed(1) ?? '',
      object.isEraser ? 'eraser' : 'pen',
    ].join(':')
  }

  if (object.type === 'text') {
    return [
      object.id,
      object.type,
      object.x,
      object.y,
      object.text,
      object.fontSize,
      object.color,
      object.fontFamily ?? '',
    ].join(':')
  }

  if (object.type === 'fill') {
    return [object.id, object.type, object.x, object.y, object.width, object.height, object.color].join(':')
  }

  if (object.type === 'image') {
    return [
      object.id,
      object.type,
      object.x,
      object.y,
      object.width,
      object.height,
      object.src,
      object.rotation ?? 0,
    ].join(':')
  }

  return [
    object.id,
    object.type,
    object.x,
    object.y,
    object.width,
    object.height,
    object.color,
    object.strokeWidth,
    object.fill ?? '',
    object.rotation ?? 0,
  ].join(':')
}

function getDraftObjectsSignature(draftObjects: InfinityObject[]) {
  return draftObjects.map(getDraftObjectSignature).join('|')
}

function getCursorDistance(firstCursor: { x: number; y: number }, secondCursor: { x: number; y: number }) {
  const distanceX = firstCursor.x - secondCursor.x
  const distanceY = firstCursor.y - secondCursor.y
  return Math.sqrt(distanceX * distanceX + distanceY * distanceY)
}

async function createStageBlob(stage: Konva.Stage, rect: InfinityCaptureRect): Promise<Blob> {
  const cloneContainer = document.createElement('div')
  cloneContainer.style.position = 'fixed'
  cloneContainer.style.left = '-100000px'
  cloneContainer.style.top = '-100000px'
  cloneContainer.style.width = `${stage.width()}px`
  cloneContainer.style.height = `${stage.height()}px`
  document.body.appendChild(cloneContainer)

  const clonedStage = stage.clone({ container: cloneContainer }) as Konva.Stage
  clonedStage.findOne(`#${INFINITY_CANVAS_BACKGROUND_LAYER_ID}`)?.destroy()
  clonedStage.draw()

  let blob: Blob | null = null
  try {
    blob = (await clonedStage.toBlob({
      x: rect.x,
      y: rect.y,
      width: rect.width,
      height: rect.height,
      pixelRatio: 2,
      mimeType: 'image/png',
    })) as Blob | null
  } finally {
    clonedStage.destroy()
    cloneContainer.remove()
  }

  if (!blob) {
    throw new Error('empty-canvas-export')
  }

  return blob
}

export function InfinityStageView({ room }: InfinityStageViewProps) {
  const acquireLock = room.acquireLock
  const releaseLock = room.releaseLock
  const saveOutput = room.saveOutput
  const sendRoomCursor = room.sendCursor
  const sendRoomOperations = room.sendOperations
  const stageRef = useRef<Konva.Stage>(null)
  const currentPenLineRef = useRef<Konva.Line>(null)
  const currentEraserLineRef = useRef<Konva.Line>(null)
  const previewRectRef = useRef<Konva.Rect>(null)
  const previewEllipseRef = useRef<Konva.Ellipse>(null)
  const cursorPreviewRef = useRef<Konva.Circle>(null)
  const selectionBoxRef = useRef<Konva.Rect>(null)
  const previousServerObjectsRef = useRef(createObjectMap([]))
  const appliedServerRevisionRef = useRef<number | null>(null)
  const lastCursorSentAtRef = useRef(0)
  const lastDraftCursorSentAtRef = useRef(0)
  const lastSentCursorRef = useRef<{ x: number; y: number; zoom: number } | null>(null)
  const lastSentDraftSignatureRef = useRef('')
  const latestCursorRef = useRef<{ x: number; y: number; zoom: number } | null>(null)
  const draftObjectsRef = useRef<InfinityObject[]>([])
  const lastFinishedDraftsRef = useRef<InfinityObject[]>([])
  const draftClearTimeoutRef = useRef<number | null>(null)
  const previousSelectedIdsRef = useRef<string[]>([])
  const selectedIdsForEditRef = useRef<Set<string>>(new Set())
  const requestedLockIdsRef = useRef<Set<string>>(new Set())
  const [isCaptureMode, setIsCaptureMode] = useState(false)
  const [copiedInviteTarget, setCopiedInviteTarget] = useState<'link' | 'code' | null>(null)
  const [retainedRemoteDrafts, setRetainedRemoteDrafts] = useState<Record<string, RetainedRemoteDraft>>({})
  const [printRevealPreviewUrl, setPrintRevealPreviewUrl] = useState<string | null>(null)

  const nodeRefs = useMemo(
    () => ({
      currentPenLineRef,
      currentEraserLineRef,
      previewRectRef,
      previewEllipseRef,
      cursorPreviewRef,
      selectionBoxRef,
    }),
    [],
  )

  const getForeignLock = useCallback(
    (elementId: string) => {
      const lock = room.locks[elementId]
      if (!lock || lock.userUuid === room.myUserUuid) return null
      return lock
    },
    [room.locks, room.myUserUuid],
  )

  const getMyLock = useCallback(
    (elementId: string) => {
      const lock = room.locks[elementId]
      if (!lock || lock.userUuid !== room.myUserUuid) return null
      return lock
    },
    [room.locks, room.myUserUuid],
  )

  const handleBlockedObjectEdit = useCallback(
    (elementId: string) => {
      const lock = getForeignLock(elementId)
      if (!lock) {
        toast.info('요소 편집 권한을 확인 중이에요. 잠시 후 다시 시도해 주세요.')
        return
      }
      const participant = lock ? room.participantsByUserUuid[lock.userUuid] : null
      toast.info(`${participant?.nickname ?? '다른 참여자'}가 편집 중인 요소예요.`)
    },
    [getForeignLock, room.participantsByUserUuid],
  )

  const createCursorPayload = useCallback(
    (draftObjects: InfinityObject[]) => ({
      color: room.me?.color ?? '#5b8fd8',
      nickname: room.me?.nickname ?? null,
      draftObject: draftObjects[0] ?? null,
      draftObjects,
    }),
    [room.me?.color, room.me?.nickname],
  )

  const sendCursor = useCallback(
    (cursor: { x: number; y: number; zoom: number }, options: { force?: boolean } = {}) => {
      const now = Date.now()
      const draftObjects = draftObjectsRef.current
      const hasDraftObjects = draftObjects.length > 0
      const draftSignature = hasDraftObjects ? getDraftObjectsSignature(draftObjects) : ''
      const previousCursor = lastSentCursorRef.current
      const hasMeaningfulCursorMove =
        !previousCursor ||
        previousCursor.zoom !== cursor.zoom ||
        getCursorDistance(previousCursor, cursor) >= CURSOR_MIN_DISTANCE
      const hasDraftChanged = draftSignature !== lastSentDraftSignatureRef.current

      if (!options.force && !hasMeaningfulCursorMove && !hasDraftChanged) return

      const minInterval = hasDraftObjects ? DRAFT_SEND_INTERVAL_MS : CURSOR_SEND_INTERVAL_MS
      if (!options.force && now - lastCursorSentAtRef.current < minInterval) return
      if (hasDraftObjects && !options.force && now - lastDraftCursorSentAtRef.current < DRAFT_SEND_INTERVAL_MS) return

      lastCursorSentAtRef.current = now
      lastSentCursorRef.current = cursor
      lastSentDraftSignatureRef.current = draftSignature
      if (hasDraftObjects) {
        lastDraftCursorSentAtRef.current = now
      }
      sendRoomCursor({
        x: cursor.x,
        y: cursor.y,
        zoom: cursor.zoom,
        payload: createCursorPayload(draftObjects),
      })
    },
    [createCursorPayload, sendRoomCursor],
  )

  const handleLocalOperations = useCallback(
    (operations: Parameters<typeof sendRoomOperations>[0]) => {
      const sent = sendRoomOperations(operations)
      if (!sent) {
        toast.error('서버 연결 후 편집할 수 있어요.')
      }
    },
    [sendRoomOperations],
  )

  const handleDraftObjectChange = useCallback(
    (draftObject: InfinityObject | InfinityObject[] | null) => {
      if (draftClearTimeoutRef.current) {
        window.clearTimeout(draftClearTimeoutRef.current)
        draftClearTimeoutRef.current = null
      }
      if (draftObject !== null) {
        const draftObjects = Array.isArray(draftObject) ? draftObject : [draftObject]
        draftObjectsRef.current = draftObjects
        lastFinishedDraftsRef.current = draftObjects
        return
      }
      draftObjectsRef.current = lastFinishedDraftsRef.current
      const latestCursor = latestCursorRef.current
      if (!latestCursor) return
      draftClearTimeoutRef.current = window.setTimeout(() => {
        draftClearTimeoutRef.current = null
        draftObjectsRef.current = []
        lastFinishedDraftsRef.current = []
        sendCursor(latestCursor, { force: true })
      }, 1800)
    },
    [sendCursor],
  )

  useEffect(
    () => () => {
      if (draftClearTimeoutRef.current) {
        window.clearTimeout(draftClearTimeoutRef.current)
      }
    },
    [],
  )

  useEffect(
    () => () => {
      if (printRevealPreviewUrl) {
        URL.revokeObjectURL(printRevealPreviewUrl)
      }
    },
    [printRevealPreviewUrl],
  )

  const drawing = useInfinityDrawing(stageRef, nodeRefs, {
    canSelectObject: (elementId) => getForeignLock(elementId) === null,
    canEditObject: (elementId) =>
      getForeignLock(elementId) === null &&
      (getMyLock(elementId) !== null || selectedIdsForEditRef.current.has(elementId)),
    onBlockedObjectEdit: handleBlockedObjectEdit,
    onDraftObjectChange: handleDraftObjectChange,
    onLocalOperations: handleLocalOperations,
  })

  const aiSticker = useInfinityAiSticker({
    roomCode: room.roomCode ?? '',
    stageRef,
    scaleRef: drawing.viewport.scaleRef,
    stagePosRef: drawing.viewport.stagePosRef,
    addObject: drawing.addObject,
  })

  const containerRef = useRef<HTMLDivElement>(null)
  const [stageSize, setStageSize] = useState<StageSize | null>(null)
  const centerInitialViewportRef = useRef(drawing.viewport.centerInitialViewport)

  useLayoutEffect(() => {
    selectedIdsForEditRef.current = new Set(drawing.selectedIds)
  }, [drawing.selectedIds])

  useLayoutEffect(() => {
    centerInitialViewportRef.current = drawing.viewport.centerInitialViewport
  }, [drawing.viewport.centerInitialViewport])

  const serverObjects = useMemo(() => toInfinityObjects(room.elements), [room.elements])

  const participantIdentityIndexes = useMemo(() => {
    const sortedParticipants = [...room.participants].sort((first, second) =>
      first.joinedAt.localeCompare(second.joinedAt),
    )
    return Object.fromEntries(
      sortedParticipants.map((participant, participantIndex) => [
        participant.userUuid,
        participantIndex,
      ]),
    ) as Record<string, number>
  }, [room.participants])

  const getParticipantIdentityIndex = useCallback(
    (userUuid: string) => participantIdentityIndexes[userUuid] ?? hashUserUuid(userUuid),
    [participantIdentityIndexes],
  )

  const lockedElementIds = useMemo(() => {
    const lockedIds = Object.values(room.locks)
      .filter((lock) => lock.userUuid !== room.myUserUuid)
      .map((lock) => lock.elementId)
    return new Set(lockedIds)
  }, [room.locks, room.myUserUuid])

  const selectedElementIds = useMemo(() => new Set(drawing.selectedIds), [drawing.selectedIds])

  const editingBlockedElementIds = useMemo(() => {
    const blockedIds = new Set(lockedElementIds)
    for (const object of drawing.objects) {
      const lock = room.locks[object.id]
      if (lock?.userUuid === room.myUserUuid) continue
      if (!lock && selectedElementIds.has(object.id)) continue
      blockedIds.add(object.id)
    }
    return blockedIds
  }, [drawing.objects, lockedElementIds, room.locks, room.myUserUuid, selectedElementIds])

  const lockedElements: InfinityLockedElementView[] = useMemo(
    () =>
      Object.values(room.locks)
        .filter((lock) => lock.userUuid !== room.myUserUuid)
        .map((lock) => {
          const participant = room.participantsByUserUuid[lock.userUuid]
          return {
            elementId: lock.elementId,
            userUuid: lock.userUuid,
            nickname: participant?.nickname ?? '참여자',
            color: participant?.color ?? '#5b8fd8',
            identityIndex: getParticipantIdentityIndex(lock.userUuid),
          }
        }),
    [getParticipantIdentityIndex, room.locks, room.myUserUuid, room.participantsByUserUuid],
  )

  const remoteCursorValues = useMemo(
    () => Object.values(room.remoteCursors),
    [room.remoteCursors],
  )

  const remoteCursors: InfinityRemoteCursorView[] = useMemo(
    () =>
      remoteCursorValues
        .filter((cursor) => cursor.userUuid !== room.myUserUuid && cursor.x !== null && cursor.y !== null)
        .map((cursor) => {
          const participant = room.participantsByUserUuid[cursor.userUuid]
          return {
            userUuid: cursor.userUuid,
            nickname: getDisplayNickname(participant, cursor.payload),
            color: participant?.color ?? getPayloadColor(cursor.payload) ?? '#5b8fd8',
            x: cursor.x ?? 0,
            y: cursor.y ?? 0,
            identityIndex: getParticipantIdentityIndex(cursor.userUuid),
          }
        }),
    [getParticipantIdentityIndex, remoteCursorValues, room.myUserUuid, room.participantsByUserUuid],
  )

  const visibleObjectIds = useMemo(
    () => new Set(drawing.objects.map((object) => object.id)),
    [drawing.objects],
  )

  const liveRemoteDraftObjects: InfinityRemoteDraftObjectView[] = useMemo(
    () =>
      remoteCursorValues
        .filter((cursor) => cursor.userUuid !== room.myUserUuid)
        .map((cursor) => {
          const draftObjects = getPayloadDraftObjects(cursor.payload)
          if (draftObjects.length === 0) return null
          const participant = room.participantsByUserUuid[cursor.userUuid]
          return draftObjects
            .map((draftObject) => ({
              userUuid: cursor.userUuid,
              nickname: getDisplayNickname(participant, cursor.payload),
              color: participant?.color ?? getPayloadColor(cursor.payload) ?? '#5b8fd8',
              identityIndex: getParticipantIdentityIndex(cursor.userUuid),
              object: draftObject,
            }))
        })
        .flat()
        .filter((draftObject): draftObject is InfinityRemoteDraftObjectView => draftObject !== null),
    [
      getParticipantIdentityIndex,
      remoteCursorValues,
      room.myUserUuid,
      room.participantsByUserUuid,
    ],
  )

  useEffect(() => {
    const animationFrameId = window.requestAnimationFrame(() => {
      const now = Date.now()
      setRetainedRemoteDrafts((currentDrafts) => {
        const nextDrafts: Record<string, RetainedRemoteDraft> = {}

        for (const [key, retainedDraft] of Object.entries(currentDrafts)) {
          const isVisible = visibleObjectIds.has(retainedDraft.draft.object.id)
          const visibleSince = isVisible
            ? retainedDraft.visibleSince ?? now
            : null
          const confirmedLongEnough =
            visibleSince !== null && now - visibleSince >= REMOTE_DRAFT_CONFIRMED_RETENTION_MS

          if (retainedDraft.expiresAt > now && !confirmedLongEnough) {
            nextDrafts[key] = { ...retainedDraft, visibleSince }
          }
        }

        for (const draft of liveRemoteDraftObjects) {
          const key = `${draft.userUuid}:${draft.object.id}`
          const isVisible = visibleObjectIds.has(draft.object.id)
          const previousDraft = nextDrafts[key]
          const canReusePreviousDraft =
            previousDraft &&
            previousDraft.expiresAt - now > REMOTE_DRAFT_EXPIRY_REFRESH_THRESHOLD_MS &&
            areInfinityObjectsEqual(previousDraft.draft.object, draft.object)

          if (canReusePreviousDraft) {
            nextDrafts[key] = {
              ...previousDraft,
              visibleSince: isVisible ? previousDraft.visibleSince ?? now : null,
            }
            continue
          }

          nextDrafts[key] = {
            draft,
            expiresAt: now + REMOTE_DRAFT_RETENTION_MS,
            visibleSince: isVisible ? previousDraft?.visibleSince ?? now : null,
          }
        }

        const currentKeys = Object.keys(currentDrafts)
        const nextKeys = Object.keys(nextDrafts)
        const changed =
          currentKeys.length !== nextKeys.length ||
          nextKeys.some((key) => {
            const currentDraft = currentDrafts[key]
            const nextDraft = nextDrafts[key]
            return (
              !currentDraft ||
              !nextDraft ||
              currentDraft.expiresAt !== nextDraft.expiresAt ||
              currentDraft.visibleSince !== nextDraft.visibleSince ||
              !areInfinityObjectsEqual(currentDraft.draft.object, nextDraft.draft.object)
            )
          })

        return changed ? nextDrafts : currentDrafts
      })
    })

    return () => window.cancelAnimationFrame(animationFrameId)
  }, [liveRemoteDraftObjects, visibleObjectIds])

  useEffect(() => {
    const retainedDrafts = Object.values(retainedRemoteDrafts)
    if (retainedDrafts.length === 0) return
    const now = Date.now()
    const nextExpiry = Math.min(...retainedDrafts.map((retainedDraft) => retainedDraft.expiresAt))
    const timeoutId = window.setTimeout(() => {
      setRetainedRemoteDrafts((currentDrafts) => {
        const currentTime = Date.now()
        const nextDrafts = Object.fromEntries(
          Object.entries(currentDrafts).filter(
            ([, retainedDraft]) =>
              retainedDraft.expiresAt > currentTime &&
              (retainedDraft.visibleSince === null ||
                currentTime - retainedDraft.visibleSince < REMOTE_DRAFT_CONFIRMED_RETENTION_MS),
          ),
        )
        return Object.keys(nextDrafts).length === Object.keys(currentDrafts).length
          ? currentDrafts
          : nextDrafts
      })
    }, Math.max(nextExpiry - now, 0))

    return () => window.clearTimeout(timeoutId)
  }, [retainedRemoteDrafts, visibleObjectIds])

  const remoteDraftObjects: InfinityRemoteDraftObjectView[] = useMemo(
    () =>
      Object.values(retainedRemoteDrafts)
        .map((retainedDraft) => retainedDraft.draft),
    [retainedRemoteDrafts],
  )

  const applyMeasuredStageSize = useCallback((width: number, height: number) => {
    if (!Number.isFinite(width) || !Number.isFinite(height)) return

    const nextWidth = Math.max(1, Math.round(width))
    const nextHeight = Math.max(1, Math.round(height))

    setStageSize((currentSize) =>
      currentSize?.width === nextWidth && currentSize.height === nextHeight
        ? currentSize
        : { width: nextWidth, height: nextHeight },
    )
    centerInitialViewportRef.current(nextWidth, nextHeight)
  }, [])

  useLayoutEffect(() => {
    const container = containerRef.current
    if (!container) return

    const bounds = container.getBoundingClientRect()
    applyMeasuredStageSize(bounds.width, bounds.height)

    const observer = new ResizeObserver((entries) => {
      const entry = entries[0]
      if (!entry) return
      const { width, height } = entry.contentRect
      applyMeasuredStageSize(width, height)
    })
    observer.observe(container)
    return () => observer.disconnect()
  }, [applyMeasuredStageSize])

  useEffect(() => {
    const serverRevision = room.revision
    const isSameServerRevision = appliedServerRevisionRef.current === serverRevision
    const isStaleEmptySnapshot =
      isSameServerRevision && serverObjects.length === 0 && drawing.objects.length > 0

    if (isStaleEmptySnapshot) return

    const previousServerObjects = previousServerObjectsRef.current
    const nextServerObjectMap = createObjectMap(serverObjects)
    const isSameServerObjects = areInfinityObjectListsEqual(
      objectMapValues(previousServerObjects),
      serverObjects,
    )
    if (isSameServerObjects) {
      appliedServerRevisionRef.current = serverRevision
      return
    }

    if (areInfinityObjectListsEqual(drawing.objects, serverObjects)) {
      appliedServerRevisionRef.current = serverRevision
      previousServerObjectsRef.current = nextServerObjectMap
      return
    }

    const isInitialServerApply = appliedServerRevisionRef.current === null
    const nextObjectsBase =
      room.hasPendingOperations && !isInitialServerApply
        ? mergeServerObjectsWithLocalPending({
            previousServerObjects,
            serverObjects,
            localObjects: drawing.objects,
          })
        : serverObjects
    const nextObjects = nextObjectsBase
    const nextObjectIds = new Set(nextObjects.map((object) => object.id))
    const selectedIds = drawing.selectedIds.filter((selectedId) =>
      nextObjectIds.has(selectedId),
    )

    appliedServerRevisionRef.current = serverRevision
    previousServerObjectsRef.current = nextServerObjectMap
    if (isInitialServerApply) {
      drawing.replaceObjectsFromServer(nextObjects, selectedIds)
    } else {
      drawing.syncObjectsFromServer(nextObjects, selectedIds)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [room.hasPendingOperations, room.revision, serverObjects])

  useEffect(() => {
    const previousSelectedIds = previousSelectedIdsRef.current
    const nextSelectedIds = drawing.selectedIds
    const removedIds = previousSelectedIds.filter((selectedId) => !nextSelectedIds.includes(selectedId))

    for (const elementId of nextSelectedIds) {
      const lock = room.locks[elementId]
      if (!lock) {
        if (!requestedLockIdsRef.current.has(elementId)) {
          requestedLockIdsRef.current.add(elementId)
          acquireLock(elementId)
        }
        continue
      }

      requestedLockIdsRef.current.delete(elementId)
    }

    for (const elementId of removedIds) {
      requestedLockIdsRef.current.delete(elementId)
      const lock = room.locks[elementId]
      if (lock?.userUuid === room.myUserUuid) {
        releaseLock(elementId)
      }
    }

    previousSelectedIdsRef.current = nextSelectedIds
  }, [
    acquireLock,
    drawing.selectedIds,
    releaseLock,
    room.locks,
    room.myUserUuid,
  ])

  const handleCursorMove = useCallback(
    (cursor: { x: number; y: number; zoom: number }) => {
      latestCursorRef.current = cursor
      sendCursor(cursor)
    },
    [sendCursor],
  )

  const copyInviteText = useCallback((target: 'link' | 'code', text: string) => {
    void navigator.clipboard.writeText(text).then(() => {
      setCopiedInviteTarget(target)
      window.setTimeout(() => setCopiedInviteTarget(null), 1400)
      toast.success(target === 'link' ? '초대 링크를 복사했어요.' : '초대코드를 복사했어요.')
    })
  }, [])

  const handleCopyInviteCode = useCallback(() => {
    if (!room.inviteCode) return
    copyInviteText('code', room.inviteCode)
  }, [copyInviteText, room.inviteCode])

  const handleCopyInviteLink = useCallback(() => {
    if (typeof window === 'undefined') return
    copyInviteText('link', window.location.href)
  }, [copyInviteText])

  const handleCapture = useCallback(
    async (rect: InfinityCaptureRect, ratio: InfinityCaptureRatio) => {
      const stage = stageRef.current
      if (!stage) return

      try {
        const blob = await createStageBlob(stage, rect)
        const previewUrl = URL.createObjectURL(blob)
        setPrintRevealPreviewUrl((previousPreviewUrl) => {
          if (previousPreviewUrl) URL.revokeObjectURL(previousPreviewUrl)
          return previewUrl
        })
        setIsCaptureMode(false)
        const output = await saveOutput(blob, {
          roomCode: room.roomCode,
          revision: room.revision,
          ratio,
          rect,
        })
        if (!output) {
          setPrintRevealPreviewUrl((currentPreviewUrl) => {
            if (currentPreviewUrl) URL.revokeObjectURL(currentPreviewUrl)
            return null
          })
        }
      } catch {
        toast.error('선택한 영역을 이미지로 만들지 못했어요.')
      }
    },
    [room.revision, room.roomCode, saveOutput],
  )

  const releaseSelectedLocks = useCallback(
    (elementIds: string[]) => {
      elementIds.forEach((elementId) => {
        releaseLock(elementId)
      })
    },
    [releaseLock],
  )

  return (
    <div className="fixed inset-0 h-dvh w-dvw overflow-hidden bg-canvas-background">
      <InfinityToolPanel
        drawing={drawing}
        isAiStickerOpen={aiSticker.isOpen}
        onAiStickerClick={aiSticker.open}
      />
      <InfinityAiStickerModal
        open={aiSticker.isOpen}
        loading={aiSticker.isCreating}
        previewSticker={aiSticker.previewSticker}
        onClose={aiSticker.close}
        onSubmit={aiSticker.createSticker}
        onAttach={aiSticker.attachPreviewSticker}
        onClearPreview={aiSticker.clearPreviewSticker}
      />

      <div ref={containerRef} className="absolute inset-0 min-w-0 overflow-hidden">
        {stageSize ? (
          <InfinityCanvasStage
            width={stageSize.width}
            height={stageSize.height}
            stageRef={stageRef}
            drawing={drawing}
            currentPenLineRef={currentPenLineRef}
            currentEraserLineRef={currentEraserLineRef}
            previewRectRef={previewRectRef}
            previewEllipseRef={previewEllipseRef}
            cursorPreviewRef={cursorPreviewRef}
            selectionBoxRef={selectionBoxRef}
            isShiftDown={drawing.isShiftDown}
            lockedElements={lockedElements}
            lockedElementIds={editingBlockedElementIds}
            remoteDraftObjects={remoteDraftObjects}
            remoteCursors={remoteCursors}
            onCursorMove={handleCursorMove}
            onSelectionInteractionEnd={releaseSelectedLocks}
            onDraftObjectsChange={handleDraftObjectChange}
          />
        ) : (
          <div className="absolute inset-0 bg-canvas-background" aria-hidden />
        )}

        {drawing.textEditor && (
          <InfinityTextEditor
            key={drawing.textEditor.editingId ?? `${drawing.textEditor.x}:${drawing.textEditor.y}`}
            state={drawing.textEditor}
            scaleRef={drawing.viewport.scaleRef}
            stagePosRef={drawing.viewport.stagePosRef}
            editingTool={drawing.tool}
            onCommit={(value) => drawing.commitTextEditor(value)}
            onCancel={drawing.closeTextEditor}
          />
        )}

        {isCaptureMode && (
          <InfinityCaptureOverlay
            isSaving={room.isSavingOutput}
            onCancel={() => setIsCaptureMode(false)}
            onCapture={handleCapture}
          />
        )}

        <InfinityPrintRevealOverlay
          previewUrl={printRevealPreviewUrl}
          isSaving={room.isSavingOutput}
          onDone={() => {
            setPrintRevealPreviewUrl((currentPreviewUrl) => {
              if (currentPreviewUrl) URL.revokeObjectURL(currentPreviewUrl)
              return null
            })
          }}
        />

      </div>

      <section
        className="fixed left-1/2 top-4 z-20 w-[min(326px,calc(100vw-32px))] -translate-x-1/2 overflow-hidden rounded-[26px] border border-white/72 bg-[#3aa7f4] p-2.5 text-white shadow-[0_14px_30px_rgba(46,95,210,0.24),inset_0_1px_0_rgba(255,255,255,0.42)]"
        aria-label="무한 캔버스 초대 공유"
      >
        <div className="pointer-events-none absolute inset-0 bg-white/8" />
        <div className="pointer-events-none absolute -left-8 -top-8 size-24 rounded-full bg-white/18 blur-xl" />
        <div className="relative flex items-center justify-center gap-4">
          <div className="min-w-0 shrink-0 text-left">
            <p className="caption-b text-white/82">초대코드</p>
            <p className="h3-b tracking-[0.08em] drop-shadow-[0_2px_6px_rgba(18,38,130,0.42)]">
              {room.inviteCode ?? '-'}
            </p>
          </div>
          <div className="grid shrink-0 grid-cols-2 gap-1.5">
            <button
              type="button"
              onClick={handleCopyInviteLink}
              className="caption-b inline-flex h-10 items-center justify-center gap-1.5 rounded-full bg-white/92 px-2.5 text-[#2860c8] shadow-[0_6px_14px_rgba(36,72,170,0.16)] transition-transform hover:-translate-y-0.5"
            >
              <Link2 className="size-3.5" aria-hidden />
              {copiedInviteTarget === 'link' ? '복사됨' : '링크'}
            </button>
            <button
              type="button"
              onClick={handleCopyInviteCode}
              className="caption-b inline-flex h-10 items-center justify-center gap-1.5 rounded-full bg-white/92 px-2.5 text-[#2860c8] shadow-[0_6px_14px_rgba(36,72,170,0.16)] transition-transform hover:-translate-y-0.5"
            >
              <Copy className="size-3.5" aria-hidden />
              {copiedInviteTarget === 'code' ? '복사됨' : '코드'}
            </button>
          </div>
        </div>
      </section>

      <InfinityParticipantsPanel
        connectionStatus={room.connectionStatus}
        isUpdatingProfile={room.isUpdatingProfile}
        maxParticipants={room.maxParticipants}
        me={room.me}
        myUserUuid={room.myUserUuid}
        onUpdateMyColor={room.updateMyColor}
        participants={room.participants}
      />

      <div className="fixed bottom-8 right-[calc(7rem+env(safe-area-inset-right))] z-20 flex items-center gap-4">
        <button
          type="button"
          onClick={() => setIsCaptureMode(true)}
          disabled={room.isSavingOutput}
          className="body-b inline-flex h-12 min-w-[132px] items-center justify-center gap-2 rounded-full border border-white/72 bg-[#3aa7f4] px-5 text-white shadow-[0_10px_22px_rgba(46,95,210,0.22),inset_0_1px_0_rgba(255,255,255,0.42)] transition-transform hover:-translate-y-0.5 hover:scale-[1.03] disabled:pointer-events-none disabled:opacity-55"
          aria-label="스크린캡쳐"
        >
          <Camera className="size-4" aria-hidden />
          스크린캡쳐
        </button>
        <button
          type="button"
          onClick={room.leaveCanvas}
          className="body-b inline-flex h-12 min-w-[106px] items-center justify-center gap-2 rounded-full border border-white/72 bg-[#3aa7f4] px-5 text-white shadow-[0_10px_22px_rgba(46,95,210,0.22),inset_0_1px_0_rgba(255,255,255,0.42)] transition-transform hover:-translate-y-0.5 hover:scale-[1.03]"
          aria-label="나가기"
        >
          <LogOut className="size-4" aria-hidden />
          나가기
        </button>
      </div>
    </div>
  )
}
