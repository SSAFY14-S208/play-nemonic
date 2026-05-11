'use client'

import { useCallback } from 'react'
import { ArrowLeft } from 'lucide-react'
import Link from 'next/link'
import { usePathname, useRouter } from 'next/navigation'
import {
  FlipbookDrawingView,
  FlipbookEntranceView,
  FlipbookLobbyView,
  FlipbookNicknameModal,
  FlipbookResultView,
} from './components'
import { useFlipbook } from './hooks'
import {
  getFlipbookStepFromPathname,
  getFlipbookStepPath,
} from './constants'
import type { FlipbookStep } from './types'

export default function FlipbookPage() {
  const pathname = usePathname()
  const router = useRouter()
  const routeStep = getFlipbookStepFromPathname(pathname)
  const navigateToStep = useCallback(
    (
      step: FlipbookStep,
      options: {
        roomCode?: string | null
        replace?: boolean
      } = {},
    ) => {
      const nextPath = getFlipbookStepPath(step)
      const roomCodeQuery = options.roomCode ? `?roomCode=${options.roomCode}` : ''
      const nextHref = `${nextPath}${roomCodeQuery}`
      const currentQuery =
        typeof window === 'undefined' ? '' : window.location.search.replace(/^\?/, '')
      const currentHref = currentQuery ? `${pathname}?${currentQuery}` : pathname

      if (currentHref !== nextHref) {
        if (options.replace) {
          router.replace(nextHref)
          return
        }

        router.push(nextHref)
      }
    },
    [pathname, router],
  )
  const flipbook = useFlipbook({
    routeStep,
    onStepChange: navigateToStep,
  })

  return (
    <main className="relative min-h-screen bg-flipbook-background text-flipbook-ink">
      <Link
        href="/hub"
        className="body-b absolute left-4 top-4 z-50 inline-flex cursor-pointer items-center gap-1.5 rounded-full border border-relay-line bg-relay-paper px-4 py-2 text-relay-ink shadow-sm transition-all hover:-translate-y-0.5 hover:brightness-95 sm:left-6 sm:top-6 lg:left-[5%]"
      >
        <ArrowLeft className="size-5" aria-hidden />
        네모닉 월드로 돌아가기
      </Link>

      {flipbook.currentStep === 'booth' && (
        <FlipbookEntranceView
          roomCodeDraft={flipbook.roomCodeDraft}
          isBusy={flipbook.isBusy}
          errorMessage={flipbook.errorMessage}
          onRoomCodeDraftChange={flipbook.setRoomCodeDraft}
          onCreateRoom={flipbook.createRoom}
          onEnterRoom={flipbook.enterRoom}
        />
      )}

      {flipbook.currentStep === 'lobby' && (
        <FlipbookLobbyView
          currentParticipant={flipbook.currentParticipant}
          participants={flipbook.participants}
          roomCode={flipbook.roomCode}
          participantCount={flipbook.participantCount}
          maxParticipants={flipbook.maxParticipants}
          selectedTimeLimitSeconds={flipbook.selectedTimeLimitSeconds}
          roundCount={flipbook.roundCount}
          connectionStatus={flipbook.connectionStatus}
          canStartGame={flipbook.canStartGame}
          isHost={flipbook.isHost}
          isBusy={flipbook.isBusy}
          errorMessage={flipbook.errorMessage}
          onBack={flipbook.leaveRoom}
          onSelectTimeLimit={flipbook.selectTimeLimit}
          onSelectRoundCount={flipbook.selectRoundCount}
          onStartGame={flipbook.startGame}
        />
      )}

      {flipbook.currentStep === 'drawing' && (
        <FlipbookDrawingView
          activeRoundIndex={flipbook.activeRoundIndex}
          roundCount={flipbook.drawingRoundCount}
          remainingSeconds={flipbook.remainingSeconds}
          currentParticipant={flipbook.currentParticipant}
          isSubmitting={flipbook.isSubmitting}
          isRoundSubmitted={flipbook.isRoundSubmitted}
          connectionStatus={flipbook.connectionStatus}
          errorMessage={flipbook.errorMessage}
          lines={flipbook.drawingBoard.lines}
          previousFrameLines={flipbook.previousFrameLines}
          selectedToolKey={flipbook.drawingBoard.selectedToolKey}
          selectedColor={flipbook.drawingBoard.selectedColor}
          selectedOpacity={flipbook.drawingBoard.selectedOpacity}
          strokeWidth={flipbook.drawingBoard.strokeWidth}
          recentColors={flipbook.drawingBoard.recentColors}
          canUndoDrawing={flipbook.drawingBoard.canUndoDrawing}
          canRedoDrawing={flipbook.drawingBoard.canRedoDrawing}
          onSelectTool={flipbook.drawingBoard.setSelectedToolKey}
          onSelectColor={flipbook.drawingBoard.setSelectedColor}
          onOpacityChange={flipbook.drawingBoard.setSelectedOpacity}
          onStrokeWidthChange={flipbook.drawingBoard.setStrokeWidth}
          onUndoDrawing={flipbook.drawingBoard.undoDrawing}
          onRedoDrawing={flipbook.drawingBoard.redoDrawing}
          onClearDrawing={flipbook.drawingBoard.clearDrawing}
          onDrawStart={flipbook.drawingBoard.beginDrawing}
          onDrawMove={flipbook.drawingBoard.continueDrawing}
          onDrawEnd={flipbook.drawingBoard.endDrawing}
          onExit={flipbook.leaveRoom}
          onCompleteRound={flipbook.completeRound}
        />
      )}

      {flipbook.currentStep === 'result' && (
        <FlipbookResultView
          resultItems={flipbook.resultItems}
          resultOwnerNames={flipbook.resultOwnerNames}
          activeResultIndex={flipbook.activeResultIndex}
          gifUrl={flipbook.gifUrl}
          resultCount={flipbook.resultCount}
          onSelectResult={flipbook.selectResult}
          onCreateAnother={() => flipbook.selectStep('booth')}
        />
      )}

      <FlipbookNicknameModal
        open={flipbook.nicknameModalOpen}
        onOpenChange={flipbook.setNicknameModalOpen}
        onSuccess={flipbook.continuePendingNicknameAction}
      />
    </main>
  )
}
