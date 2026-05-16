'use client'

import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type Konva from 'konva'
import { LogOut, Printer } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/shared/components'
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
    if (!room.me?.color) return
    drawing.setColor(room.me.color)
    // 색상 초기 동기화 용도라 drawing 전체 의존성을 열지 않는다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [room.me?.color])

  useEffect(() => {
    const container = containerRef.current
    if (!container) return

    const observer = new ResizeObserver((entries) => {
      const entry = entries[0]
      if (!entry) return
      const { width, height } = entry.contentRect
      setStageSize({ width, height })
    })
    observer.observe(container)
    return () => observer.disconnect()
  }, [])

  useEffect(() => {
    const selectedIds = drawing.selectedIds.filter((selectedId) =>
      serverObjects.some((object) => object.id === selectedId),
    )
    isApplyingRemoteRef.current = true
    previousObjectsRef.current = createObjectMap(serverObjects)
    drawing.replaceObjectsFromServer(serverObjects, selectedIds)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [serverObjects])

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
          canvasId: room.canvasId,
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
    <div className="relative h-screen w-screen overflow-hidden bg-canvas-background">
      <InfinityToolPanel drawing={drawing} />

      <div ref={containerRef} className="relative h-full w-full">
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

      <div className="fixed left-1/2 top-5 z-20 flex -translate-x-1/2 items-center gap-3 rounded-full border border-canvas-border bg-canvas-panel px-4 py-2 shadow-md">
        <p className="caption-b text-canvas-muted">초대코드</p>
        <button
          type="button"
          onClick={handleCopyInviteCode}
          className="body-b rounded-full bg-white px-4 py-2 text-canvas-ink transition-colors hover:bg-canvas-active"
        >
          {room.inviteCode ?? '-'}
        </button>
      </div>

      <InfinityParticipantsPanel
        connectionStatus={room.connectionStatus}
        maxParticipants={room.maxParticipants}
        me={room.me}
        participants={room.participants}
      />

      <div className="fixed bottom-8 right-8 z-20 flex items-center gap-3">
        <Button
          type="button"
          color="blue"
          size="md"
          onClick={() => setIsCaptureMode(true)}
          disabled={room.isSavingOutput}
        >
          <Printer className="size-4" aria-hidden />
          출력
        </Button>
        <Button type="button" color="dark" size="md" onClick={room.leaveCanvas}>
          <LogOut className="size-4" aria-hidden />
          나가기
        </Button>
      </div>
    </div>
  )
}
