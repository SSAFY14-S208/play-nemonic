'use client'

import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type Konva from 'konva'
import Image from 'next/image'
import { toast } from 'sonner'
import type { InfiniteCanvasOperationRequest } from '@/shared/types'
import { useInfinityDrawing, type useInfinityCanvasRoom } from '../hooks'
import type { InfinityObject } from '../constants'
import {
  createClientOperationId,
  isInfinityObject,
  stringifyInfinityObject,
  toInfinityObjects,
} from '../infinityObjectUtils'
import { InfinityCanvasStage } from './InfinityCanvasStage'
import type {
  InfinityLockedElementView,
  InfinityRemoteDraftObjectView,
  InfinityRemoteCursorView,
} from './InfinityCanvasStage'
import {
  InfinityCaptureOverlay,
  type InfinityCaptureRatio,
  type InfinityCaptureRect,
} from './InfinityCaptureOverlay'
import { InfinityParticipantsPanel } from './InfinityParticipantsPanel'
import { InfinityTextEditor } from './InfinityTextEditor'
import { InfinityToolPanel } from './InfinityToolPanel'

type InfinityCanvasRoom = ReturnType<typeof useInfinityCanvasRoom>

interface InfinityStageViewProps {
  room: InfinityCanvasRoom
}

function createObjectMap(objects: ReturnType<typeof toInfinityObjects>) {
  return new Map(objects.map((object) => [object.id, object]))
}

function areInfinityObjectListsEqual(firstObjects: InfinityObject[], secondObjects: InfinityObject[]) {
  if (firstObjects.length !== secondObjects.length) return false

  const secondObjectMap = createObjectMap(secondObjects)
  return firstObjects.every((firstObject) => {
    const secondObject = secondObjectMap.get(firstObject.id)
    if (!secondObject) return false
    return stringifyInfinityObject(firstObject) === stringifyInfinityObject(secondObject)
  })
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

function getPayloadDraftObject(payload: Record<string, unknown> | null) {
  const draftObject = payload?.draftObject
  return isInfinityObject(draftObject) ? draftObject : null
}

function hashUserUuid(userUuid: string) {
  let hash = 0
  for (let index = 0; index < userUuid.length; index++) {
    hash = (hash * 31 + userUuid.charCodeAt(index)) | 0
  }
  return Math.abs(hash)
}

async function createStageBlob(stage: Konva.Stage, rect: InfinityCaptureRect): Promise<Blob> {
  const blob = (await stage.toBlob({
    x: rect.x,
    y: rect.y,
    width: rect.width,
    height: rect.height,
    pixelRatio: 2,
    mimeType: 'image/png',
  })) as Blob | null

  if (!blob) {
    throw new Error('empty-canvas-export')
  }

  return blob
}

export function InfinityStageView({ room }: InfinityStageViewProps) {
  const stageRef = useRef<Konva.Stage>(null)
  const currentPenLineRef = useRef<Konva.Line>(null)
  const currentEraserLineRef = useRef<Konva.Line>(null)
  const previewRectRef = useRef<Konva.Rect>(null)
  const previewEllipseRef = useRef<Konva.Ellipse>(null)
  const cursorPreviewRef = useRef<Konva.Circle>(null)
  const selectionBoxRef = useRef<Konva.Rect>(null)
  const previousObjectsRef = useRef(createObjectMap([]))
  const isApplyingRemoteRef = useRef(false)
  const appliedServerRevisionRef = useRef<number | null>(null)
  const lastCursorSentAtRef = useRef(0)
  const latestCursorRef = useRef<{ x: number; y: number; zoom: number } | null>(null)
  const draftObjectRef = useRef<InfinityObject | null>(null)
  const previousSelectedIdsRef = useRef<string[]>([])
  const [isCaptureMode, setIsCaptureMode] = useState(false)

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

  const handleBlockedObjectEdit = useCallback(
    (elementId: string) => {
      const lock = getForeignLock(elementId)
      const participant = lock ? room.participantsByUserUuid[lock.userUuid] : null
      toast.info(`${participant?.nickname ?? '다른 참여자'}가 편집 중인 요소예요.`)
    },
    [getForeignLock, room.participantsByUserUuid],
  )

  const createCursorPayload = useCallback(
    (draftObject: InfinityObject | null) => ({
      color: room.me?.color ?? '#5b8fd8',
      nickname: room.me?.nickname ?? null,
      draftObject,
    }),
    [room.me?.color, room.me?.nickname],
  )

  const sendCursor = useCallback(
    (cursor: { x: number; y: number; zoom: number }, options: { force?: boolean } = {}) => {
      const now = Date.now()
      if (!options.force && now - lastCursorSentAtRef.current < 40) return
      lastCursorSentAtRef.current = now
      room.sendCursor({
        x: cursor.x,
        y: cursor.y,
        zoom: cursor.zoom,
        payload: createCursorPayload(draftObjectRef.current),
      })
    },
    [createCursorPayload, room],
  )

  const handleDraftObjectChange = useCallback(
    (draftObject: InfinityObject | null) => {
      draftObjectRef.current = draftObject
      if (draftObject !== null) return
      const latestCursor = latestCursorRef.current
      if (!latestCursor) return
      sendCursor(latestCursor, { force: true })
    },
    [sendCursor],
  )

  const drawing = useInfinityDrawing(stageRef, nodeRefs, {
    canEditObject: (elementId) => getForeignLock(elementId) === null,
    onBlockedObjectEdit: handleBlockedObjectEdit,
    onDraftObjectChange: handleDraftObjectChange,
  })

  const containerRef = useRef<HTMLDivElement>(null)
  const [stageSize, setStageSize] = useState({ width: 800, height: 600 })

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

  const remoteCursors: InfinityRemoteCursorView[] = useMemo(
    () =>
      Object.values(room.remoteCursors)
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
    [getParticipantIdentityIndex, room.myUserUuid, room.participantsByUserUuid, room.remoteCursors],
  )

  const remoteDraftObjects: InfinityRemoteDraftObjectView[] = useMemo(
    () =>
      Object.values(room.remoteCursors)
        .filter((cursor) => cursor.userUuid !== room.myUserUuid)
        .map((cursor) => {
          const draftObject = getPayloadDraftObject(cursor.payload)
          if (!draftObject) return null
          const participant = room.participantsByUserUuid[cursor.userUuid]
          return {
            userUuid: cursor.userUuid,
            nickname: getDisplayNickname(participant, cursor.payload),
            color: participant?.color ?? getPayloadColor(cursor.payload) ?? '#5b8fd8',
            identityIndex: getParticipantIdentityIndex(cursor.userUuid),
            object: draftObject,
          }
        })
        .filter((draftObject): draftObject is InfinityRemoteDraftObjectView => draftObject !== null),
    [getParticipantIdentityIndex, room.myUserUuid, room.participantsByUserUuid, room.remoteCursors],
  )

  useEffect(() => {
    const container = containerRef.current
    if (!container) return

    const observer = new ResizeObserver((entries) => {
      const entry = entries[0]
      if (!entry) return
      const { width, height } = entry.contentRect
      setStageSize({ width, height })
      drawing.viewport.centerInitialViewport(width, height)
    })
    observer.observe(container)
    return () => observer.disconnect()
  }, [drawing.viewport])

  useEffect(() => {
    const serverRevision = room.revision
    const isSameServerRevision = appliedServerRevisionRef.current === serverRevision
    const isStaleEmptySnapshot =
      isSameServerRevision && serverObjects.length === 0 && drawing.objects.length > 0

    if (isStaleEmptySnapshot) return

    const nextServerObjectMap = createObjectMap(serverObjects)
    if (areInfinityObjectListsEqual(drawing.objects, serverObjects)) {
      appliedServerRevisionRef.current = serverRevision
      previousObjectsRef.current = nextServerObjectMap
      return
    }

    const selectedIds = drawing.selectedIds.filter((selectedId) =>
      serverObjects.some((object) => object.id === selectedId),
    )
    isApplyingRemoteRef.current = true
    appliedServerRevisionRef.current = serverRevision
    previousObjectsRef.current = nextServerObjectMap
    drawing.replaceObjectsFromServer(serverObjects, selectedIds)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [room.revision, serverObjects])

  useEffect(() => {
    if (isApplyingRemoteRef.current) {
      isApplyingRemoteRef.current = false
      previousObjectsRef.current = createObjectMap(drawing.objects)
      return
    }

    const previousObjects = previousObjectsRef.current
    const currentObjects = createObjectMap(drawing.objects)
    const operations: InfiniteCanvasOperationRequest[] = []

    if (drawing.objects.length === 0 && previousObjects.size > 0) {
      operations.push({
        clientOperationId: createClientOperationId(),
        operationType: 'CLEAR_CANVAS',
      })
    } else {
      for (const currentObject of drawing.objects) {
        const previousObject = previousObjects.get(currentObject.id)
        if (previousObject && stringifyInfinityObject(previousObject) === stringifyInfinityObject(currentObject)) {
          continue
        }

        operations.push({
          clientOperationId: createClientOperationId(),
          operationType: previousObject ? 'UPDATE_ELEMENT' : 'CREATE_ELEMENT',
          elementId: currentObject.id,
          element: { ...currentObject },
        })
      }

      for (const previousObject of previousObjects.values()) {
        if (currentObjects.has(previousObject.id)) continue
        operations.push({
          clientOperationId: createClientOperationId(),
          operationType: 'DELETE_ELEMENT',
          elementId: previousObject.id,
        })
      }
    }

    previousObjectsRef.current = currentObjects
    if (operations.length === 0) return

    const sent = room.sendOperations(operations)
    if (!sent) {
      toast.error('서버 연결 후 편집할 수 있어요.')
    }
  }, [drawing.objects, room])

  useEffect(() => {
    const previousSelectedIds = previousSelectedIdsRef.current
    const nextSelectedIds = drawing.selectedIds
    const addedIds = nextSelectedIds.filter((selectedId) => !previousSelectedIds.includes(selectedId))
    const removedIds = previousSelectedIds.filter((selectedId) => !nextSelectedIds.includes(selectedId))

    for (const elementId of addedIds) {
      const lock = room.locks[elementId]
      if (!lock || lock.userUuid === room.myUserUuid) {
        room.acquireLock(elementId)
      }
    }

    for (const elementId of removedIds) {
      const lock = room.locks[elementId]
      if (lock?.userUuid === room.myUserUuid) {
        room.releaseLock(elementId)
      }
    }

    previousSelectedIdsRef.current = nextSelectedIds
  }, [drawing.selectedIds, room])

  const handleCursorMove = useCallback(
    (cursor: { x: number; y: number; zoom: number }) => {
      latestCursorRef.current = cursor
      sendCursor(cursor)
    },
    [sendCursor],
  )

  const handleCopyInviteCode = useCallback(() => {
    if (!room.inviteCode) return
    void navigator.clipboard.writeText(room.inviteCode)
    toast.success('초대코드를 복사했어요.')
  }, [room.inviteCode])

  const handleCapture = useCallback(
    async (rect: InfinityCaptureRect, ratio: InfinityCaptureRatio) => {
      const stage = stageRef.current
      if (!stage) return

      try {
        const blob = await createStageBlob(stage, rect)
        const output = await room.saveOutput(blob, {
          roomCode: room.roomCode,
          revision: room.revision,
          ratio,
          rect,
        })
        if (output) {
          setIsCaptureMode(false)
        }
      } catch {
        toast.error('선택한 영역을 이미지로 만들지 못했어요.')
      }
    },
    [room],
  )

  return (
    <div className="fixed inset-0 h-dvh w-dvw overflow-hidden bg-canvas-background">
      <InfinityToolPanel drawing={drawing} />

      <div ref={containerRef} className="absolute inset-0 min-w-0 overflow-hidden">
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
          lockedElementIds={lockedElementIds}
          remoteDraftObjects={remoteDraftObjects}
          remoteCursors={remoteCursors}
          onCursorMove={handleCursorMove}
        />

        {drawing.textEditor && (
          <InfinityTextEditor
            state={drawing.textEditor}
            scaleRef={drawing.viewport.scaleRef}
            stagePosRef={drawing.viewport.stagePosRef}
            editingTool={drawing.tool}
            onCommit={(text, fontSize) => drawing.commitTextEditor(text, fontSize)}
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
      </div>

      <button
        type="button"
        onClick={handleCopyInviteCode}
        className="fixed left-1/2 top-4 z-20 h-[120px] w-[min(300px,calc(100vw-32px))] -translate-x-1/2 transition-transform hover:-translate-y-0.5 hover:scale-[1.02]"
        aria-label={`초대코드 ${room.inviteCode ?? '-'} 복사`}
      >
        <Image
          src="/images/infinite-canvas/invite-code.png"
          alt=""
          aria-hidden
          fill
          priority
          sizes="300px"
          className="object-contain drop-shadow-[0_14px_24px_rgba(55,82,190,0.24)]"
        />
        <div className="absolute inset-x-[18%] inset-y-[25%] grid place-items-center">
          <span className="h2-b text-white drop-shadow-[0_3px_7px_rgba(33,45,126,0.5)]">
            {room.inviteCode ?? '-'}
          </span>
        </div>
      </button>

      <InfinityParticipantsPanel
        connectionStatus={room.connectionStatus}
        isUpdatingProfile={room.isUpdatingProfile}
        maxParticipants={room.maxParticipants}
        me={room.me}
        onUpdateMyColor={room.updateMyColor}
        participants={room.participants}
      />

      <div className="fixed bottom-8 right-8 z-20 flex items-center gap-4">
        <button
          type="button"
          onClick={() => setIsCaptureMode(true)}
          disabled={room.isSavingOutput}
          className="relative h-[56px] w-[116px] transition-transform hover:-translate-y-0.5 hover:scale-[1.03] disabled:pointer-events-none disabled:opacity-55"
          aria-label="출력"
        >
          <Image
            src="/images/infinite-canvas/print-button.png"
            alt=""
            aria-hidden
            fill
            priority
            sizes="116px"
            className="object-contain drop-shadow-[0_10px_18px_rgba(55,82,190,0.22)]"
          />
        </button>
        <button
          type="button"
          onClick={room.leaveCanvas}
          className="relative h-[56px] w-[145px] transition-transform hover:-translate-y-0.5 hover:scale-[1.03]"
          aria-label="나가기"
        >
          <Image
            src="/images/infinite-canvas/exit-button.png"
            alt=""
            aria-hidden
            fill
            priority
            sizes="145px"
            className="object-contain drop-shadow-[0_10px_18px_rgba(55,82,190,0.22)]"
          />
        </button>
      </div>
    </div>
  )
}
