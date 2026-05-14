'use client'

import { useEffect, useState } from 'react'
import Image from 'next/image'
import { Image as KonvaImage } from 'react-konva'
import {
  Brush,
  Eraser,
  ImagePlus,
  PaintBucket,
  Redo2,
  Send,
  Trash2,
  Undo2,
  X,
  type LucideIcon,
} from 'lucide-react'
import { DrawingBoard } from '@/shared/components/DrawingBoard'
import {
  DRAWING_COLORS,
  DRAWING_STROKE_WIDTH_OPTIONS,
} from '@/shared/constants'
import { cn } from '@/shared/libs'
import type { DrawingToolKey, MemoSourceType } from '@/shared/types'
import { PostItNote } from '@/shared/components/PostItNote'
import { COMMUNITY_COMPOSER_BOARD_SIZE, type useCommunityComposer } from '../hooks'
import {
  COMMUNITY_MEMO_COLOR_OPTIONS,
  DEFAULT_COMMUNITY_MEMO_COLOR,
} from '../utils'
import { CommunityGalleryPicker } from './CommunityGalleryPicker'

interface CommunityComposerModalProps {
  composer: ReturnType<typeof useCommunityComposer>
}

type ToolActionKey = DrawingToolKey | 'undo' | 'redo' | 'clear'

const SOURCE_TABS: Array<{ key: MemoSourceType; label: string }> = [
  { key: 'DIRECT', label: '직접 작성' },
  { key: 'GALLERY', label: '갤러리' },
]

const TOOL_ACTIONS: Array<{
  key: ToolActionKey
  label: string
  icon: LucideIcon
}> = [
  { key: 'pencil', label: '브러시', icon: Brush },
  { key: 'eraser', label: '지우개', icon: Eraser },
  { key: 'bucket', label: '채우기', icon: PaintBucket },
  { key: 'undo', label: '되돌리기', icon: Undo2 },
  { key: 'redo', label: '다시 실행', icon: Redo2 },
  { key: 'clear', label: '전체 지우기', icon: Trash2 },
]

const GALLERY_HIDDEN_TOOL_ACTIONS: ToolActionKey[] = ['bucket']

function getContainedImageFrame({
  imageWidth,
  imageHeight,
  frameWidth,
  frameHeight,
}: {
  imageWidth: number
  imageHeight: number
  frameWidth: number
  frameHeight: number
}) {
  const imageRatio = imageWidth / imageHeight
  const frameRatio = frameWidth / frameHeight
  const width = imageRatio > frameRatio ? frameWidth : frameHeight * imageRatio
  const height = imageRatio > frameRatio ? frameWidth / imageRatio : frameHeight

  return {
    x: (frameWidth - width) / 2,
    y: (frameHeight - height) / 2,
    width,
    height,
  }
}

export function CommunityComposerModal({ composer }: CommunityComposerModalProps) {
  if (!composer.isComposerOpen) return null

  const { drawingBoard, sourceType, postStatus, backgroundColor } = composer
  const isPosting = postStatus === 'loading'
  const selectedGalleryCanvasImageUrl =
    composer.handoffDraft?.imageUrl ||
    composer.selectedGalleryDetail?.contentUrl || composer.selectedGalleryDetail?.thumbnailUrl
  const selectedGalleryPreviewUrl =
    composer.handoffDraft?.thumbnailUrl || selectedGalleryCanvasImageUrl

  const handlePreparePlacement = () => {
    if (sourceType === 'DIRECT') {
      void composer.prepareDirectMemoPlacement()
      return
    }

    void composer.prepareGalleryMemoPlacement()
  }

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="community-composer-title"
      className="fixed inset-0 z-[var(--z-overlay)] grid place-items-center bg-black/45 p-4"
    >
      <section className="flex max-h-[94vh] w-full max-w-[86rem] flex-col overflow-hidden rounded-[0.5rem] bg-surface-default shadow-[0_24px_60px_rgb(17_24_21_/_30%)]">
        <header className="flex items-center justify-between border-b border-border-default px-5 py-4">
          <div>
            <p className="caption-b text-primary-2">커뮤니티 캔버스</p>
            <h2 id="community-composer-title" className="h2-b text-fg-primary">
              새 메모 붙이기
            </h2>
          </div>
          <button
            type="button"
            aria-label="작성 닫기"
            onClick={composer.closeComposer}
            className="grid size-11 place-items-center rounded-full bg-surface-subtle text-fg-secondary transition hover:bg-surface-muted"
          >
            <X className="size-5" />
          </button>
        </header>

        <div className="flex min-h-0 flex-1 flex-col gap-4 overflow-y-auto p-4">
          <SourceTabs
            sourceType={sourceType}
            onSelectSourceType={composer.selectSourceType}
          />

          <div className="grid min-h-0 gap-4 lg:grid-cols-[minmax(0,1fr)_18rem]">
          <section className="min-w-0">
            {sourceType === 'DIRECT' ? (
              <div className="grid min-h-0 gap-4 xl:grid-cols-[minmax(0,46rem)_18rem]">
                <div className="w-fit max-w-full overflow-x-auto rounded-[0.5rem] border border-border-default bg-surface-subtle p-2 [scrollbar-width:none] [&::-webkit-scrollbar]:hidden">
                  <DrawingBoard
                    boardSize={COMMUNITY_COMPOSER_BOARD_SIZE}
                    backgroundColor={backgroundColor}
                    backgroundCornerRadius={18}
                    gridColor="#ffe0a3"
                    lines={drawingBoard.lines}
                    onDrawStart={drawingBoard.beginDrawing}
                    onDrawMove={drawingBoard.continueDrawing}
                    onDrawEnd={drawingBoard.endDrawing}
                    className="mx-auto"
                  />
                </div>

                <div
                  className={cn(
                    'grid gap-3 rounded-[0.5rem] border border-border-default bg-surface-default p-3',
                    isPosting && 'pointer-events-none opacity-60',
                  )}
                >
                  <DrawingToolStrip
                    selectedToolKey={drawingBoard.selectedToolKey}
                    canUndoDrawing={drawingBoard.canUndoDrawing}
                    canRedoDrawing={drawingBoard.canRedoDrawing}
                    onSelectTool={drawingBoard.setSelectedToolKey}
                    onUndoDrawing={drawingBoard.undoDrawing}
                    onRedoDrawing={drawingBoard.redoDrawing}
                    onClearDrawing={drawingBoard.clearDrawing}
                  />

                  <DrawingBrushPanel
                    colors={DRAWING_COLORS}
                    selectedColor={drawingBoard.selectedColor}
                    selectedOpacity={drawingBoard.selectedOpacity}
                    strokeWidth={drawingBoard.strokeWidth}
                    onSelectColor={drawingBoard.setSelectedColor}
                    onOpacityChange={drawingBoard.setSelectedOpacity}
                    onStrokeWidthChange={drawingBoard.setStrokeWidth}
                  />
                </div>
              </div>
            ) : (
              <div className="grid min-h-0 gap-4 xl:grid-cols-[18rem_minmax(0,46rem)]">
                <div className="grid min-w-0 gap-3">
                  {composer.handoffDraft ? (
                    <CommunityHandoffSourcePanel draft={composer.handoffDraft} />
                  ) : (
                    <CommunityGalleryPicker
                      items={composer.galleryItems}
                      status={composer.galleryStatus}
                      error={composer.galleryError}
                      selectedGalleryId={composer.selectedGalleryId}
                      onLoad={composer.loadGalleryItems}
                      onSelect={(galleryId) => void composer.selectGalleryItem(galleryId)}
                    />
                  )}

                  {selectedGalleryCanvasImageUrl && (
                    <div
                      className={cn(
                        'grid gap-3 rounded-[0.5rem] border border-border-default bg-surface-default p-3',
                        isPosting && 'pointer-events-none opacity-60',
                      )}
                    >
                      <DrawingToolStrip
                        compact
                        hiddenToolKeys={GALLERY_HIDDEN_TOOL_ACTIONS}
                        selectedToolKey={drawingBoard.selectedToolKey}
                        canUndoDrawing={drawingBoard.canUndoDrawing}
                        canRedoDrawing={drawingBoard.canRedoDrawing}
                        onSelectTool={drawingBoard.setSelectedToolKey}
                        onUndoDrawing={drawingBoard.undoDrawing}
                        onRedoDrawing={drawingBoard.redoDrawing}
                        onClearDrawing={drawingBoard.clearDrawing}
                      />

                      <DrawingBrushPanel
                        compact
                        colors={DRAWING_COLORS}
                        selectedColor={drawingBoard.selectedColor}
                        selectedOpacity={drawingBoard.selectedOpacity}
                        strokeWidth={drawingBoard.strokeWidth}
                        onSelectColor={drawingBoard.setSelectedColor}
                        onOpacityChange={drawingBoard.setSelectedOpacity}
                        onStrokeWidthChange={drawingBoard.setStrokeWidth}
                      />
                    </div>
                  )}
                </div>

                <div className="w-fit max-w-full overflow-x-auto rounded-[0.5rem] border border-border-default bg-surface-subtle p-2 [scrollbar-width:none] [&::-webkit-scrollbar]:hidden">
                  {selectedGalleryCanvasImageUrl ? (
                    <DrawingBoard
                      boardSize={COMMUNITY_COMPOSER_BOARD_SIZE}
                      backgroundColor={backgroundColor}
                      backgroundCornerRadius={18}
                      gridColor="transparent"
                      lines={drawingBoard.lines}
                      onDrawStart={drawingBoard.beginDrawing}
                      onDrawMove={drawingBoard.continueDrawing}
                      onDrawEnd={drawingBoard.endDrawing}
                      className="mx-auto"
                      childrenBeforeLines={
                        <GalleryCanvasBaseImage
                          imageUrl={selectedGalleryCanvasImageUrl}
                          boardSize={COMMUNITY_COMPOSER_BOARD_SIZE}
                        />
                      }
                    />
                  ) : (
                    <div className="grid h-[33.75rem] w-[45rem] max-w-full place-items-center rounded-[0.5rem] border border-dashed border-border-default bg-surface-default text-fg-secondary">
                      <ImagePlus className="size-8" />
                    </div>
                  )}
                </div>
              </div>
            )}
          </section>

          <aside className="flex min-h-0 flex-col gap-4 rounded-[0.5rem] border border-border-default bg-surface-subtle p-4">
            <MemoColorPicker
              selectedMemoColor={composer.selectedMemoColor}
              onSelectMemoColor={composer.setSelectedMemoColor}
            />

            <MemoPreview
              sourceType={sourceType}
              memoColor={composer.selectedMemoColor}
              imageUrl={sourceType === 'GALLERY' ? selectedGalleryPreviewUrl : null}
            />

            {sourceType === 'GALLERY' && composer.galleryDetailStatus === 'loading' && (
              <p className="caption-r text-fg-secondary">선택한 항목을 확인하는 중</p>
            )}

            <button
              type="button"
              disabled={isPosting}
              onClick={handlePreparePlacement}
              className="body-b mt-auto inline-flex h-12 w-full items-center justify-center gap-2 rounded-[0.45rem] bg-primary-1 text-fg-inverse transition hover:-translate-y-0.5 disabled:cursor-not-allowed disabled:opacity-60"
            >
              {sourceType === 'DIRECT' ? <Send className="size-5" /> : <ImagePlus className="size-5" />}
              {isPosting ? '메모지 준비 중' : '메모지 들기'}
            </button>
          </aside>
          </div>
        </div>
      </section>
    </div>
  )
}

function CommunityHandoffSourcePanel({
  draft,
}: {
  draft: NonNullable<ReturnType<typeof useCommunityComposer>['handoffDraft']>
}) {
  const previewUrl = draft.thumbnailUrl || draft.imageUrl
  const sourceLabel =
    draft.sourceKind === 'FORTUNE'
      ? '오늘의 운세'
      : draft.sourceKind === 'RELAY'
        ? '릴레이 드로잉'
        : '폰 갤러리'

  return (
    <section className="rounded-[0.5rem] border border-border-default bg-surface-subtle p-4">
      <p className="caption-b text-primary-2">{sourceLabel}</p>
      <h3 className="body-b mt-1 truncate text-fg-primary">{draft.title}</h3>
      <div className="relative mt-4 aspect-[4/3] overflow-hidden rounded-[0.45rem] border border-border-default bg-surface-default">
        <Image
          src={previewUrl}
          alt={`${draft.title} 커뮤니티 게시 원본`}
          fill
          sizes="320px"
          unoptimized
          className="object-contain"
        />
      </div>
    </section>
  )
}

function GalleryCanvasBaseImage({
  imageUrl,
  boardSize,
}: {
  imageUrl: string
  boardSize: typeof COMMUNITY_COMPOSER_BOARD_SIZE
}) {
  const [imageElement, setImageElement] = useState<HTMLImageElement | null>(null)

  useEffect(() => {
    let isCancelled = false

    void (async () => {
      if (!isCancelled) setImageElement(null)

      if (!imageUrl) {
        return
      }

      const nextImageElement = new window.Image()
      nextImageElement.crossOrigin = 'anonymous'
      nextImageElement.onload = () => {
        if (!isCancelled) setImageElement(nextImageElement)
      }
      nextImageElement.onerror = () => {
        if (!isCancelled) setImageElement(null)
      }
      nextImageElement.src = imageUrl
    })()

    return () => {
      isCancelled = true
    }
  }, [imageUrl])

  if (!imageElement || imageElement.naturalWidth <= 0 || imageElement.naturalHeight <= 0) {
    return null
  }

  const imageFrame = getContainedImageFrame({
    imageWidth: imageElement.naturalWidth,
    imageHeight: imageElement.naturalHeight,
    frameWidth: boardSize.width,
    frameHeight: boardSize.height,
  })

  return (
    <KonvaImage
      image={imageElement}
      x={imageFrame.x}
      y={imageFrame.y}
      width={imageFrame.width}
      height={imageFrame.height}
      listening={false}
    />
  )
}

function SourceTabs({
  sourceType,
  onSelectSourceType,
}: {
  sourceType: MemoSourceType
  onSelectSourceType: (sourceType: MemoSourceType) => void
}) {
  return (
    <div className="inline-grid grid-cols-2 self-start rounded-[0.45rem] border border-border-default bg-surface-subtle p-1">
      {SOURCE_TABS.map((tab) => (
        <button
          key={tab.key}
          type="button"
          aria-pressed={sourceType === tab.key}
          onClick={() => onSelectSourceType(tab.key)}
          className={cn(
            'body-b h-11 rounded-[0.35rem] px-5 transition',
            sourceType === tab.key
              ? 'bg-surface-default text-fg-primary shadow-sm'
              : 'text-fg-secondary hover:text-fg-primary',
          )}
        >
          {tab.label}
        </button>
      ))}
    </div>
  )
}

function DrawingToolStrip({
  compact = false,
  hiddenToolKeys = [],
  selectedToolKey,
  canUndoDrawing,
  canRedoDrawing,
  onSelectTool,
  onUndoDrawing,
  onRedoDrawing,
  onClearDrawing,
}: {
  compact?: boolean
  hiddenToolKeys?: ToolActionKey[]
  selectedToolKey: DrawingToolKey
  canUndoDrawing: boolean
  canRedoDrawing: boolean
  onSelectTool: (toolKey: DrawingToolKey) => void
  onUndoDrawing: () => void
  onRedoDrawing: () => void
  onClearDrawing: () => void
}) {
  const handleToolAction = (toolActionKey: ToolActionKey) => {
    if (toolActionKey === 'undo') {
      onUndoDrawing()
      return
    }
    if (toolActionKey === 'redo') {
      onRedoDrawing()
      return
    }
    if (toolActionKey === 'clear') {
      onClearDrawing()
      return
    }
    onSelectTool(toolActionKey)
  }

  return (
    <section className={cn('rounded-[0.45rem] bg-surface-subtle', compact ? 'p-2' : 'p-3')}>
      <p className="caption-b mb-2 text-fg-secondary">도구</p>
      <div className={cn('grid gap-2', compact ? 'grid-cols-5' : 'grid-cols-3')}>
        {TOOL_ACTIONS.filter(
          (toolAction) => !hiddenToolKeys.includes(toolAction.key),
        ).map((toolAction) => {
          const isSelected = selectedToolKey === toolAction.key
          const isDisabled =
            (toolAction.key === 'undo' && !canUndoDrawing) ||
            (toolAction.key === 'redo' && !canRedoDrawing)
          const Icon = toolAction.icon

          return (
            <button
              key={toolAction.key}
              type="button"
              title={toolAction.label}
              aria-label={toolAction.label}
              aria-pressed={isSelected}
              disabled={isDisabled}
              onClick={() => handleToolAction(toolAction.key)}
              className={cn(
                'grid w-full place-items-center rounded-[0.45rem] border border-border-default bg-surface-default text-fg-secondary shadow-sm transition hover:border-primary-1 hover:text-fg-primary',
                compact ? 'h-10' : 'h-11',
                isSelected && 'border-primary-1 bg-primary-5 text-primary-2 ring-2 ring-primary-5',
                isDisabled && 'cursor-not-allowed opacity-40 shadow-none',
              )}
            >
              <Icon className="size-5" />
              <span className="sr-only">{toolAction.label}</span>
            </button>
          )
        })}
      </div>
    </section>
  )
}

function DrawingBrushPanel({
  compact = false,
  colors,
  selectedColor,
  selectedOpacity,
  strokeWidth,
  onSelectColor,
  onOpacityChange,
  onStrokeWidthChange,
}: {
  compact?: boolean
  colors: string[]
  selectedColor: string
  selectedOpacity: number
  strokeWidth: number
  onSelectColor: (color: string) => void
  onOpacityChange: (opacity: number) => void
  onStrokeWidthChange: (strokeWidth: number) => void
}) {
  return (
    <section className={cn('rounded-[0.45rem] bg-surface-subtle', compact ? 'p-2' : 'p-3')}>
      <p className="caption-b mb-2 text-fg-secondary">펜</p>
      <div className={cn('flex flex-wrap', compact ? 'gap-1.5' : 'gap-2')}>
        {colors.slice(0, 20).map((color) => (
          <button
            key={color}
            type="button"
            aria-label={`${color} 색상`}
            aria-pressed={selectedColor === color}
            onClick={() => onSelectColor(color)}
            className={cn(
              'rounded-[0.35rem] border border-border-default shadow-sm transition hover:-translate-y-0.5',
              compact ? 'size-7' : 'size-8',
              selectedColor === color &&
                'ring-2 ring-primary-1 ring-offset-2 ring-offset-surface-subtle',
            )}
            style={{ backgroundColor: color }}
          />
        ))}
      </div>

      <div className={cn('flex flex-wrap items-center gap-2 border-t border-border-default', compact ? 'mt-3 pt-2' : 'mt-4 pt-3')}>
        {DRAWING_STROKE_WIDTH_OPTIONS.map((strokeWidthOption) => (
          <button
            key={strokeWidthOption}
            type="button"
            aria-label={`${strokeWidthOption}px 굵기`}
            aria-pressed={strokeWidth === strokeWidthOption}
            onClick={() => onStrokeWidthChange(strokeWidthOption)}
            className={cn(
              'grid place-items-center rounded-full border border-border-default bg-surface-default shadow-sm transition hover:border-primary-1',
              compact ? 'size-8' : 'size-9',
              strokeWidth === strokeWidthOption && 'border-primary-1 ring-2 ring-primary-5',
            )}
          >
            <span
              className="rounded-full bg-fg-primary"
              style={{ width: strokeWidthOption, height: strokeWidthOption }}
            />
          </button>
        ))}
      </div>

      <input
        type="range"
        min={10}
        max={100}
        step={5}
        value={Math.round(selectedOpacity * 100)}
        aria-label={`투명도 ${Math.round(selectedOpacity * 100)}%`}
        onChange={(event) => onOpacityChange(Number(event.target.value) / 100)}
        className={cn(
          'h-4 w-full cursor-pointer appearance-none rounded-full border border-border-default bg-[linear-gradient(90deg,#ffffff_0%,#d9d9d9_45%,#212121_100%)] [&::-webkit-slider-runnable-track]:h-4 [&::-webkit-slider-runnable-track]:rounded-full [&::-webkit-slider-thumb]:mt-[-1px] [&::-webkit-slider-thumb]:size-[18px] [&::-webkit-slider-thumb]:appearance-none [&::-webkit-slider-thumb]:rounded-full [&::-webkit-slider-thumb]:border-2 [&::-webkit-slider-thumb]:border-fg-primary [&::-webkit-slider-thumb]:bg-surface-default',
          compact ? 'mt-3' : 'mt-4',
        )}
      />
    </section>
  )
}

function MemoColorPicker({
  selectedMemoColor,
  onSelectMemoColor,
}: {
  selectedMemoColor: string
  onSelectMemoColor: (memoColor: string) => void
}) {
  return (
    <section>
      <p className="caption-b mb-2 text-fg-secondary">메모지 색상</p>
      <div className="grid grid-cols-3 gap-2">
        {COMMUNITY_MEMO_COLOR_OPTIONS.map((memoColor) => (
          <button
            key={memoColor.value}
            type="button"
            aria-label={`${memoColor.name} 메모지`}
            aria-pressed={selectedMemoColor === memoColor.value}
            onClick={() => onSelectMemoColor(memoColor.value)}
            className={cn(
              'body-b h-12 rounded-[0.45rem] border border-border-default text-fg-primary transition hover:-translate-y-0.5',
              selectedMemoColor === memoColor.value &&
                'border-primary-1 ring-2 ring-primary-5',
            )}
            style={{ backgroundColor: memoColor.value }}
          >
            {memoColor.name}
          </button>
        ))}
      </div>
    </section>
  )
}

function MemoPreview({
  sourceType,
  memoColor,
  imageUrl,
}: {
  sourceType: MemoSourceType
  memoColor: string
  imageUrl: string | null | undefined
}) {
  return (
    <section className="mt-2 rounded-[0.5rem] border border-border-default bg-surface-default p-4">
      <p className="caption-b mb-3 text-fg-secondary">미리보기</p>
      <div className="relative mx-auto h-[12rem] w-[14rem]">
        <PostItNote
          className="absolute inset-0 h-full w-full drop-shadow-[0_12px_18px_rgb(66_45_25_/_18%)]"
          style={{ color: memoColor || DEFAULT_COMMUNITY_MEMO_COLOR }}
        />
        <span className="absolute inset-x-6 bottom-7 top-9 overflow-hidden rounded-[0.35rem]">
          {imageUrl ? (
            <Image
              src={imageUrl}
              alt="선택한 갤러리 메모 미리보기"
              fill
              sizes="224px"
              unoptimized
              className="object-contain"
            />
          ) : (
            <span className="body-b grid h-full place-items-center text-fg-secondary">
              {sourceType === 'DIRECT' ? '직접 작성' : '갤러리'}
            </span>
          )}
        </span>
      </div>
    </section>
  )
}
