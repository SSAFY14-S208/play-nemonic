'use client'

import { useEffect, useState, type CSSProperties } from 'react'
import { WorldHomeLink } from '@/shared/components/WorldHomeLink'
import { cn } from '@/shared/libs'
import { consumeCommunityCanvasHandoffDraft } from '@/shared/utils'
import {
  CommunityComposerModal,
  CommunityMemoDetailModal,
  CommunityReportModal,
  CommunityWall,
} from './components'
import { useCommunityCanvas, useCommunityComposer } from './hooks'

const refreshButtonStyle = {
  backgroundImage: 'url("/images/community-canvas/ui/button-refresh-token.png")',
} satisfies CSSProperties

const memoButtonStyle = {
  backgroundImage: 'url("/images/community-canvas/ui/button-add-token.png")',
} satisfies CSSProperties

type CommunityCanvasThemeStyle = CSSProperties & Record<`--${string}`, string>

const communityCanvasThemeStyle: CommunityCanvasThemeStyle = {
  '--color-primary-1': '#FFB72C',
  '--color-primary-2': '#E88900',
  '--color-primary-5': '#FFF0B8',
  '--color-fg-primary': '#2D2638',
  '--color-fg-secondary': '#6F6681',
  '--color-fg-disabled': '#B4ACBF',
  '--color-border-default': '#E7DAB8',
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
    composer.isComposerOpen || communityCanvas.selectedMemoUuid !== null || isReportOpen

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
            style={refreshButtonStyle}
            className={cn(
              'size-20 rounded-full bg-contain bg-center bg-no-repeat drop-shadow-[0_8px_10px_rgb(61_77_70_/_22%)] transition duration-150 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-primary-5',
              isHeaderActionsDisabled
                ? 'cursor-not-allowed opacity-70 brightness-[0.45] grayscale'
                : 'hover:-translate-y-0.5 hover:brightness-110 active:translate-y-0 active:brightness-95',
            )}
          />
          <button
            type="button"
            aria-label="새 메모 붙이기"
            disabled={isHeaderActionsDisabled}
            onClick={composer.openComposer}
            style={memoButtonStyle}
            className={cn(
              'size-20 rounded-full bg-contain bg-center bg-no-repeat drop-shadow-[0_8px_10px_rgb(61_77_70_/_22%)] transition duration-150 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-primary-5',
              isHeaderActionsDisabled
                ? 'cursor-not-allowed opacity-70 brightness-[0.45] grayscale'
                : 'hover:-translate-y-0.5 hover:brightness-110 active:translate-y-0 active:brightness-95',
            )}
          />
        </div>
      </header>

      <CommunityWall
        memos={communityCanvas.memos}
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
