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
import {
  useCommunityCompactViewport,
  useCommunityModalFitScale,
} from './useCommunityModalFitScale'

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

const GALLERY_HIDDEN_TOOL_ACTIONS: ToolActionKey[] = []
const COMPOSER_BOARD_SURFACE_CLASS = 'h-[540px] w-[720px] max-h-full max-w-full'
const COMPOSER_MODAL_WIDTH = 1600
const COMPOSER_MODAL_HEIGHT = 980
const COMPOSER_MODAL_MAX_WIDTH = 1600
const COMPOSER_COMPACT_BOARD_MAX_WIDTH = 656

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
  const modalScale = useCommunityModalFitScale({
    designWidth: COMPOSER_MODAL_WIDTH,
    designHeight: COMPOSER_MODAL_HEIGHT,
    maxWidth: COMPOSER_MODAL_MAX_WIDTH,
    viewportPadding: 32,
  })
  const isCompactViewport = useCommunityCompactViewport()

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

  if (isCompactViewport) {
    return (
      <CommunityComposerCompactModal
        composer={composer}
        isPosting={isPosting}
        selectedGalleryCanvasImageUrl={selectedGalleryCanvasImageUrl}
        selectedGalleryPreviewUrl={selectedGalleryPreviewUrl}
        onPreparePlacement={handlePreparePlacement}
      />
    )
  }

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="community-composer-title"
      className="fixed inset-0 z-[var(--z-overlay)] grid place-items-center overflow-y-auto overflow-x-hidden bg-[#19172a]/50 p-4 backdrop-blur-[2px]"
    >
      <div
        className="relative shrink-0"
        style={{
          width: COMPOSER_MODAL_WIDTH * modalScale,
          height: COMPOSER_MODAL_HEIGHT * modalScale,
        }}
      >
        <section
          className="absolute left-0 top-0 overflow-hidden bg-contain bg-center bg-no-repeat"
          style={{
            width: COMPOSER_MODAL_WIDTH,
            height: COMPOSER_MODAL_HEIGHT,
            transform: `scale(${modalScale})`,
            transformOrigin: 'top left',
            backgroundImage: 'url("/images/community-canvas/ui/modal-composer-frame.svg")',
            backgroundSize: '100% 100%',
          }}
        >
        <header className="absolute left-[5.5%] right-[5.5%] top-[7.1%] flex h-[11.4%] items-center justify-between px-4">
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
            className="grid size-11 place-items-center rounded-full border border-[#ffd66b] bg-[#fff8e1] text-fg-secondary shadow-[0_7px_16px_rgb(71_68_112_/_16%)] transition hover:-translate-y-0.5 hover:bg-white"
          >
            <X className="size-5" />
          </button>
        </header>

        <div className="absolute left-[4.75%] top-[21.55%] h-[4.65%] w-[16.875%]">
          <SourceTabs
            sourceType={sourceType}
            onSelectSourceType={composer.selectSourceType}
          />
        </div>

        <div className="absolute bottom-[8.6%] left-[4.75%] right-[4.75%] top-[29%] grid min-h-0 gap-[1.5%] overflow-hidden xl:grid-cols-[minmax(0,750fr)_320fr_330fr]">
            {sourceType === 'DIRECT' ? (
              <>
                <div className="flex h-full min-w-0 items-center justify-center overflow-visible">
                  <DrawingBoard
                    boardSize={COMMUNITY_COMPOSER_BOARD_SIZE}
                    backgroundColor={backgroundColor}
                    backgroundCornerRadius={18}
                    gridColor="#ffe0a3"
                    lines={drawingBoard.lines}
                    onDrawStart={drawingBoard.beginDrawing}
                    onDrawMove={drawingBoard.continueDrawing}
                    onDrawEnd={drawingBoard.endDrawing}
                    className="overflow-hidden rounded-[1rem] shadow-[0_10px_20px_rgb(78_44_20_/_12%)]"
                  />
                </div>

                <div
                  className={cn(
                    'grid min-h-0 self-center auto-rows-max content-start gap-3 overflow-y-auto rounded-[0.55rem] bg-surface-default p-3 shadow-[0_10px_20px_rgb(78_44_20_/_12%)] [height:min(100%,33.75rem)]',
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
              </>
            ) : (
              <>
                <div className="flex h-full min-w-0 items-center justify-center overflow-visible">
                  {!selectedGalleryCanvasImageUrl && composer.handoffDraft ? (
                    <CommunityHandoffSourcePanel
                      draft={composer.handoffDraft}
                      className={COMPOSER_BOARD_SURFACE_CLASS}
                    />
                  ) : !selectedGalleryCanvasImageUrl ? (
                    <CommunityGalleryPicker
                      className={COMPOSER_BOARD_SURFACE_CLASS}
                      items={composer.galleryItems}
                      status={composer.galleryStatus}
                      error={composer.galleryError}
                      selectedGalleryId={composer.selectedGalleryId}
                      onLoad={composer.loadGalleryItems}
                      onSelect={(galleryId) => void composer.selectGalleryItem(galleryId)}
                    />
                  ) : (
                    <DrawingBoard
                      boardSize={COMMUNITY_COMPOSER_BOARD_SIZE}
                      backgroundColor={backgroundColor}
                      backgroundCornerRadius={18}
                      gridColor="transparent"
                      lines={drawingBoard.lines}
                      onDrawStart={drawingBoard.beginDrawing}
                      onDrawMove={drawingBoard.continueDrawing}
                      onDrawEnd={drawingBoard.endDrawing}
                      className="overflow-hidden rounded-[1rem] shadow-[0_10px_20px_rgb(78_44_20_/_12%)]"
                      childrenBeforeLines={
                        <GalleryCanvasBaseImage
                          imageUrl={selectedGalleryCanvasImageUrl}
                          boardSize={COMMUNITY_COMPOSER_BOARD_SIZE}
                        />
                      }
                    />
                  )}
                </div>

                <div
                  className={cn(
                    'grid min-h-0 self-center auto-rows-max content-start gap-3 overflow-y-auto rounded-[0.55rem] bg-surface-default p-3 shadow-[0_10px_20px_rgb(78_44_20_/_12%)] [height:min(100%,33.75rem)]',
                    isPosting && 'pointer-events-none opacity-60',
                  )}
                >
                  <DrawingToolStrip
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
                    colors={DRAWING_COLORS}
                    selectedColor={drawingBoard.selectedColor}
                    selectedOpacity={drawingBoard.selectedOpacity}
                    strokeWidth={drawingBoard.strokeWidth}
                    onSelectColor={drawingBoard.setSelectedColor}
                    onOpacityChange={drawingBoard.setSelectedOpacity}
                    onStrokeWidthChange={drawingBoard.setStrokeWidth}
                  />
                </div>
              </>
            )}

          <aside className="flex min-h-0 flex-col gap-4 self-center overflow-y-auto overflow-x-hidden rounded-[0.55rem] bg-surface-subtle p-4 shadow-[0_10px_20px_rgb(78_44_20_/_12%)] [height:min(100%,33.75rem)]">
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
              className="body-b sticky bottom-0 mt-auto inline-flex h-12 w-full items-center justify-center gap-2 rounded-[0.45rem] bg-[#FFD95D] text-fg-primary shadow-[0_8px_18px_rgb(78_44_20_/_14%)] transition hover:-translate-y-0.5 disabled:cursor-not-allowed disabled:opacity-60"
            >
              {sourceType === 'DIRECT' ? <Send className="size-5" /> : <ImagePlus className="size-5" />}
              {isPosting ? '메모지 준비 중' : '메모지 들기'}
            </button>
          </aside>
        </div>
        </section>
      </div>
    </div>
  )
}

function CommunityComposerCompactModal({
  composer,
  isPosting,
  selectedGalleryCanvasImageUrl,
  selectedGalleryPreviewUrl,
  onPreparePlacement,
}: {
  composer: ReturnType<typeof useCommunityComposer>
  isPosting: boolean
  selectedGalleryCanvasImageUrl: string | null | undefined
  selectedGalleryPreviewUrl: string | null | undefined
  onPreparePlacement: () => void
}) {
  const { drawingBoard, sourceType, backgroundColor } = composer
  const boardScale = useCommunityModalFitScale({
    designWidth: COMMUNITY_COMPOSER_BOARD_SIZE.width,
    designHeight: COMMUNITY_COMPOSER_BOARD_SIZE.height,
    maxWidth: COMPOSER_COMPACT_BOARD_MAX_WIDTH,
    viewportPadding: 112,
  })

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="community-composer-title"
      className="fixed inset-0 z-[var(--z-overlay)] overflow-y-auto overflow-x-hidden bg-[#19172a]/50 p-3 backdrop-blur-[2px]"
    >
      <section className="mx-auto flex min-h-full w-full max-w-[46rem] flex-col gap-4 rounded-[1.5rem] border-[0.35rem] border-[#f5d96c] bg-[#fff2b3] p-4 shadow-[0_18px_38px_rgb(25_20_40_/_24%)]">
        <header className="flex items-start justify-between gap-3 rounded-[1rem] border border-[#f0d887] bg-[#fffdf1] p-4">
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
            className="grid size-11 shrink-0 place-items-center rounded-full border border-[#ffd66b] bg-[#fff8e1] text-fg-secondary shadow-[0_7px_16px_rgb(71_68_112_/_16%)]"
          >
            <X className="size-5" />
          </button>
        </header>

        <div className="h-12 w-full max-w-[22rem]">
          <SourceTabs
            sourceType={sourceType}
            onSelectSourceType={composer.selectSourceType}
          />
        </div>

        <div className="overflow-hidden rounded-[1rem] bg-white/70 p-3 shadow-[0_10px_20px_rgb(78_44_20_/_12%)]">
          <div
            className="relative mx-auto"
            style={{
              width: COMMUNITY_COMPOSER_BOARD_SIZE.width * boardScale,
              height: COMMUNITY_COMPOSER_BOARD_SIZE.height * boardScale,
            }}
          >
            <div
              className="absolute left-0 top-0"
              style={{
                width: COMMUNITY_COMPOSER_BOARD_SIZE.width,
                height: COMMUNITY_COMPOSER_BOARD_SIZE.height,
                transform: `scale(${boardScale})`,
                transformOrigin: 'top left',
              }}
            >
              {sourceType === 'DIRECT' ? (
                <DrawingBoard
                  boardSize={COMMUNITY_COMPOSER_BOARD_SIZE}
                  backgroundColor={backgroundColor}
                  backgroundCornerRadius={18}
                  gridColor="#ffe0a3"
                  lines={drawingBoard.lines}
                  onDrawStart={drawingBoard.beginDrawing}
                  onDrawMove={drawingBoard.continueDrawing}
                  onDrawEnd={drawingBoard.endDrawing}
                  className="overflow-hidden rounded-[1rem] shadow-[0_10px_20px_rgb(78_44_20_/_12%)]"
                />
              ) : !selectedGalleryCanvasImageUrl && composer.handoffDraft ? (
                <CommunityHandoffSourcePanel
                  draft={composer.handoffDraft}
                  className={COMPOSER_BOARD_SURFACE_CLASS}
                />
              ) : !selectedGalleryCanvasImageUrl ? (
                <CommunityGalleryPicker
                  className={COMPOSER_BOARD_SURFACE_CLASS}
                  items={composer.galleryItems}
                  status={composer.galleryStatus}
                  error={composer.galleryError}
                  selectedGalleryId={composer.selectedGalleryId}
                  onLoad={composer.loadGalleryItems}
                  onSelect={(galleryId) => void composer.selectGalleryItem(galleryId)}
                />
              ) : (
                <DrawingBoard
                  boardSize={COMMUNITY_COMPOSER_BOARD_SIZE}
                  backgroundColor={backgroundColor}
                  backgroundCornerRadius={18}
                  gridColor="transparent"
                  lines={drawingBoard.lines}
                  onDrawStart={drawingBoard.beginDrawing}
                  onDrawMove={drawingBoard.continueDrawing}
                  onDrawEnd={drawingBoard.endDrawing}
                  className="overflow-hidden rounded-[1rem] shadow-[0_10px_20px_rgb(78_44_20_/_12%)]"
                  childrenBeforeLines={
                    <GalleryCanvasBaseImage
                      imageUrl={selectedGalleryCanvasImageUrl}
                      boardSize={COMMUNITY_COMPOSER_BOARD_SIZE}
                    />
                  }
                />
              )}
            </div>
          </div>
        </div>

        <section
          className={cn(
            'grid gap-3 rounded-[0.75rem] bg-surface-default p-3 shadow-[0_10px_20px_rgb(78_44_20_/_12%)]',
            isPosting && 'pointer-events-none opacity-60',
          )}
        >
          <DrawingToolStrip
            hiddenToolKeys={sourceType === 'GALLERY' ? GALLERY_HIDDEN_TOOL_ACTIONS : []}
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
        </section>

        <aside className="grid gap-4 rounded-[0.75rem] bg-surface-subtle p-4 shadow-[0_10px_20px_rgb(78_44_20_/_12%)]">
          <MemoColorPicker
            selectedMemoColor={composer.selectedMemoColor}
            onSelectMemoColor={composer.setSelectedMemoColor}
          />

          <MemoPreview
            sourceType={sourceType}
            memoColor={composer.selectedMemoColor}
            imageUrl={sourceType === 'GALLERY' ? selectedGalleryPreviewUrl : null}
          />

          <button
            type="button"
            disabled={isPosting}
            onClick={onPreparePlacement}
            className="body-b sticky bottom-3 inline-flex h-12 w-full items-center justify-center gap-2 rounded-[0.45rem] bg-[#FFD95D] text-fg-primary shadow-[0_8px_18px_rgb(78_44_20_/_14%)] transition disabled:cursor-not-allowed disabled:opacity-60"
          >
            {sourceType === 'DIRECT' ? <Send className="size-5" /> : <ImagePlus className="size-5" />}
            {isPosting ? '메모지 준비 중' : '메모지 들기'}
          </button>
        </aside>
      </section>
    </div>
  )
}

function CommunityHandoffSourcePanel({
  draft,
  className,
}: {
  draft: NonNullable<ReturnType<typeof useCommunityComposer>['handoffDraft']>
  className?: string
}) {
  const previewUrl = draft.thumbnailUrl || draft.imageUrl
  const sourceLabel =
    draft.sourceKind === 'FORTUNE'
      ? '오늘의 운세'
      : draft.sourceKind === 'RELAY'
        ? '릴레이 드로잉'
        : '폰 갤러리'

  return (
    <section className={cn('rounded-[0.5rem] border border-border-default bg-surface-subtle p-4', className)}>
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
    <div className="grid h-full w-full grid-cols-2 rounded-[0.45rem] border border-border-default bg-surface-subtle px-1 py-0.5">
      {SOURCE_TABS.map((tab) => (
        <button
          key={tab.key}
          type="button"
          aria-pressed={sourceType === tab.key}
          onClick={() => onSelectSourceType(tab.key)}
          className={cn(
            'body-b h-full whitespace-nowrap rounded-[0.35rem] px-5 transition',
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
      <div className="grid grid-cols-3 gap-2">
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
                compact ? 'h-11' : 'h-14',
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
    <section className={cn('rounded-[0.45rem] bg-surface-subtle', compact ? 'p-2 pb-1.5' : 'p-3 pb-2')}>
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
    <section className="mt-2 overflow-hidden rounded-[0.5rem] border border-border-default bg-surface-default p-4">
      <p className="caption-b mb-3 text-fg-secondary">미리보기</p>
      <div className="relative mx-auto aspect-square w-[min(clamp(9rem,18dvh,13rem),100%)]">
        <PostItNote
          shape="square"
          className="absolute inset-0 h-full w-full drop-shadow-[0_12px_18px_rgb(66_45_25_/_18%)]"
          style={{ color: memoColor || DEFAULT_COMMUNITY_MEMO_COLOR }}
        />
        <span className="absolute inset-x-7 bottom-8 top-10 overflow-hidden rounded-[0.35rem]">
          {imageUrl ? (
            <Image
              src={imageUrl}
              alt="선택한 갤러리 메모 미리보기"
              fill
              sizes="208px"
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
