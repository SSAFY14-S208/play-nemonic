'use client'

import {
  FlipbookDrawingView,
  FlipbookEntranceView,
  FlipbookLobbyView,
  FlipbookNicknameModal,
  FlipbookResultView,
} from './components'
import { useFlipbook } from './hooks'

export default function FlipbookPage() {
  const flipbook = useFlipbook()

  return (
    <main className="min-h-screen bg-flipbook-background text-flipbook-ink">
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
          strokeWidth={flipbook.drawingBoard.strokeWidth}
          recentColors={flipbook.drawingBoard.recentColors}
          canUndoDrawing={flipbook.drawingBoard.canUndoDrawing}
          canRedoDrawing={flipbook.drawingBoard.canRedoDrawing}
          onSelectTool={flipbook.drawingBoard.setSelectedToolKey}
          onSelectColor={flipbook.drawingBoard.setSelectedColor}
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
          frames={flipbook.frames}
          resultItems={flipbook.resultItems}
          resultOwnerNames={flipbook.resultOwnerNames}
          activeResultIndex={flipbook.activeResultIndex}
          gifUrl={flipbook.gifUrl}
          resultCount={flipbook.resultCount}
          activeFrame={flipbook.activeResultFrame}
          resultFrameIndex={flipbook.resultFrameIndex}
          isGifPlaying={flipbook.isGifPlaying}
          canGoPreviousResultFrame={flipbook.canGoPreviousResultFrame}
          canGoNextResultFrame={flipbook.canGoNextResultFrame}
          onToggleGifPlaying={flipbook.setIsGifPlaying}
          onShowFrame={flipbook.showResultFrame}
          onShowPreviousFrame={flipbook.showPreviousResultFrame}
          onShowNextFrame={flipbook.showNextResultFrame}
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
