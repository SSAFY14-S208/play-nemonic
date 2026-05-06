"use client";

import {
  RelayBoothView,
  RelayDrawingView,
  RelayLobbyView,
  RelayResultView,
  RelayStepTabs,
} from "./components";
import { useRelayDrawingStore } from "./relayDrawingStore";

export default function RelayDrawingPage() {
  const currentStep = useRelayDrawingStore((state) => state.currentStep);

  return (
    <main className="min-h-screen max-h-full h-full bg-relay-background text-relay-ink">
      {currentStep === "booth" && <RelayBoothView />}
      {currentStep === "lobby" && <RelayLobbyView />}
      {currentStep === "drawing" && <RelayDrawingView />}
      {currentStep === "result" && <RelayResultView />}
      <RelayStepTabs />
    </main>
  );
}
