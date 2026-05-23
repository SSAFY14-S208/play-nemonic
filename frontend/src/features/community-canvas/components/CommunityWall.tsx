'use client'

import {
  useEffect,
  useRef,
  useState,
  type CSSProperties,
  type MouseEvent,
  type PointerEvent as ReactPointerEvent,
  type WheelEvent as ReactWheelEvent,
} from 'react'
import Image from 'next/image'
import { RotateCw } from 'lucide-react'
import { PostItNote } from '@/shared/components/PostItNote'
import {
  COMMUNITY_CANVAS_ATTACHABLE_SURFACE_BOUNDS,
  COMMUNITY_CANVAS_MEMO_HEIGHT,
  COMMUNITY_CANVAS_MEMO_WIDTH,
  COMMUNITY_CANVAS_WALL_HEIGHT,
  COMMUNITY_CANVAS_WALL_WIDTH,
} from '@/shared/constants'
import { cn } from '@/shared/libs'
import type { CommunityMemoItemResponse } from '@/shared/types'
import type { CommunityMemoLayoutDraft, CommunityPendingMemoPlacement } from '../hooks'
import { getCommunityMemoColor, getStaticCommunityImageUrl, getStaticCommunityMemoImageUrl } from '../utils'
import { CommunityMemoCard } from './CommunityMemoCard'

interface CommunityWallProps {
  memos: CommunityMemoItemResponse[]
  memoPlaybackImageUrls: Record<string, string>
  selectedMemoUuid: string | null
  memoStatus: 'idle' | 'loading' | 'success' | 'error'
  memoError: string | null
  pendingMemo: CommunityPendingMemoPlacement | null
  editingMemo: CommunityMemoItemResponse | null
  editingLayoutDraft: CommunityMemoLayoutDraft | null
  nextZIndex: number
  isAttachingMemo: boolean
  isSavingLayout: boolean
  isPlaybackPaused: boolean
  onSelectMemo: (memo: CommunityMemoItemResponse) => void
  onClearSelection: () => void
  onOpenMemoDetail: (memoUuid: string) => void
  onAttachPendingMemo: (placement: CommunityMemoLayoutDraft) => void
  onEditingLayoutChange: (partialLayout: Partial<CommunityMemoLayoutDraft>) => void
  onSaveEditingLayout: (layout?: CommunityMemoLayoutDraft) => void
  onRetry: () => void
}

const WALL_WIDTH = COMMUNITY_CANVAS_WALL_WIDTH
const WALL_HEIGHT = COMMUNITY_CANVAS_WALL_HEIGHT
const MEMO_WIDTH = COMMUNITY_CANVAS_MEMO_WIDTH
const MEMO_HEIGHT = COMMUNITY_CANVAS_MEMO_HEIGHT
const MEMO_VISUAL_SAFE_PADDING = 24
const WALL_BACKGROUND_IMAGE = '/images/community-canvas/wall-bg-studio-nemonic-board-large-v8.png'
const BOUNDARY_EPSILON = 0.5
const ATTACHABLE_SURFACE_BOUNDS = COMMUNITY_CANVAS_ATTACHABLE_SURFACE_BOUNDS
const ATTACHABLE_SURFACE_LEFT = WALL_WIDTH / 2 + ATTACHABLE_SURFACE_BOUNDS.left
const ATTACHABLE_SURFACE_TOP = WALL_HEIGHT / 2 + ATTACHABLE_SURFACE_BOUNDS.top
const ATTACHABLE_SURFACE_STYLE = {
  left: ATTACHABLE_SURFACE_LEFT,
  top: ATTACHABLE_SURFACE_TOP,
  width: ATTACHABLE_SURFACE_BOUNDS.right - ATTACHABLE_SURFACE_BOUNDS.left,
  height: ATTACHABLE_SURFACE_BOUNDS.bottom - ATTACHABLE_SURFACE_BOUNDS.top,
} satisfies CSSProperties
const ATTACHABLE_MEMO_LAYER_STYLE = {
  left: -ATTACHABLE_SURFACE_LEFT,
  top: -ATTACHABLE_SURFACE_TOP,
  width: WALL_WIDTH,
  height: WALL_HEIGHT,
} satisfies CSSProperties
const MEMO_PLACEMENT_ANIMATION_DURATION_MS = 720
const MEMO_SELECT_DELAY_MS = 220
const MEMO_DETAIL_DOUBLE_TAP_DELAY_MS = 500
const MEMO_DETAIL_TAP_MOVE_THRESHOLD = 8

type WallPoint = {
  x: number
  y: number
}

type WallBounds = {
  left: number
  top: number
  right: number
  bottom: number
}

type WallInteraction =
  | { type: 'drag-edit'; pointerId: number; pointerOffsetX: number; pointerOffsetY: number }
  | { type: 'rotate-edit'; pointerId: number }
  | { type: 'rotate-pending'; pointerId: number }

type MemoPlacementMotion = 'attach' | 'detach' | 'lift' | 'release'

type ExitingMemo = {
  memo: CommunityMemoItemResponse
  removalKey: string
}

type WallPanState = {
  pointerId: number
  startX: number
  scrollLeft: number
}

type MemoTapState = {
  memoUuid: string
  tappedAt: number
}

type EditableDetailTapState = {
  memoUuid: string
  pointerId: number
  startClientX: number
  startClientY: number
}

function normalizeRotation(rotationDeg: number) {
  const normalized = ((((rotationDeg + 180) % 360) + 360) % 360) - 180
  return Math.round(normalized * 10) / 10
}

function getCenteredScrollOffset(scrollSize: number, clientSize: number) {
  return Math.max(0, (scrollSize - clientSize) / 2)
}

function lockWallVerticalScroll(visibleArea: HTMLElement) {
  const lockedScrollTop = getCenteredScrollOffset(
    visibleArea.scrollHeight,
    visibleArea.clientHeight,
  )

  if (Math.abs(visibleArea.scrollTop - lockedScrollTop) > BOUNDARY_EPSILON) {
    visibleArea.scrollTop = lockedScrollTop
  }
}

function getRotationFromPoint(point: WallPoint, layout: CommunityMemoLayoutDraft) {
  const angle = (Math.atan2(point.y - layout.positionY, point.x - layout.positionX) * 180) / Math.PI
  return normalizeRotation(angle + 90)
}

function overlapsMemo(layout: CommunityMemoLayoutDraft, memo: CommunityMemoItemResponse) {
  return (
    Math.abs(layout.positionX - memo.positionX) < MEMO_WIDTH &&
    Math.abs(layout.positionY - memo.positionY) < MEMO_HEIGHT
  )
}

function getStackedZIndex({
  layout,
  memos,
  excludeMemoUuid,
  fallbackZIndex,
}: {
  layout: CommunityMemoLayoutDraft
  memos: CommunityMemoItemResponse[]
  excludeMemoUuid?: string
  fallbackZIndex: number
}) {
  const topOverlappedZIndex = memos.reduce<number | null>((currentTop, memo) => {
    if (memo.memoUuid === excludeMemoUuid) return currentTop
    if (!overlapsMemo(layout, memo)) return currentTop
    return Math.max(currentTop ?? memo.zIndex, memo.zIndex)
  }, null)

  return topOverlappedZIndex === null ? fallbackZIndex : topOverlappedZIndex + 1
}

function getMemoTone(memo: CommunityMemoItemResponse | CommunityPendingMemoPlacement) {
  if ('memoColor' in memo) return memo.memoColor
  return getCommunityMemoColor(memo)
}

function getMemoCornerPoints(layout: CommunityMemoLayoutDraft): WallPoint[] {
  const halfMemoWidth = MEMO_WIDTH / 2 + MEMO_VISUAL_SAFE_PADDING
  const halfMemoHeight = MEMO_HEIGHT / 2 + MEMO_VISUAL_SAFE_PADDING
  const rotationRadian = (layout.rotationDeg * Math.PI) / 180
  const cosRotation = Math.cos(rotationRadian)
  const sinRotation = Math.sin(rotationRadian)

  return [
    { x: -halfMemoWidth, y: -halfMemoHeight },
    { x: halfMemoWidth, y: -halfMemoHeight },
    { x: halfMemoWidth, y: halfMemoHeight },
    { x: -halfMemoWidth, y: halfMemoHeight },
  ].map((corner) => ({
    x: layout.positionX + corner.x * cosRotation - corner.y * sinRotation,
    y: layout.positionY + corner.x * sinRotation + corner.y * cosRotation,
  }))
}

function getMemoBounds(layout: CommunityMemoLayoutDraft): WallBounds {
  const cornerPoints = getMemoCornerPoints(layout)

  return {
    left: Math.min(...cornerPoints.map((point) => point.x)),
    top: Math.min(...cornerPoints.map((point) => point.y)),
    right: Math.max(...cornerPoints.map((point) => point.x)),
    bottom: Math.max(...cornerPoints.map((point) => point.y)),
  }
}

function toMemoLayoutDraft(
  layout: Pick<CommunityMemoLayoutDraft, 'positionX' | 'positionY' | 'zIndex' | 'rotationDeg'>,
): CommunityMemoLayoutDraft {
  return {
    positionX: layout.positionX,
    positionY: layout.positionY,
    zIndex: layout.zIndex,
    rotationDeg: layout.rotationDeg,
  }
}

function isMemoLayoutInsideBounds(layout: CommunityMemoLayoutDraft, bounds: WallBounds): boolean {
  const memoBounds = getMemoBounds(layout)

  return (
    memoBounds.left >= bounds.left + BOUNDARY_EPSILON &&
    memoBounds.right <= bounds.right - BOUNDARY_EPSILON &&
    memoBounds.top >= bounds.top + BOUNDARY_EPSILON &&
    memoBounds.bottom <= bounds.bottom - BOUNDARY_EPSILON
  )
}

function clampMemoLayoutToAttachableSurface(layout: CommunityMemoLayoutDraft) {
  return clampMemoLayoutToBounds(layout, ATTACHABLE_SURFACE_BOUNDS)
}

function clampMemoLayoutToBounds(
  layout: CommunityMemoLayoutDraft,
  bounds: WallBounds,
): CommunityMemoLayoutDraft {
  const memoBounds = getMemoBounds(layout)
  let correctedPositionX = layout.positionX
  let correctedPositionY = layout.positionY

  if (memoBounds.left < bounds.left + BOUNDARY_EPSILON) {
    correctedPositionX += bounds.left + BOUNDARY_EPSILON - memoBounds.left
  } else if (memoBounds.right > bounds.right - BOUNDARY_EPSILON) {
    correctedPositionX += bounds.right - BOUNDARY_EPSILON - memoBounds.right
  }

  if (memoBounds.top < bounds.top + BOUNDARY_EPSILON) {
    correctedPositionY += bounds.top + BOUNDARY_EPSILON - memoBounds.top
  } else if (memoBounds.bottom > bounds.bottom - BOUNDARY_EPSILON) {
    correctedPositionY += bounds.bottom - BOUNDARY_EPSILON - memoBounds.bottom
  }

  return {
    ...layout,
    positionX: Math.round(correctedPositionX),
    positionY: Math.round(correctedPositionY),
  }
}

function getDisplayMemo(memo: CommunityMemoItemResponse): CommunityMemoItemResponse {
  return {
    ...memo,
    ...clampMemoLayoutToAttachableSurface(toMemoLayoutDraft(memo)),
  }
}

export function CommunityWall({
  memos,
  memoPlaybackImageUrls,
  selectedMemoUuid,
  memoStatus,
  memoError,
  pendingMemo,
  editingMemo,
  editingLayoutDraft,
  nextZIndex,
  isAttachingMemo,
  isSavingLayout,
  isPlaybackPaused,
  onSelectMemo,
  onClearSelection,
  onOpenMemoDetail,
  onAttachPendingMemo,
  onEditingLayoutChange,
  onSaveEditingLayout,
  onRetry,
}: CommunityWallProps) {
  const visibleAreaRef = useRef<HTMLElement>(null)
  const wallRef = useRef<HTMLDivElement>(null)
  const memoClickTimerRef = useRef<number | null>(null)
  const memoClickTargetUuidRef = useRef<string | null>(null)
  const lastMemoTapRef = useRef<MemoTapState | null>(null)
  const lastMemoDetailOpenRef = useRef<MemoTapState | null>(null)
  const editableDetailTapRef = useRef<EditableDetailTapState | null>(null)
  const interactionRef = useRef<WallInteraction | null>(null)
  const interactionChangedRef = useRef(false)
  const editingLayoutDraftRef = useRef<CommunityMemoLayoutDraft | null>(null)
  const cursorPlacementRef = useRef<CommunityMemoLayoutDraft | null>(null)
  const hasCursorPositionRef = useRef(false)
  const isSavingLayoutRef = useRef(isSavingLayout)
  const saveEditingLayoutRef = useRef(onSaveEditingLayout)
  const previousMemoMapRef = useRef<Map<string, CommunityMemoItemResponse> | null>(null)
  const memoPlacementAnimationTimerRefs = useRef<number[]>([])
  const panStateRef = useRef<WallPanState | null>(null)
  const hasCenteredWallScrollRef = useRef(false)
  const [wallScale, setWallScale] = useState(1)
  const [viewportSize, setViewportSize] = useState({ width: 0, height: 0 })
  const [cursorPlacement, setCursorPlacement] = useState<CommunityMemoLayoutDraft>({
    positionX: 0,
    positionY: 0,
    zIndex: 1,
    rotationDeg: 0,
  })
  const [isPendingPlacementInsideVisibleArea, setIsPendingPlacementInsideVisibleArea] =
    useState(true)
  const [interaction, setInteraction] = useState<WallInteraction | null>(null)
  const [enteringMemoUuids, setEnteringMemoUuids] = useState<Set<string>>(() => new Set())
  const [exitingMemos, setExitingMemos] = useState<ExitingMemo[]>([])
  const isEditingLayout = editingMemo !== null && editingLayoutDraft !== null
  const isWallManipulating = pendingMemo !== null || isEditingLayout

  useEffect(() => {
    let cancelled = false

    const updateWallScale = () => {
      const visualViewport = window.visualViewport
      const viewportWidth = visualViewport?.width ?? window.innerWidth
      const viewportHeight = visualViewport?.height ?? window.innerHeight

      setViewportSize({ width: viewportWidth, height: viewportHeight })
      setWallScale(Math.max(viewportWidth / WALL_WIDTH, viewportHeight / WALL_HEIGHT))
    }

    void (async () => {
      await Promise.resolve()
      if (!cancelled) updateWallScale()
    })()

    window.addEventListener('resize', updateWallScale)
    window.visualViewport?.addEventListener('resize', updateWallScale)
    return () => {
      cancelled = true
      window.removeEventListener('resize', updateWallScale)
      window.visualViewport?.removeEventListener('resize', updateWallScale)
    }
  }, [])

  useEffect(() => {
    const visibleArea = visibleAreaRef.current
    if (!visibleArea) return
    if (viewportSize.width <= 0 || viewportSize.height <= 0) return

    const frameId = window.requestAnimationFrame(() => {
      if (!hasCenteredWallScrollRef.current) {
        visibleArea.scrollLeft = getCenteredScrollOffset(
          visibleArea.scrollWidth,
          visibleArea.clientWidth,
        )
        hasCenteredWallScrollRef.current = true
      }
      lockWallVerticalScroll(visibleArea)
    })

    return () => window.cancelAnimationFrame(frameId)
  }, [viewportSize.height, viewportSize.width, wallScale])

  useEffect(() => {
    interactionRef.current = interaction
  }, [interaction])

  useEffect(() => {
    editingLayoutDraftRef.current = editingLayoutDraft
  }, [editingLayoutDraft])

  useEffect(() => {
    let cancelled = false

    void (async () => {
      if (!pendingMemo) {
        cursorPlacementRef.current = null
        hasCursorPositionRef.current = false
        setIsPendingPlacementInsideVisibleArea(true)
        return
      }

      const nextPlacement = {
        positionX: 0,
        positionY: 0,
        zIndex: nextZIndex,
        rotationDeg: 0,
      }
      cursorPlacementRef.current = nextPlacement
      hasCursorPositionRef.current = false
      setIsPendingPlacementInsideVisibleArea(true)
      if (!cancelled) {
        setCursorPlacement(nextPlacement)
      }
    })()

    return () => {
      cancelled = true
    }
  }, [nextZIndex, pendingMemo])

  useEffect(() => {
    isSavingLayoutRef.current = isSavingLayout
  }, [isSavingLayout])

  useEffect(() => {
    saveEditingLayoutRef.current = onSaveEditingLayout
  }, [onSaveEditingLayout])

  useEffect(() => {
    const handlePointerEnd = (event: globalThis.PointerEvent) => {
      const currentInteraction = interactionRef.current
      if (!currentInteraction) return
      if (currentInteraction.pointerId !== event.pointerId) return

      const editableDetailTap = editableDetailTapRef.current
      if (
        event.type === 'pointerup' &&
        currentInteraction.type === 'drag-edit' &&
        editableDetailTap?.pointerId === event.pointerId
      ) {
        editableDetailTapRef.current = null
        lastMemoTapRef.current = null
        interactionRef.current = null
        interactionChangedRef.current = false
        setInteraction(null)
        lastMemoDetailOpenRef.current = {
          memoUuid: editableDetailTap.memoUuid,
          tappedAt: window.performance.now(),
        }
        onOpenMemoDetail(editableDetailTap.memoUuid)
        return
      }

      if (
        (currentInteraction.type === 'drag-edit' || currentInteraction.type === 'rotate-edit') &&
        editingLayoutDraftRef.current &&
        interactionChangedRef.current &&
        !isSavingLayoutRef.current
      ) {
        saveEditingLayoutRef.current(editingLayoutDraftRef.current)
      }

      editableDetailTapRef.current = null
      interactionRef.current = null
      interactionChangedRef.current = false
      setInteraction(null)
    }

    window.addEventListener('pointerup', handlePointerEnd)
    window.addEventListener('pointercancel', handlePointerEnd)
    return () => {
      window.removeEventListener('pointerup', handlePointerEnd)
      window.removeEventListener('pointercancel', handlePointerEnd)
    }
  }, [onOpenMemoDetail])

  useEffect(() => {
    const memoPlacementAnimationTimers = memoPlacementAnimationTimerRefs.current

    return () => {
      if (memoClickTimerRef.current) window.clearTimeout(memoClickTimerRef.current)
      memoClickTargetUuidRef.current = null
      lastMemoTapRef.current = null
      editableDetailTapRef.current = null
      memoPlacementAnimationTimers.forEach((timerId) => window.clearTimeout(timerId))
    }
  }, [])

  useEffect(() => {
    let cancelled = false

    void (async () => {
      await Promise.resolve()
      if (cancelled) return

      const nextMemoMap = new Map(
        memos.map((memo) => [memo.memoUuid, getDisplayMemo(memo)]),
      )
      const previousMemoMap = previousMemoMapRef.current

      if (!previousMemoMap) {
        previousMemoMapRef.current = nextMemoMap
        return
      }

      const enteringMemoIds = [...nextMemoMap.keys()].filter(
        (memoUuid) => !previousMemoMap.has(memoUuid),
      )
      const movedMemoIds = [...nextMemoMap.entries()]
        .filter(([memoUuid, memo]) => {
          const previousMemo = previousMemoMap.get(memoUuid)
          if (!previousMemo) return false

          return (
            memo.positionX !== previousMemo.positionX ||
            memo.positionY !== previousMemo.positionY ||
            memo.zIndex !== previousMemo.zIndex ||
            memo.rotationDeg !== previousMemo.rotationDeg
          )
        })
        .map(([memoUuid]) => memoUuid)
      const attachingMemoIds = [...new Set([...enteringMemoIds, ...movedMemoIds])]
      const removedMemos = [...previousMemoMap.entries()]
        .filter(([memoUuid]) => !nextMemoMap.has(memoUuid))
        .map(([memoUuid, memo]) => ({
          memo,
          removalKey: `${memoUuid}-${Date.now()}`,
        }))

      if (attachingMemoIds.length > 0) {
        setEnteringMemoUuids((currentMemoUuids) => {
          const nextMemoUuids = new Set(currentMemoUuids)
          attachingMemoIds.forEach((memoUuid) => nextMemoUuids.add(memoUuid))
          return nextMemoUuids
        })

        const timerId = window.setTimeout(() => {
          setEnteringMemoUuids((currentMemoUuids) => {
            const nextMemoUuids = new Set(currentMemoUuids)
            attachingMemoIds.forEach((memoUuid) => nextMemoUuids.delete(memoUuid))
            return nextMemoUuids
          })
        }, MEMO_PLACEMENT_ANIMATION_DURATION_MS)
        memoPlacementAnimationTimerRefs.current.push(timerId)
      }

      if (removedMemos.length > 0) {
        setExitingMemos((currentExitingMemos) => [
          ...currentExitingMemos.filter(
            (exitingMemo) =>
              !removedMemos.some(
                (removedMemo) => removedMemo.memo.memoUuid === exitingMemo.memo.memoUuid,
              ),
          ),
          ...removedMemos,
        ])

        const timerId = window.setTimeout(() => {
          setExitingMemos((currentExitingMemos) =>
            currentExitingMemos.filter(
              (exitingMemo) =>
                !removedMemos.some(
                  (removedMemo) => removedMemo.removalKey === exitingMemo.removalKey,
                ),
            ),
          )
        }, MEMO_PLACEMENT_ANIMATION_DURATION_MS)
        memoPlacementAnimationTimerRefs.current.push(timerId)
      }

      previousMemoMapRef.current = nextMemoMap
    })()

    return () => {
      cancelled = true
    }
  }, [memos])

  const clearMemoClickTimer = () => {
    if (memoClickTimerRef.current) {
      window.clearTimeout(memoClickTimerRef.current)
    }
    memoClickTimerRef.current = null
    memoClickTargetUuidRef.current = null
  }

  const getWallPoint = (clientX: number, clientY: number): WallPoint | null => {
    const wallElement = wallRef.current
    const wallRect = wallElement?.getBoundingClientRect()
    if (!wallElement || !wallRect) return null

    return {
      x: Math.round(((clientX - wallRect.left) / wallRect.width) * WALL_WIDTH - WALL_WIDTH / 2),
      y: Math.round(((clientY - wallRect.top) / wallRect.height) * WALL_HEIGHT - WALL_HEIGHT / 2),
    }
  }

  const getCurrentAttachableBounds = (): WallBounds | null => {
    const wallRect = wallRef.current?.getBoundingClientRect()
    const visibleAreaRect = visibleAreaRef.current?.getBoundingClientRect()
    if (!wallRect || !visibleAreaRect) return null

    const visibleRect = {
      left: Math.max(wallRect.left, visibleAreaRect.left),
      top: Math.max(wallRect.top, visibleAreaRect.top),
      right: Math.min(wallRect.right, visibleAreaRect.right),
      bottom: Math.min(wallRect.bottom, visibleAreaRect.bottom),
    }

    if (visibleRect.left >= visibleRect.right || visibleRect.top >= visibleRect.bottom) {
      return null
    }

    const visibleWallBounds = {
      left: ((visibleRect.left - wallRect.left) / wallRect.width) * WALL_WIDTH - WALL_WIDTH / 2,
      top: ((visibleRect.top - wallRect.top) / wallRect.height) * WALL_HEIGHT - WALL_HEIGHT / 2,
      right: ((visibleRect.right - wallRect.left) / wallRect.width) * WALL_WIDTH - WALL_WIDTH / 2,
      bottom: ((visibleRect.bottom - wallRect.top) / wallRect.height) * WALL_HEIGHT - WALL_HEIGHT / 2,
    }

    const attachableBounds = {
      left: Math.max(ATTACHABLE_SURFACE_BOUNDS.left, visibleWallBounds.left),
      top: Math.max(ATTACHABLE_SURFACE_BOUNDS.top, visibleWallBounds.top),
      right: Math.min(ATTACHABLE_SURFACE_BOUNDS.right, visibleWallBounds.right),
      bottom: Math.min(ATTACHABLE_SURFACE_BOUNDS.bottom, visibleWallBounds.bottom),
    }

    if (
      attachableBounds.left >= attachableBounds.right ||
      attachableBounds.top >= attachableBounds.bottom
    ) {
      return null
    }

    return attachableBounds
  }

  const getBoundedMemoLayout = (
    layout: CommunityMemoLayoutDraft,
  ): CommunityMemoLayoutDraft | null => {
    const attachableBounds = getCurrentAttachableBounds()
    if (!attachableBounds) return null
    return clampMemoLayoutToBounds(layout, attachableBounds)
  }

  const isMemoLayoutInsideVisibleArea = (layout: CommunityMemoLayoutDraft) => {
    const attachableBounds = getCurrentAttachableBounds()
    if (!attachableBounds) return false
    return isMemoLayoutInsideBounds(layout, attachableBounds)
  }

  const updateCursorPlacement = (
    placement: CommunityMemoLayoutDraft,
    options: { hasPosition?: boolean } = {},
  ) => {
    const boundedPlacement = getBoundedMemoLayout(placement) ?? placement
    cursorPlacementRef.current = boundedPlacement
    const hasResolvedPosition = options.hasPosition ?? hasCursorPositionRef.current
    if (options.hasPosition) {
      hasCursorPositionRef.current = true
    }
    setIsPendingPlacementInsideVisibleArea(
      !hasResolvedPosition || isMemoLayoutInsideVisibleArea(boundedPlacement),
    )
    setCursorPlacement(boundedPlacement)
  }

  const getPendingPlacementFromPoint = (point: WallPoint) => {
    const placement = {
      positionX: point.x,
      positionY: point.y,
      zIndex: nextZIndex,
      rotationDeg: cursorPlacement.rotationDeg,
    }

    const boundedPlacement = getBoundedMemoLayout(placement) ?? placement

    return {
      ...boundedPlacement,
      zIndex: getStackedZIndex({
        layout: boundedPlacement,
        memos,
        fallbackZIndex: nextZIndex,
      }),
    }
  }

  const getEditingPlacementFromPoint = (
    point: WallPoint,
    pointerOffset: WallPoint = { x: 0, y: 0 },
  ) => {
    if (!editingMemo || !editingLayoutDraft) return null

    const placement = {
      ...editingLayoutDraft,
      positionX: point.x - pointerOffset.x,
      positionY: point.y - pointerOffset.y,
    }
    const boundedPlacement = getBoundedMemoLayout(placement) ?? placement

    return {
      ...boundedPlacement,
      zIndex: getStackedZIndex({
        layout: boundedPlacement,
        memos,
        excludeMemoUuid: editingMemo.memoUuid,
        fallbackZIndex: editingLayoutDraft.zIndex,
      }),
    }
  }

  const handleWallPointerMove = (event: ReactPointerEvent<HTMLDivElement>) => {
    if (isWallManipulating && event.cancelable) {
      event.preventDefault()
    }

    const point = getWallPoint(event.clientX, event.clientY)
    if (!point) return

    const currentInteraction = interactionRef.current
    if (
      currentInteraction?.type === 'rotate-edit' &&
      currentInteraction.pointerId === event.pointerId &&
      editingLayoutDraftRef.current
    ) {
      const currentEditingLayoutDraft = editingLayoutDraftRef.current
      const nextLayout = {
        ...currentEditingLayoutDraft,
        rotationDeg: getRotationFromPoint(point, currentEditingLayoutDraft),
      }
      const boundedLayout = getBoundedMemoLayout(nextLayout)
      if (!boundedLayout) return

      editingLayoutDraftRef.current = boundedLayout
      interactionChangedRef.current = true
      onEditingLayoutChange(boundedLayout)
      return
    }

    if (
      currentInteraction?.type === 'drag-edit' &&
      currentInteraction.pointerId === event.pointerId &&
      isEditingLayout &&
      !isSavingLayout
    ) {
      const editableDetailTap = editableDetailTapRef.current
      if (editableDetailTap?.pointerId === event.pointerId) {
        const movedDistance = Math.hypot(
          event.clientX - editableDetailTap.startClientX,
          event.clientY - editableDetailTap.startClientY,
        )

        if (movedDistance > MEMO_DETAIL_TAP_MOVE_THRESHOLD) {
          editableDetailTapRef.current = null
        }
      }

      const nextPlacement = getEditingPlacementFromPoint(point, {
        x: currentInteraction.pointerOffsetX,
        y: currentInteraction.pointerOffsetY,
      })
      if (nextPlacement && isMemoLayoutInsideVisibleArea(nextPlacement)) {
        editingLayoutDraftRef.current = nextPlacement
        interactionChangedRef.current = true
        onEditingLayoutChange(nextPlacement)
      }
      return
    }

    if (
      currentInteraction?.type === 'rotate-pending' &&
      currentInteraction.pointerId === event.pointerId
    ) {
      const currentPlacement = cursorPlacementRef.current ?? cursorPlacement
      updateCursorPlacement({
        ...currentPlacement,
        rotationDeg: getRotationFromPoint(point, currentPlacement),
      })
      return
    }

    if (pendingMemo && !isAttachingMemo) {
      updateCursorPlacement(getPendingPlacementFromPoint(point), { hasPosition: true })
      return
    }
  }

  const handleWallClick = (event: MouseEvent<HTMLDivElement>) => {
    const point = getWallPoint(event.clientX, event.clientY)
    if (!point) return

    if (pendingMemo && !isAttachingMemo && !interaction) {
      const visualPlacement = cursorPlacementRef.current
      const nextPlacement =
        visualPlacement && hasCursorPositionRef.current
          ? visualPlacement
          : getPendingPlacementFromPoint(point)

      if (!isMemoLayoutInsideVisibleArea(nextPlacement)) {
        setIsPendingPlacementInsideVisibleArea(false)
        return
      }

      onAttachPendingMemo(nextPlacement)
      return
    }
  }

  const handleBlankSurfaceClick = (event: MouseEvent<HTMLElement>) => {
    const clickedElement = event.target
    if (!(clickedElement instanceof Element)) return
    if (clickedElement.closest('[data-community-memo-interactive="true"]')) return

    if (!interaction && !isSavingLayout) {
      clearMemoClickTimer()
      onClearSelection()
    }
  }

  const handleMemoSelect = (memo: CommunityMemoItemResponse) => {
    const tappedAt = window.performance.now()
    const lastMemoTap = lastMemoTapRef.current
    const isSameMemoDoubleTap =
      lastMemoTap?.memoUuid === memo.memoUuid &&
      tappedAt - lastMemoTap.tappedAt <= MEMO_DETAIL_DOUBLE_TAP_DELAY_MS

    if (
      isSameMemoDoubleTap &&
      (!memoClickTimerRef.current || memoClickTargetUuidRef.current === memo.memoUuid)
    ) {
      lastMemoTapRef.current = { memoUuid: memo.memoUuid, tappedAt }
      handleMemoOpenDetail(memo.memoUuid)
      return
    }

    clearMemoClickTimer()
    lastMemoTapRef.current = { memoUuid: memo.memoUuid, tappedAt }
    memoClickTargetUuidRef.current = memo.memoUuid
    memoClickTimerRef.current = window.setTimeout(() => {
      onSelectMemo(memo)
      memoClickTimerRef.current = null
      memoClickTargetUuidRef.current = null
    }, MEMO_SELECT_DELAY_MS)
  }

  const handleMemoOpenDetail = (memoUuid: string) => {
    const openedAt = window.performance.now()
    const lastMemoDetailOpen = lastMemoDetailOpenRef.current
    if (
      lastMemoDetailOpen?.memoUuid === memoUuid &&
      openedAt - lastMemoDetailOpen.tappedAt <= MEMO_DETAIL_DOUBLE_TAP_DELAY_MS
    ) {
      return
    }

    lastMemoDetailOpenRef.current = { memoUuid, tappedAt: openedAt }
    clearMemoClickTimer()
    lastMemoTapRef.current = null
    editableDetailTapRef.current = null
    onOpenMemoDetail(memoUuid)
  }

  const handleBeginEditingMove = (event: ReactPointerEvent<HTMLDivElement>) => {
    if (!isEditingLayout || isSavingLayout || event.button !== 0) return
    const point = getWallPoint(event.clientX, event.clientY)
    if (!point) return
    const boundedEditingLayout = getBoundedMemoLayout(editingLayoutDraft) ?? editingLayoutDraft

    event.preventDefault()
    event.stopPropagation()
    event.currentTarget.setPointerCapture(event.pointerId)
    const lastMemoTap = lastMemoTapRef.current
    const shouldOpenDetailOnTap =
      editingMemo !== null &&
      lastMemoTap?.memoUuid === editingMemo.memoUuid &&
      window.performance.now() - lastMemoTap.tappedAt <= MEMO_DETAIL_DOUBLE_TAP_DELAY_MS

    editableDetailTapRef.current = shouldOpenDetailOnTap
      ? {
          memoUuid: editingMemo.memoUuid,
          pointerId: event.pointerId,
          startClientX: event.clientX,
          startClientY: event.clientY,
        }
      : null
    editingLayoutDraftRef.current = boundedEditingLayout
    onEditingLayoutChange(boundedEditingLayout)
    const nextInteraction = {
      type: 'drag-edit' as const,
      pointerId: event.pointerId,
      pointerOffsetX: point.x - boundedEditingLayout.positionX,
      pointerOffsetY: point.y - boundedEditingLayout.positionY,
    }
    interactionRef.current = nextInteraction
    interactionChangedRef.current = false
    setInteraction(nextInteraction)
  }

  const handleBeginEditingRotate = (event: ReactPointerEvent<HTMLButtonElement>) => {
    if (!isEditingLayout || isSavingLayout || event.button !== 0) return
    const boundedEditingLayout = getBoundedMemoLayout(editingLayoutDraft) ?? editingLayoutDraft

    event.preventDefault()
    event.stopPropagation()
    event.currentTarget.setPointerCapture(event.pointerId)
    editingLayoutDraftRef.current = boundedEditingLayout
    onEditingLayoutChange(boundedEditingLayout)
    interactionRef.current = { type: 'rotate-edit', pointerId: event.pointerId }
    interactionChangedRef.current = false
    setInteraction({ type: 'rotate-edit', pointerId: event.pointerId })
  }

  const handleBeginPendingRotate = (event: ReactPointerEvent<HTMLButtonElement>) => {
    if (isAttachingMemo || event.button !== 0) return
    event.preventDefault()
    event.stopPropagation()
    event.currentTarget.setPointerCapture(event.pointerId)
    interactionRef.current = { type: 'rotate-pending', pointerId: event.pointerId }
    interactionChangedRef.current = false
    setInteraction({ type: 'rotate-pending', pointerId: event.pointerId })
  }

  const handleWallViewportPointerDown = (event: ReactPointerEvent<HTMLElement>) => {
    if (pendingMemo || event.button !== 0) return
    if (!(event.target instanceof Element)) return
    if (event.target.closest('[data-community-memo-interactive="true"]')) return

    const visibleArea = visibleAreaRef.current
    if (!visibleArea) return

    panStateRef.current = {
      pointerId: event.pointerId,
      startX: event.clientX,
      scrollLeft: visibleArea.scrollLeft,
    }
    visibleArea.setPointerCapture(event.pointerId)
  }

  const handleWallViewportPointerMove = (event: ReactPointerEvent<HTMLElement>) => {
    const panState = panStateRef.current
    const visibleArea = visibleAreaRef.current
    if (!panState || !visibleArea || panState.pointerId !== event.pointerId) return

    visibleArea.scrollLeft = panState.scrollLeft - (event.clientX - panState.startX)
    lockWallVerticalScroll(visibleArea)
  }

  const handleWallViewportPointerEnd = (event: ReactPointerEvent<HTMLElement>) => {
    if (panStateRef.current?.pointerId === event.pointerId) {
      panStateRef.current = null
    }
  }

  const handleWallViewportWheel = (event: ReactWheelEvent<HTMLElement>) => {
    if (event.ctrlKey) return

    const visibleArea = visibleAreaRef.current
    if (!visibleArea) return

    const horizontalDelta =
      Math.abs(event.deltaX) > Math.abs(event.deltaY) ? event.deltaX : event.deltaY
    if (horizontalDelta === 0) return

    event.preventDefault()
    visibleArea.scrollLeft += horizontalDelta
    lockWallVerticalScroll(visibleArea)
  }

  const handleWallViewportScroll = () => {
    const visibleArea = visibleAreaRef.current
    if (!visibleArea) return

    lockWallVerticalScroll(visibleArea)
  }

  const scaledWallWidth = WALL_WIDTH * wallScale
  const scaledWallHeight = WALL_HEIGHT * wallScale
  const wallViewportWidth = Math.max(scaledWallWidth, viewportSize.width)
  const wallViewportHeight = Math.max(scaledWallHeight, viewportSize.height)
  const wallOffsetX = (wallViewportWidth - scaledWallWidth) / 2
  const wallOffsetY = (wallViewportHeight - scaledWallHeight) / 2

  return (
    <section
      ref={visibleAreaRef}
      data-community-wall="true"
      onClick={handleBlankSurfaceClick}
      onPointerDown={handleWallViewportPointerDown}
      onPointerMove={handleWallViewportPointerMove}
      onPointerUp={handleWallViewportPointerEnd}
      onPointerCancel={handleWallViewportPointerEnd}
      onWheel={handleWallViewportWheel}
      onScroll={handleWallViewportScroll}
      className={cn(
        'absolute inset-0 z-0 overflow-x-auto overflow-y-hidden overscroll-x-contain overscroll-y-none bg-surface-default [scrollbar-width:none] [&::-webkit-scrollbar]:hidden',
        !isWallManipulating && 'cursor-grab active:cursor-grabbing [touch-action:pan-x]',
        isWallManipulating && 'touch-none',
      )}
    >
      <div
        className="relative"
        style={{
          width: wallViewportWidth,
          height: wallViewportHeight,
        }}
      >
      <div
        ref={wallRef}
        role="presentation"
        onPointerMove={handleWallPointerMove}
        onClick={handleWallClick}
        className={cn(
          'absolute overflow-visible rounded-[0.45rem] bg-surface-default shadow-[0_24px_60px_rgb(53_45_32_/_24%)]',
          pendingMemo &&
            (isPendingPlacementInsideVisibleArea
              ? 'cursor-copy ring-4 ring-primary-5'
              : 'cursor-not-allowed ring-4 ring-primary-5'),
          isEditingLayout && 'cursor-default ring-4 ring-primary-5',
          (isAttachingMemo || isSavingLayout) && 'cursor-wait',
        )}
        style={{
          left: wallOffsetX,
          top: wallOffsetY,
          width: WALL_WIDTH,
          height: WALL_HEIGHT,
          transform: `scale(${wallScale})`,
          transformOrigin: 'top left',
        }}
      >
        <Image
          src={WALL_BACKGROUND_IMAGE}
          alt=""
          fill
          priority
          unoptimized
          sizes={`${WALL_WIDTH}px`}
          aria-hidden="true"
          draggable={false}
          onDragStart={(event) => event.preventDefault()}
          className="pointer-events-none z-0 select-none object-cover [-webkit-user-drag:none]"
        />

        <div
          aria-hidden={memoStatus === 'loading'}
          className="absolute overflow-hidden"
          style={ATTACHABLE_SURFACE_STYLE}
        >
          <div className="absolute" style={ATTACHABLE_MEMO_LAYER_STYLE}>
            {memos
              .filter((memo) => memo.memoUuid !== editingMemo?.memoUuid)
              .map((memo) => {
                const displayMemo = getDisplayMemo(memo)

                return (
                  <CommunityMemoCard
                    key={memo.memoUuid}
                    memo={displayMemo}
                    isActive={selectedMemoUuid === memo.memoUuid}
                    playbackImageUrl={memoPlaybackImageUrls[memo.memoUuid]}
                    isPlaybackPaused={isPlaybackPaused}
                    placementMotion={enteringMemoUuids.has(memo.memoUuid) ? 'attach' : undefined}
                    isInteractionDisabled={isWallManipulating}
                    onSelect={handleMemoSelect}
                    onOpenDetail={handleMemoOpenDetail}
                  />
                )
              })}

            {exitingMemos.map(({ memo, removalKey }) => (
              <CommunityMemoCard
                key={removalKey}
                memo={getDisplayMemo(memo)}
                isActive={false}
                playbackImageUrl={memoPlaybackImageUrls[memo.memoUuid]}
                isPlaybackPaused={isPlaybackPaused}
                placementMotion="detach"
                isInteractionDisabled
                onSelect={handleMemoSelect}
                onOpenDetail={handleMemoOpenDetail}
              />
            ))}

            {isEditingLayout && (
              <EditableMemoPreview
                memo={editingMemo}
                playbackImageUrl={memoPlaybackImageUrls[editingMemo.memoUuid]}
                isPlaybackPaused={isPlaybackPaused}
                layout={clampMemoLayoutToAttachableSurface(editingLayoutDraft)}
                disabled={isSavingLayout}
                isFluttering={
                  interaction?.type === 'drag-edit' || interaction?.type === 'rotate-edit'
                }
                placementMotion={isSavingLayout ? 'attach' : 'release'}
                onBeginMove={handleBeginEditingMove}
                onBeginRotate={handleBeginEditingRotate}
              />
            )}

            {pendingMemo && (
              <PendingMemoPreview
                pendingMemo={pendingMemo}
                placement={cursorPlacement}
                isPlaybackPaused={isPlaybackPaused}
                isAttachingMemo={isAttachingMemo}
                isPlacementInsideVisibleArea={isPendingPlacementInsideVisibleArea}
                isFluttering={interaction?.type === 'rotate-pending' || !isAttachingMemo}
                placementMotion={isAttachingMemo ? 'attach' : undefined}
                onBeginRotate={handleBeginPendingRotate}
              />
            )}
          </div>
        </div>

        {memoStatus === 'loading' && !isWallManipulating && (
          <div className="absolute inset-0 z-[11000] grid place-items-center bg-white/45 backdrop-blur-[1px]">
            <p className="body-b rounded-[0.45rem] bg-white px-4 py-3 text-fg-primary shadow-sm">
              메모를 불러오는 중
            </p>
          </div>
        )}

        {memoStatus === 'success' && memos.length === 0 && !isWallManipulating && (
          <div className="absolute left-1/2 top-1/2 z-[11000] w-[22rem] -translate-x-1/2 -translate-y-1/2 rounded-[0.5rem] border border-border-default bg-white/86 p-6 text-center shadow-sm">
            <p className="h3-b text-fg-primary">아직 붙은 메모가 없어요.</p>
            <p className="body-r mt-2 text-fg-secondary">
              첫 번째 스냅샷을 벽에 남겨보세요.
            </p>
          </div>
        )}

        {memoStatus === 'error' && !isWallManipulating && (
          <div className="absolute left-1/2 top-1/2 z-[11000] w-[24rem] -translate-x-1/2 -translate-y-1/2 rounded-[0.5rem] border border-border-default bg-white/90 p-6 text-center shadow-sm">
            <p className="h3-b text-fg-primary">벽을 불러오지 못했어요.</p>
            <p className="body-r mt-2 text-fg-secondary">{memoError}</p>
            <button
              type="button"
              onClick={(event) => {
                event.stopPropagation()
                onRetry()
              }}
              className="body-b mt-4 h-10 rounded-[0.45rem] bg-primary-1 px-4 text-fg-primary"
            >
              다시 불러오기
            </button>
          </div>
        )}
      </div>
      </div>
    </section>
  )
}

function EditableMemoPreview({
  memo,
  playbackImageUrl,
  isPlaybackPaused,
  layout,
  disabled,
  isFluttering,
  placementMotion,
  onBeginMove,
  onBeginRotate,
}: {
  memo: CommunityMemoItemResponse
  playbackImageUrl?: string | null
  isPlaybackPaused: boolean
  layout: CommunityMemoLayoutDraft
  disabled: boolean
  isFluttering: boolean
  placementMotion?: MemoPlacementMotion
  onBeginMove: (event: ReactPointerEvent<HTMLDivElement>) => void
  onBeginRotate: (event: ReactPointerEvent<HTMLButtonElement>) => void
}) {
  return (
    <MemoSurface
      imageUrl={
        isPlaybackPaused
          ? getStaticCommunityMemoImageUrl(memo)
          : playbackImageUrl || memo.memoThumbnailImageUrl || memo.memoImageUrl
      }
      tone={getMemoTone(memo)}
      layout={layout}
      disabled={disabled}
      isFluttering={isFluttering}
      placementMotion={placementMotion}
      onBeginMove={onBeginMove}
      onBeginRotate={onBeginRotate}
    />
  )
}

function PendingMemoPreview({
  pendingMemo,
  placement,
  isPlaybackPaused,
  isAttachingMemo,
  isPlacementInsideVisibleArea,
  isFluttering,
  placementMotion,
  onBeginRotate,
}: {
  pendingMemo: CommunityPendingMemoPlacement
  placement: CommunityMemoLayoutDraft
  isPlaybackPaused: boolean
  isAttachingMemo: boolean
  isPlacementInsideVisibleArea: boolean
  isFluttering: boolean
  placementMotion?: MemoPlacementMotion
  onBeginRotate: (event: ReactPointerEvent<HTMLButtonElement>) => void
}) {
  return (
    <MemoSurface
      imageUrl={
        isPlaybackPaused
          ? getStaticCommunityImageUrl(pendingMemo.previewUrl)
          : pendingMemo.previewUrl
      }
      tone={getMemoTone(pendingMemo)}
      layout={placement}
      disabled={isAttachingMemo}
      isPlacementInsideVisibleArea={isPlacementInsideVisibleArea}
      isFluttering={isFluttering}
      placementMotion={placementMotion}
      onBeginRotate={onBeginRotate}
    />
  )
}

function MemoSurface({
  imageUrl,
  tone,
  layout,
  disabled,
  isPlacementInsideVisibleArea = true,
  isFluttering,
  placementMotion,
  onBeginMove,
  onBeginRotate,
}: {
  imageUrl: string | null
  tone: string
  layout: CommunityMemoLayoutDraft
  disabled: boolean
  isPlacementInsideVisibleArea?: boolean
  isFluttering: boolean
  placementMotion?: MemoPlacementMotion
  onBeginMove?: (event: ReactPointerEvent<HTMLDivElement>) => void
  onBeginRotate: (event: ReactPointerEvent<HTMLButtonElement>) => void
}) {
  return (
    <div
      data-community-memo-interactive="true"
      onPointerDown={onBeginMove}
      onClick={(event) => {
        if (onBeginMove) event.stopPropagation()
      }}
      aria-invalid={!isPlacementInsideVisibleArea}
      className={cn(
        'group absolute h-[160px] w-[160px] touch-none select-none',
        onBeginMove && !disabled && 'cursor-grab active:cursor-grabbing',
        (disabled || !isPlacementInsideVisibleArea) && 'opacity-60',
      )}
      style={{
        left: `calc(50% + ${layout.positionX}px)`,
        top: `calc(50% + ${layout.positionY}px)`,
        zIndex: 10_000,
        transform: `translate(-50%, -50%) rotate(${layout.rotationDeg}deg)`,
      }}
    >
      <span
        data-community-memo-placement={placementMotion}
        className="community-memo-placement absolute inset-0"
      >
        <PostItNote
          shape="square"
          motion={disabled ? 'none' : isFluttering ? 'active' : 'hover'}
          selected={Boolean(onBeginMove)}
          className="absolute inset-0 h-full w-full drop-shadow-[0_12px_18px_rgb(66_45_25_/_18%)]"
          style={{ color: tone }}
        />
        <span
          data-post-it-art-motion={disabled ? undefined : isFluttering ? 'active' : 'hover'}
          className="post-it-note-art absolute inset-x-4 bottom-5 top-7 overflow-hidden rounded-[0.35rem]"
        >
          {imageUrl && (
            <Image
              src={imageUrl}
              alt=""
              fill
              sizes="160px"
              unoptimized
              draggable={false}
              className="object-contain"
            />
          )}
        </span>
      </span>
      <button
        type="button"
        aria-label="메모 회전"
        onPointerDown={onBeginRotate}
        onClick={(event) => event.stopPropagation()}
        disabled={disabled}
        className="pointer-events-auto absolute left-1/2 top-0 grid size-8 -translate-x-1/2 -translate-y-1/2 touch-none place-items-center rounded-full border border-border-default bg-white text-fg-secondary shadow-[0_8px_16px_rgb(71_68_112_/_18%)] disabled:cursor-not-allowed disabled:opacity-60"
      >
        <RotateCw className="size-4" />
      </button>
    </div>
  )
}
