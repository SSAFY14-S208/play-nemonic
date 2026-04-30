'use client'

import {
  RelayBoothView,
  RelayDrawingView,
  RelayLobbyView,
  RelayResultView,
  RelayStepTabs,
} from './components'
import { useRelayDrawing } from './useRelayDrawing'

export default function RelayDrawingPage() {
  const relayDrawing = useRelayDrawing()

  return (
    <main className="bg-relay-background text-relay-ink">
      {relayDrawing.currentStep === 'booth' && (
        <RelayBoothView
          onCreateRoom={relayDrawing.goToNextStep}
          onEnterRoom={relayDrawing.goToNextStep}
        />
      )}

      {relayDrawing.currentStep === 'lobby' && (
        <RelayLobbyView onStartGame={relayDrawing.goToNextStep} />
      )}

      {relayDrawing.currentStep === 'drawing' && (
        <RelayDrawingView
          selectedToolKey={relayDrawing.selectedToolKey}
          selectedColor={relayDrawing.selectedColor}
          strokeWidth={relayDrawing.strokeWidth}
          lines={relayDrawing.lines}
          onSelectTool={relayDrawing.setSelectedToolKey}
          onSelectColor={relayDrawing.setSelectedColor}
          onStrokeWidthChange={relayDrawing.setStrokeWidth}
          onUndoDrawing={relayDrawing.undoDrawing}
          onClearDrawing={relayDrawing.clearDrawing}
          onDrawStart={relayDrawing.beginDrawing}
          onDrawMove={relayDrawing.continueDrawing}
          onDrawEnd={relayDrawing.endDrawing}
          onCompleteRound={relayDrawing.goToNextStep}
        />
      )}

      {relayDrawing.currentStep === 'result' && (
        <RelayResultView onCreateAnother={() => relayDrawing.selectStep('booth')} />
      )}

      <RelayStepTabs
        currentStep={relayDrawing.currentStep}
        onSelectStep={relayDrawing.selectStep}
      />
    </main>
  )
}
