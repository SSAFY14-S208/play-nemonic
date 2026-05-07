'use client'

import {
  FlipbookDrawingView,
  FlipbookEntranceView,
  FlipbookLobbyView,
  FlipbookResultView,
} from './components'
import { useFlipbook } from './hooks'

export default function FlipbookPage() {
  const flipbook = useFlipbook()

  return (
    <main className="min-h-screen bg-flipbook-background text-flipbook-ink">
      {flipbook.currentStep === 'booth' && (
        <FlipbookEntranceView
          onCreateRoom={flipbook.createRoom}
          onEnterRoom={flipbook.enterRoom}
        />
      )}

      {flipbook.currentStep === 'lobby' && (
        <FlipbookLobbyView
          selectedTimeLimitSeconds={flipbook.selectedTimeLimitSeconds}
          roundCount={flipbook.roundCount}
          minimumRoundCount={flipbook.minimumRoundCount}
          onSelectTimeLimit={flipbook.selectTimeLimit}
          onDecreaseRoundCount={flipbook.decreaseRoundCount}
          onIncreaseRoundCount={flipbook.increaseRoundCount}
          onStartGame={flipbook.startGame}
        />
      )}

      {flipbook.currentStep === 'drawing' && (
        <FlipbookDrawingView
          activeRoundIndex={flipbook.activeRoundIndex}
          roundCount={flipbook.roundCount}
          remainingSeconds={flipbook.remainingSeconds}
          currentParticipant={flipbook.currentParticipant}
          lines={flipbook.drawingBoard.lines}
          previousFrameLines={flipbook.previousFrameLines}
          selectedToolKey={flipbook.drawingBoard.selectedToolKey}
          selectedColor={flipbook.drawingBoard.selectedColor}
          strokeWidth={flipbook.drawingBoard.strokeWidth}
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
          activeFrame={flipbook.activeResultFrame}
          resultFrameIndex={flipbook.resultFrameIndex}
          isGifPlaying={flipbook.isGifPlaying}
          canGoPreviousResultFrame={flipbook.canGoPreviousResultFrame}
          canGoNextResultFrame={flipbook.canGoNextResultFrame}
          onToggleGifPlaying={flipbook.setIsGifPlaying}
          onShowPreviousFrame={flipbook.showPreviousResultFrame}
          onShowNextFrame={flipbook.showNextResultFrame}
          onCreateAnother={() => flipbook.selectStep('booth')}
        />
      )}
    </main>
  )
}
