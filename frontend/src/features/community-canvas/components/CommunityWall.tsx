'use client'

import { useEffect, useRef, useState, type MouseEvent } from 'react'
import Image from 'next/image'
import { RotateCw, X } from 'lucide-react'
import { PostItNote } from '@/shared/components/PostItNote'
import { cn } from '@/shared/libs'
import type { CommunityMemoItemResponse } from '@/shared/types'
import type { CommunityMemoLayoutDraft, CommunityPendingMemoPlacement } from '../hooks'
import { getCommunityMemoColor } from '../utils'
import { CommunityMemoCard } from './CommunityMemoCard'

interface CommunityWallProps {
  memos: CommunityMemoItemResponse[]
  selectedMemoUuid: string | null
  memoStatus: 'idle' | 'loading' | 'success' | 'error'
  memoError: string | null
  pendingMemo: CommunityPendingMemoPlacement | null
  editingMemo: CommunityMemoItemResponse | null
  editingLayoutDraft: CommunityMemoLayoutDraft | null
  nextZIndex: number
  isAttachingMemo: boolean
  isSavingLayout: boolean
  onSelectMemo: (memo: CommunityMemoItemResponse) => void
  onClearSelection: () => void
  onOpenMemoDetail: (memoUuid: string) => void
  onAttachPendingMemo: (placement: CommunityMemoLayoutDraft) => void
  onCancelPendingMemo: () => void
  onEditingLayoutChange: (partialLayout: Partial<CommunityMemoLayoutDraft>) => void
  onSaveEditingLayout: (layout?: CommunityMemoLayoutDraft) => void
  onRetry: () => void
}

const WALL_WIDTH = 1672
const WALL_HEIGHT = 941
const MEMO_WIDTH = 184
const MEMO_HEIGHT = 156
const WALL_BACKGROUND_IMAGE = '/images/community-canvas/wall-bg.png'
const WALL_FOREGROUND_IMAGE = '/images/community-canvas/wall-fg.png'

type WallPoint = {
  x: number
  y: number
}

type WallInteraction =
  | { type: 'drag-edit'; pointerOffsetX: number; pointerOffsetY: number }
  | { type: 'rotate-edit' }
  | { type: 'rotate-pending' }

function normalizeRotation(rotationDeg: number) {
  const normalized = ((((rotationDeg + 180) % 360) + 360) % 360) - 180
  return Math.round(normalized * 10) / 10
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

export function CommunityWall({
  memos,
  selectedMemoUuid,
  memoStatus,
  memoError,
  pendingMemo,
  editingMemo,
  editingLayoutDraft,
  nextZIndex,
  isAttachingMemo,
  isSavingLayout,
  onSelectMemo,
  onClearSelection,
  onOpenMemoDetail,
  onAttachPendingMemo,
  onCancelPendingMemo,
  onEditingLayoutChange,
  onSaveEditingLayout,
  onRetry,
}: CommunityWallProps) {
  const wallRef = useRef<HTMLDivElement>(null)
  const memoClickTimerRef = useRef<number | null>(null)
  const interactionRef = useRef<WallInteraction | null>(null)
  const interactionChangedRef = useRef(false)
  const editingLayoutDraftRef = useRef<CommunityMemoLayoutDraft | null>(null)
  const cursorPlacementRef = useRef<CommunityMemoLayoutDraft | null>(null)
  const hasCursorPositionRef = useRef(false)
  const isSavingLayoutRef = useRef(isSavingLayout)
  const saveEditingLayoutRef = useRef(onSaveEditingLayout)
  const [wallScale, setWallScale] = useState(1)
  const [cursorPlacement, setCursorPlacement] = useState<CommunityMemoLayoutDraft>({
    positionX: 0,
    positionY: 0,
    zIndex: 1,
    rotationDeg: 0,
  })
  const [interaction, setInteraction] = useState<WallInteraction | null>(null)
  const isEditingLayout = editingMemo !== null && editingLayoutDraft !== null
  const isWallManipulating = pendingMemo !== null || isEditingLayout

  useEffect(() => {
    let cancelled = false

    const updateWallScale = () => {
      setWallScale(Math.max(window.innerWidth / WALL_WIDTH, window.innerHeight / WALL_HEIGHT))
    }

    void (async () => {
      await Promise.resolve()
      if (!cancelled) updateWallScale()
    })()

    window.addEventListener('resize', updateWallScale)
    return () => {
      cancelled = true
      window.removeEventListener('resize', updateWallScale)
    }
  }, [])

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
    const handleMouseUp = () => {
      const currentInteraction = interactionRef.current
      if (!currentInteraction) return

      if (
        (currentInteraction.type === 'drag-edit' || currentInteraction.type === 'rotate-edit') &&
        editingLayoutDraftRef.current &&
        interactionChangedRef.current &&
        !isSavingLayoutRef.current
      ) {
        saveEditingLayoutRef.current(editingLayoutDraftRef.current)
      }

      interactionRef.current = null
      interactionChangedRef.current = false
      setInteraction(null)
    }

    window.addEventListener('mouseup', handleMouseUp)
    return () => window.removeEventListener('mouseup', handleMouseUp)
  }, [])

  useEffect(() => {
    return () => {
      if (memoClickTimerRef.current) window.clearTimeout(memoClickTimerRef.current)
    }
  }, [])

  const clearMemoClickTimer = () => {
    if (!memoClickTimerRef.current) return
    window.clearTimeout(memoClickTimerRef.current)
    memoClickTimerRef.current = null
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

  const updateCursorPlacement = (
    placement: CommunityMemoLayoutDraft,
    options: { hasPosition?: boolean } = {},
  ) => {
    cursorPlacementRef.current = placement
    if (options.hasPosition) {
      hasCursorPositionRef.current = true
    }
    setCursorPlacement(placement)
  }

  const getPendingPlacementFromPoint = (point: WallPoint) => {
    const placement = {
      positionX: point.x,
      positionY: point.y,
      zIndex: nextZIndex,
      rotationDeg: cursorPlacement.rotationDeg,
    }

    return {
      ...placement,
      zIndex: getStackedZIndex({
        layout: placement,
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

    return {
      ...placement,
      zIndex: getStackedZIndex({
        layout: placement,
        memos,
        excludeMemoUuid: editingMemo.memoUuid,
        fallbackZIndex: editingLayoutDraft.zIndex,
      }),
    }
  }

  const handleWallMouseMove = (event: MouseEvent<HTMLDivElement>) => {
    const point = getWallPoint(event.clientX, event.clientY)
    if (!point) return

    if (interaction?.type === 'rotate-edit' && editingLayoutDraft) {
      const nextLayout = {
        ...editingLayoutDraft,
        rotationDeg: getRotationFromPoint(point, editingLayoutDraft),
      }
      editingLayoutDraftRef.current = nextLayout
      interactionChangedRef.current = true
      onEditingLayoutChange({ rotationDeg: nextLayout.rotationDeg })
      return
    }

    if (interaction?.type === 'drag-edit' && isEditingLayout && !isSavingLayout) {
      const nextPlacement = getEditingPlacementFromPoint(point, {
        x: interaction.pointerOffsetX,
        y: interaction.pointerOffsetY,
      })
      if (nextPlacement) {
        editingLayoutDraftRef.current = nextPlacement
        interactionChangedRef.current = true
        onEditingLayoutChange(nextPlacement)
      }
      return
    }

    if (interaction?.type === 'rotate-pending') {
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
      onAttachPendingMemo(
        visualPlacement && hasCursorPositionRef.current
          ? visualPlacement
          : getPendingPlacementFromPoint(point),
      )
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
    clearMemoClickTimer()
    memoClickTimerRef.current = window.setTimeout(() => {
      onSelectMemo(memo)
      memoClickTimerRef.current = null
    }, 220)
  }

  const handleMemoOpenDetail = (memoUuid: string) => {
    clearMemoClickTimer()
    onOpenMemoDetail(memoUuid)
  }

  const handleBeginEditingMove = (event: MouseEvent<HTMLDivElement>) => {
    if (!isEditingLayout || isSavingLayout || event.button !== 0) return
    const point = getWallPoint(event.clientX, event.clientY)
    if (!point) return

    event.preventDefault()
    event.stopPropagation()
    const nextInteraction = {
      type: 'drag-edit' as const,
      pointerOffsetX: point.x - editingLayoutDraft.positionX,
      pointerOffsetY: point.y - editingLayoutDraft.positionY,
    }
    interactionRef.current = nextInteraction
    interactionChangedRef.current = false
    setInteraction(nextInteraction)
  }

  const handleBeginEditingRotate = (event: MouseEvent<HTMLButtonElement>) => {
    if (isSavingLayout || event.button !== 0) return
    event.preventDefault()
    event.stopPropagation()
    interactionRef.current = { type: 'rotate-edit' }
    interactionChangedRef.current = false
    setInteraction({ type: 'rotate-edit' })
  }

  const handleBeginPendingRotate = (event: MouseEvent<HTMLButtonElement>) => {
    if (isAttachingMemo || event.button !== 0) return
    event.preventDefault()
    event.stopPropagation()
    interactionRef.current = { type: 'rotate-pending' }
    interactionChangedRef.current = false
    setInteraction({ type: 'rotate-pending' })
  }

  return (
    <section
      data-community-wall="true"
      onClick={handleBlankSurfaceClick}
      className="absolute inset-0 z-0 overflow-hidden bg-surface-default"
    >
      <div
        ref={wallRef}
        role="presentation"
        onMouseMove={handleWallMouseMove}
        onClick={handleWallClick}
        className={cn(
          'absolute left-1/2 top-1/2 overflow-hidden rounded-[0.45rem] bg-surface-default shadow-[0_24px_60px_rgb(53_45_32_/_24%)]',
          pendingMemo && 'cursor-copy ring-4 ring-primary-5',
          isEditingLayout && 'cursor-default ring-4 ring-primary-5',
          (isAttachingMemo || isSavingLayout) && 'cursor-wait',
        )}
        style={{
          width: WALL_WIDTH,
          height: WALL_HEIGHT,
          transform: `translate(-50%, -50%) scale(${wallScale})`,
          transformOrigin: 'center',
        }}
      >
        <Image
          src={WALL_BACKGROUND_IMAGE}
          alt=""
          fill
          priority
          sizes={`${WALL_WIDTH}px`}
          aria-hidden="true"
          className="pointer-events-none z-0 select-none object-cover"
        />

        {memos
          .filter((memo) => memo.memoUuid !== editingMemo?.memoUuid)
          .map((memo) => (
            <CommunityMemoCard
              key={memo.memoUuid}
              memo={memo}
              isActive={selectedMemoUuid === memo.memoUuid}
              isInteractionDisabled={isWallManipulating}
              onSelect={handleMemoSelect}
              onOpenDetail={handleMemoOpenDetail}
            />
          ))}

        {isEditingLayout && (
          <EditableMemoPreview
            memo={editingMemo}
            layout={editingLayoutDraft}
            disabled={isSavingLayout}
            isFluttering={interaction?.type === 'drag-edit' || interaction?.type === 'rotate-edit'}
            onBeginMove={handleBeginEditingMove}
            onBeginRotate={handleBeginEditingRotate}
          />
        )}

        {pendingMemo && (
          <>
            <PendingCancelButton
              isAttachingMemo={isAttachingMemo}
              onCancelPendingMemo={onCancelPendingMemo}
            />
            <PendingMemoPreview
              pendingMemo={pendingMemo}
              placement={cursorPlacement}
              isAttachingMemo={isAttachingMemo}
              isFluttering={interaction?.type === 'rotate-pending' || !isAttachingMemo}
              onBeginRotate={handleBeginPendingRotate}
            />
          </>
        )}

        <Image
          src={WALL_FOREGROUND_IMAGE}
          alt=""
          fill
          sizes={`${WALL_WIDTH}px`}
          aria-hidden="true"
          className="pointer-events-none z-[9000] select-none object-cover"
        />

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
              className="body-b mt-4 h-10 rounded-[0.45rem] bg-primary-1 px-4 text-fg-inverse"
            >
              다시 불러오기
            </button>
          </div>
        )}
      </div>
    </section>
  )
}

function PendingCancelButton({
  isAttachingMemo,
  onCancelPendingMemo,
}: {
  isAttachingMemo: boolean
  onCancelPendingMemo: () => void
}) {
  return (
    <button
      type="button"
      aria-label="메모 부착 취소"
      onClick={(event) => {
        event.stopPropagation()
        onCancelPendingMemo()
      }}
      disabled={isAttachingMemo}
      className="absolute right-5 top-5 z-[10001] grid size-10 place-items-center rounded-full bg-white text-fg-secondary shadow-[0_10px_22px_rgb(61_43_22_/_16%)] disabled:cursor-not-allowed disabled:opacity-60"
    >
      <X className="size-5" />
    </button>
  )
}

function EditableMemoPreview({
  memo,
  layout,
  disabled,
  isFluttering,
  onBeginMove,
  onBeginRotate,
}: {
  memo: CommunityMemoItemResponse
  layout: CommunityMemoLayoutDraft
  disabled: boolean
  isFluttering: boolean
  onBeginMove: (event: MouseEvent<HTMLDivElement>) => void
  onBeginRotate: (event: MouseEvent<HTMLButtonElement>) => void
}) {
  return (
    <MemoSurface
      imageUrl={memo.memoThumbnailImageUrl || memo.memoImageUrl}
      tone={getMemoTone(memo)}
      layout={layout}
      disabled={disabled}
      isFluttering={isFluttering}
      onBeginMove={onBeginMove}
      onBeginRotate={onBeginRotate}
    />
  )
}

function PendingMemoPreview({
  pendingMemo,
  placement,
  isAttachingMemo,
  isFluttering,
  onBeginRotate,
}: {
  pendingMemo: CommunityPendingMemoPlacement
  placement: CommunityMemoLayoutDraft
  isAttachingMemo: boolean
  isFluttering: boolean
  onBeginRotate: (event: MouseEvent<HTMLButtonElement>) => void
}) {
  return (
    <MemoSurface
      imageUrl={pendingMemo.previewUrl}
      tone={getMemoTone(pendingMemo)}
      layout={placement}
      disabled={isAttachingMemo}
      isFluttering={isFluttering}
      onBeginRotate={onBeginRotate}
    />
  )
}

function MemoSurface({
  imageUrl,
  tone,
  layout,
  disabled,
  isFluttering,
  onBeginMove,
  onBeginRotate,
}: {
  imageUrl: string
  tone: string
  layout: CommunityMemoLayoutDraft
  disabled: boolean
  isFluttering: boolean
  onBeginMove?: (event: MouseEvent<HTMLDivElement>) => void
  onBeginRotate: (event: MouseEvent<HTMLButtonElement>) => void
}) {
  return (
    <div
      data-community-memo-interactive="true"
      onMouseDown={onBeginMove}
      onClick={(event) => {
        if (onBeginMove) event.stopPropagation()
      }}
      className={cn(
        'group absolute h-[156px] w-[184px]',
        onBeginMove && !disabled && 'cursor-grab active:cursor-grabbing',
        disabled && 'opacity-60',
      )}
      style={{
        left: `calc(50% + ${layout.positionX}px)`,
        top: `calc(50% + ${layout.positionY}px)`,
        zIndex: 10_000,
        transform: `translate(-50%, -50%) rotate(${layout.rotationDeg}deg)`,
      }}
    >
      <PostItNote
        motion={disabled ? 'none' : isFluttering ? 'active' : 'hover'}
        selected={Boolean(onBeginMove)}
        className="absolute inset-0 h-full w-full drop-shadow-[0_12px_18px_rgb(66_45_25_/_18%)]"
        style={{ color: tone }}
      />
      <span
        data-post-it-art-motion={disabled ? undefined : isFluttering ? 'active' : 'hover'}
        className="post-it-note-art absolute inset-x-5 bottom-6 top-8 overflow-hidden rounded-[0.35rem]"
      >
        <Image
          src={imageUrl}
          alt=""
          fill
          sizes="184px"
          unoptimized
          draggable={false}
          className="object-contain"
        />
      </span>
      <button
        type="button"
        aria-label="메모 회전"
        onMouseDown={onBeginRotate}
        onClick={(event) => event.stopPropagation()}
        disabled={disabled}
        className="pointer-events-auto absolute left-1/2 top-0 grid size-8 -translate-x-1/2 -translate-y-1/2 place-items-center rounded-full border border-border-default bg-white text-fg-secondary shadow-[0_8px_16px_rgb(61_43_22_/_18%)] disabled:cursor-not-allowed disabled:opacity-60"
      >
        <RotateCw className="size-4" />
      </button>
    </div>
  )
}
