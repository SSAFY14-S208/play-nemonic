'use client'

import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type Konva from 'konva'
import { ArrowDownToLine, ArrowUpToLine, Copy, Link2, LogOut, Printer } from 'lucide-react'
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

function objectMapValues(objectMap: Map<string, InfinityObject>) {
  return [...objectMap.values()]
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
      stringifyInfinityObject(previousServerObject) !== stringifyInfinityObject(localObject)

    if (
      localObjectChangedFromPreviousServer &&
      stringifyInfinityObject(serverObject) !== stringifyInfinityObject(localObject)
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
  const previousObjectsRef = useRef(createObjectMap([]))
  const isApplyingRemoteRef = useRef(false)
  const appliedServerRevisionRef = useRef<number | null>(null)
  const lastCursorSentAtRef = useRef(0)
  const lastDraftCursorSentAtRef = useRef(0)
  const latestCursorRef = useRef<{ x: number; y: number; zoom: number } | null>(null)
  const draftObjectRef = useRef<InfinityObject | null>(null)
  const lastFinishedDraftRef = useRef<InfinityObject | null>(null)
  const draftClearTimeoutRef = useRef<number | null>(null)
  const previousSelectedIdsRef = useRef<string[]>([])
  const [isCaptureMode, setIsCaptureMode] = useState(false)
  const [copiedInviteTarget, setCopiedInviteTarget] = useState<'link' | 'code' | null>(null)
  const [layerMenu, setLayerMenu] = useState<{ x: number; y: number } | null>(null)

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
      const draftObject = draftObjectRef.current
      const minInterval = draftObject ? 24 : 45
      if (!options.force && now - lastCursorSentAtRef.current < minInterval) return
      if (draftObject && !options.force && now - lastDraftCursorSentAtRef.current < 24) return

      lastCursorSentAtRef.current = now
      if (draftObject) {
        lastDraftCursorSentAtRef.current = now
      }
      sendRoomCursor({
        x: cursor.x,
        y: cursor.y,
        zoom: cursor.zoom,
        payload: createCursorPayload(draftObject),
      })
    },
    [createCursorPayload, sendRoomCursor],
  )

  const handleDraftObjectChange = useCallback(
    (draftObject: InfinityObject | null) => {
      if (draftClearTimeoutRef.current) {
        window.clearTimeout(draftClearTimeoutRef.current)
        draftClearTimeoutRef.current = null
      }
      if (draftObject !== null) {
        draftObjectRef.current = draftObject
        lastFinishedDraftRef.current = draftObject
        return
      }
      draftObjectRef.current = lastFinishedDraftRef.current
      const latestCursor = latestCursorRef.current
      if (!latestCursor) return
      draftClearTimeoutRef.current = window.setTimeout(() => {
        draftClearTimeoutRef.current = null
        draftObjectRef.current = null
        lastFinishedDraftRef.current = null
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

  const visibleObjectIds = useMemo(
    () => new Set(drawing.objects.map((object) => object.id)),
    [drawing.objects],
  )

  const remoteDraftObjects: InfinityRemoteDraftObjectView[] = useMemo(
    () =>
      Object.values(room.remoteCursors)
        .filter((cursor) => cursor.userUuid !== room.myUserUuid)
        .map((cursor) => {
          const draftObject = getPayloadDraftObject(cursor.payload)
          if (!draftObject) return null
          if (visibleObjectIds.has(draftObject.id)) return null
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
    [getParticipantIdentityIndex, room.myUserUuid, room.participantsByUserUuid, room.remoteCursors, visibleObjectIds],
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
    const isSameServerObjects = areInfinityObjectListsEqual(
      objectMapValues(previousObjectsRef.current),
      serverObjects,
    )
    if (isSameServerObjects) {
      appliedServerRevisionRef.current = serverRevision
      return
    }

    if (areInfinityObjectListsEqual(drawing.objects, serverObjects)) {
      appliedServerRevisionRef.current = serverRevision
      previousObjectsRef.current = nextServerObjectMap
      return
    }

    const isInitialServerApply = appliedServerRevisionRef.current === null
    const nextObjects =
      room.hasPendingOperations && !isInitialServerApply
        ? mergeServerObjectsWithLocalPending({
            previousServerObjects: previousObjectsRef.current,
            serverObjects,
            localObjects: drawing.objects,
          })
        : serverObjects
    const selectedIds = drawing.selectedIds.filter((selectedId) =>
      nextObjects.some((object) => object.id === selectedId),
    )

    isApplyingRemoteRef.current = true
    appliedServerRevisionRef.current = serverRevision
    previousObjectsRef.current = nextServerObjectMap
    if (isInitialServerApply) {
      drawing.replaceObjectsFromServer(nextObjects, selectedIds)
    } else {
      drawing.syncObjectsFromServer(nextObjects, selectedIds)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [room.hasPendingOperations, room.revision, serverObjects])

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

    const sent = sendRoomOperations(operations)
    if (!sent) {
      toast.error('서버 연결 후 편집할 수 있어요.')
    }
  }, [drawing.objects, sendRoomOperations])

  useEffect(() => {
    const previousSelectedIds = previousSelectedIdsRef.current
    const nextSelectedIds = drawing.selectedIds
    const addedIds = nextSelectedIds.filter((selectedId) => !previousSelectedIds.includes(selectedId))
    const removedIds = previousSelectedIds.filter((selectedId) => !nextSelectedIds.includes(selectedId))

    for (const elementId of addedIds) {
      const lock = room.locks[elementId]
      if (!lock || lock.userUuid === room.myUserUuid) {
        acquireLock(elementId)
      }
    }

    for (const elementId of removedIds) {
      const lock = room.locks[elementId]
      if (lock?.userUuid === room.myUserUuid) {
        releaseLock(elementId)
      }
    }

    previousSelectedIdsRef.current = nextSelectedIds
  }, [acquireLock, drawing.selectedIds, releaseLock, room.locks, room.myUserUuid])

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
        const output = await saveOutput(blob, {
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
    [room.revision, room.roomCode, saveOutput],
  )

  const handleLayerMenuRequest = useCallback(
    (request: { elementId: string; x: number; y: number }) => {
      if (getForeignLock(request.elementId)) {
        handleBlockedObjectEdit(request.elementId)
        return
      }
      setLayerMenu({
        x: Math.min(Math.max(request.x, 12), window.innerWidth - 172),
        y: Math.min(Math.max(request.y, 12), window.innerHeight - 112),
      })
    },
    [getForeignLock, handleBlockedObjectEdit],
  )

  const shiftSelectedLayer = useCallback(
    (direction: 1 | -1) => {
      drawing.shiftSelectedZIndex(direction)
      setLayerMenu(null)
    },
    [drawing],
  )

  useEffect(() => {
    if (!layerMenu) return
    const closeLayerMenu = () => setLayerMenu(null)
    window.addEventListener('pointerdown', closeLayerMenu)
    window.addEventListener('keydown', closeLayerMenu)
    return () => {
      window.removeEventListener('pointerdown', closeLayerMenu)
      window.removeEventListener('keydown', closeLayerMenu)
    }
  }, [layerMenu])

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
          onLayerMenuRequest={handleLayerMenuRequest}
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

        {layerMenu && drawing.selectedIds.length > 0 && (
          <div
            className="fixed z-30 grid min-w-40 gap-1 rounded-[18px] border border-white/75 bg-white/95 p-2 shadow-[0_16px_32px_rgba(35,64,140,0.22)] backdrop-blur"
            style={{ left: layerMenu.x, top: layerMenu.y }}
            onPointerDown={(event) => event.stopPropagation()}
          >
            <button
              type="button"
              onClick={() => shiftSelectedLayer(1)}
              className="body-b inline-flex h-10 items-center gap-2 rounded-full px-3 text-[#25376c] transition-colors hover:bg-[#eaf6ff]"
            >
              <ArrowUpToLine className="size-4" aria-hidden />
              앞으로 가져오기
            </button>
            <button
              type="button"
              onClick={() => shiftSelectedLayer(-1)}
              className="body-b inline-flex h-10 items-center gap-2 rounded-full px-3 text-[#25376c] transition-colors hover:bg-[#eaf6ff]"
            >
              <ArrowDownToLine className="size-4" aria-hidden />
              뒤로 보내기
            </button>
          </div>
        )}
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

      <div className="fixed bottom-8 right-8 z-20 flex items-center gap-4">
        <button
          type="button"
          onClick={() => setIsCaptureMode(true)}
          disabled={room.isSavingOutput}
          className="body-b inline-flex h-12 min-w-[96px] items-center justify-center gap-2 rounded-full border border-white/72 bg-[#3aa7f4] px-5 text-white shadow-[0_10px_22px_rgba(46,95,210,0.22),inset_0_1px_0_rgba(255,255,255,0.42)] transition-transform hover:-translate-y-0.5 hover:scale-[1.03] disabled:pointer-events-none disabled:opacity-55"
          aria-label="출력"
        >
          <Printer className="size-4" aria-hidden />
          출력
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
