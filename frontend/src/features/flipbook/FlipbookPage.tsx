'use client'

import { useCallback, useState } from 'react'
import { usePathname, useRouter } from 'next/navigation'
import {
  FlipbookDrawingView,
  FlipbookEntranceView,
  FlipbookLobbyView,
  FlipbookNicknameModal,
  FlipbookResultView,
} from './components'
import { useFlipbook, useFlipbookResultAutoCycle } from './hooks'
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

  // FlipbookPrintResultStage가 참여자의 reveal 시퀀스 완료(=GIF가 화면에 보이는
  // 시점)를 알려주면 setState로 받아둔다. 자동 전환 hook이 이 값과
  // activeResultIndex가 일치할 때부터 5초 카운트를 시작한다.
  const [revealedResultIndex, setRevealedResultIndex] = useState<number | null>(null)

  useFlipbookResultAutoCycle({
    enabled: flipbook.currentStep === 'result',
    resultCount: flipbook.resultItems.length,
    activeResultIndex: flipbook.activeResultIndex,
    revealedResultIndex,
    onSelectResult: flipbook.selectResult,
  })

  return (
    <main className="relative min-h-screen bg-flipbook-background text-flipbook-ink">
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
          minParticipants={flipbook.minParticipants}
          maxParticipants={flipbook.maxParticipants}
          selectedTimeLimitSeconds={flipbook.selectedTimeLimitSeconds}
          timeLimitOptions={flipbook.timeLimitOptions}
          connectionStatus={flipbook.connectionStatus}
          canStartGame={flipbook.canStartGame}
          isHost={flipbook.isHost}
          isBusy={flipbook.isBusy}
          errorMessage={flipbook.errorMessage}
          onSelectTimeLimit={flipbook.selectTimeLimit}
          onStartGame={flipbook.startGame}
          onLeaveRoom={flipbook.leaveRoom}
          onKickParticipant={flipbook.kickParticipant}
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
          isAssignmentReady={flipbook.isAssignmentReady}
          submittedCount={flipbook.submittedCount}
          totalCount={flipbook.totalCount}
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
          onCompleteRound={flipbook.completeRound}
        />
      )}

      {flipbook.currentStep === 'result' && (
        <FlipbookResultView
          resultItems={flipbook.resultItems}
          resultOwnerNames={flipbook.resultOwnerNames}
          activeResultIndex={flipbook.activeResultIndex}
          resultCount={flipbook.resultCount}
          canCloseRoom={flipbook.canCloseRoom}
          isBusy={flipbook.isBusy}
          errorMessage={flipbook.errorMessage}
          onSelectResult={flipbook.selectResult}
          onReturnToLobby={
            flipbook.canCloseRoom
              ? flipbook.closeRoom
              : () => flipbook.selectStep('booth')
          }
          onParticipantRevealComplete={setRevealedResultIndex}
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
