'use client'

import { useEffect, useState, type CSSProperties } from 'react'
import { Plus, RefreshCw } from 'lucide-react'
import { WorldHomeLink } from '@/shared/components/WorldHomeLink'
import { cn } from '@/shared/libs'
import { consumeCommunityCanvasHandoffDraft } from '@/shared/utils'
import {
  CommunityComposerModal,
  CommunityMemoDetailModal,
  CommunityMemoPrintRevealOverlay,
  CommunityReportModal,
  CommunityWall,
} from './components'
import { useCommunityCanvas, useCommunityComposer } from './hooks'

type CommunityCanvasThemeStyle = CSSProperties & Record<`--${string}`, string>

const communityCanvasThemeStyle: CommunityCanvasThemeStyle = {
  '--color-primary-1': '#B8AED3',
  '--color-primary-2': '#6A5F82',
  '--color-primary-5': '#ECE7F7',
  '--color-fg-primary': '#2D2638',
  '--color-fg-secondary': '#6F6681',
  '--color-fg-disabled': '#B4ACBF',
  '--color-border-default': '#D7D0E4',
  '--community-action-button-border': '#B7AEC9',
  '--community-action-button-ring': 'rgb(255 255 255 / 0.78)',
}

export function CommunityCanvasPage() {
  const [isReportOpen, setReportOpen] = useState(false)
  const communityCanvas = useCommunityCanvas()
  const composer = useCommunityComposer({
    onCreated: communityCanvas.loadCommunityMemosWithCreatedMemo,
  })
  const { openComposerWithHandoffDraft } = composer

  useEffect(() => {
    let isCancelled = false

    void (async () => {
      await Promise.resolve()
      const handoffDraft = consumeCommunityCanvasHandoffDraft()

      if (!isCancelled && handoffDraft) {
        openComposerWithHandoffDraft(handoffDraft)
      }
    })()

    return () => {
      isCancelled = true
    }
  }, [openComposerWithHandoffDraft])

  const isAttachingMemo =
    composer.pendingPlacement !== null && composer.postStatus === 'loading'
  const isSavingLayout =
    communityCanvas.editingMemo !== null && communityCanvas.mutationStatus === 'loading'
  const isHeaderActionsDisabled =
    composer.isComposerOpen ||
    composer.printRevealPlacement !== null ||
    communityCanvas.selectedMemoUuid !== null ||
    isReportOpen

  const handleDeleteSelectedMemo = () => {
    if (typeof window !== 'undefined') {
      const confirmed = window.confirm('이 메모를 삭제할까요?')
      if (!confirmed) return
    }

    void communityCanvas.deleteSelectedMemo()
  }

  return (
    <main
      className="relative min-h-screen overflow-hidden bg-surface-default text-fg-primary"
      style={communityCanvasThemeStyle}
    >
      <WorldHomeLink />
      <header className="pointer-events-none fixed inset-x-4 top-4 z-[12000] flex justify-end">
        <div className="pointer-events-auto flex items-center gap-2">
          <button
            type="button"
            aria-label="새로고침"
            disabled={isHeaderActionsDisabled}
            onClick={() => void communityCanvas.loadCommunityMemos()}
            className={cn(
              'grid size-14 place-items-center rounded-full border-2 border-[var(--community-action-button-border)] bg-white/82 text-fg-secondary shadow-[0_10px_26px_rgb(73_55_93_/_20%)] ring-2 ring-[color:var(--community-action-button-ring)] backdrop-blur-md transition duration-150 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-primary-5 sm:size-16',
              isHeaderActionsDisabled
                ? 'invisible cursor-not-allowed'
                : 'hover:-translate-y-0.5 hover:bg-white hover:text-fg-primary active:translate-y-0 active:bg-white/90',
            )}
          >
            <RefreshCw className="size-7 sm:size-8" strokeWidth={2.5} />
          </button>
          <button
            type="button"
            aria-label="새 메모 붙이기"
            disabled={isHeaderActionsDisabled}
            onClick={composer.openComposer}
            className={cn(
              'grid size-14 place-items-center rounded-full border-2 border-[var(--community-action-button-border)] bg-white/82 text-fg-secondary shadow-[0_10px_26px_rgb(73_55_93_/_20%)] ring-2 ring-[color:var(--community-action-button-ring)] backdrop-blur-md transition duration-150 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-primary-5 sm:size-16',
              isHeaderActionsDisabled
                ? 'invisible cursor-not-allowed'
                : 'hover:-translate-y-0.5 hover:bg-white hover:text-fg-primary active:translate-y-0 active:bg-white/90',
            )}
          >
            <Plus className="size-8 sm:size-9" strokeWidth={2.5} />
          </button>
        </div>
      </header>

      <CommunityWall
        memos={communityCanvas.memos}
        memoPlaybackImageUrls={communityCanvas.memoPlaybackImageUrls}
        selectedMemoUuid={communityCanvas.selectedWallMemoUuid}
        memoStatus={communityCanvas.memoStatus}
        memoError={communityCanvas.memoError}
        pendingMemo={composer.pendingPlacement}
        editingMemo={communityCanvas.editingMemo}
        editingLayoutDraft={communityCanvas.editingLayoutDraft}
        nextZIndex={communityCanvas.nextZIndex}
        isAttachingMemo={isAttachingMemo}
        isSavingLayout={isSavingLayout}
        onSelectMemo={communityCanvas.selectWallMemo}
        onClearSelection={communityCanvas.clearWallMemoSelection}
        onOpenMemoDetail={(memoUuid) => void communityCanvas.openMemoDetail(memoUuid)}
        onAttachPendingMemo={(placement) => void composer.attachPendingMemo(placement)}
        onCancelPendingMemo={composer.cancelPendingPlacement}
        onEditingLayoutChange={communityCanvas.updateEditingLayoutDraft}
        onSaveEditingLayout={(layout) => void communityCanvas.saveEditingMemoLayout(layout)}
        onRetry={() => void communityCanvas.loadCommunityMemos()}
      />

      <CommunityComposerModal composer={composer} />

      <CommunityMemoPrintRevealOverlay
        placement={composer.printRevealPlacement}
        onAccept={composer.acceptPrintedMemoPlacement}
        onCancel={composer.cancelPrintedMemoPlacement}
      />

      <CommunityMemoDetailModal
        isOpen={communityCanvas.selectedMemoUuid !== null}
        detail={communityCanvas.selectedMemoDetail}
        playbackImageUrl={communityCanvas.selectedMemoPlaybackImageUrl}
        detailStatus={communityCanvas.detailStatus}
        detailError={communityCanvas.detailError}
        mutationStatus={communityCanvas.mutationStatus}
        onClose={communityCanvas.closeMemoDetail}
        onDelete={handleDeleteSelectedMemo}
        onReportOpen={() => setReportOpen(true)}
      />

      <CommunityReportModal
        isOpen={isReportOpen}
        status={communityCanvas.reportStatus}
        onClose={() => setReportOpen(false)}
        onSubmit={(reason, reasonDetail) => {
          void communityCanvas.reportSelectedMemo(reason, reasonDetail).then((reported) => {
            if (reported) setReportOpen(false)
          })
        }}
      />
    </main>
  )
}
