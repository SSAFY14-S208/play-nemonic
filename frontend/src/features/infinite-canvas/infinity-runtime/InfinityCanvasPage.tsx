"use client";

import { useState } from "react";
import { InfinityBoothView, InfinityNicknameModal } from "./components";
import { useInfinityBooth } from "./hooks";
import "./infinity-canvas.css";

type PendingBoothAction =
  | { type: "create" }
  | { type: "join"; inviteCode: string }
  | null;

export function InfinityCanvasPage() {
  const booth = useInfinityBooth()
  const [nicknameModalOpen, setNicknameModalOpen] = useState(false)
  const [pendingAction, setPendingAction] = useState<PendingBoothAction>(null)

  const handleCreateCanvas = () => {
    if (booth.needsNicknameSetup) {
      setPendingAction({ type: "create" })
      setNicknameModalOpen(true)
      return
    }
    booth.createCanvas()
  }

  const handleJoinCanvas = (inviteCode: string) => {
    if (booth.needsNicknameSetup) {
      setPendingAction({ type: "join", inviteCode })
      setNicknameModalOpen(true)
      return
    }
    booth.joinCanvas(inviteCode)
  }

  const handleNicknameSuccess = () => {
    const action = pendingAction
    setPendingAction(null)
    if (action?.type === "create") {
      booth.createCanvas()
      return
    }
    if (action?.type === "join") {
      booth.joinCanvas(action.inviteCode)
    }
  }

  return (
    <div className="infinity-page-shell bg-canvas-background text-canvas-ink">
      <InfinityBoothView
        inviteCodeError={booth.error}
        isBusy={booth.isPending}
        isUserReady={booth.isUserReady}
        selectedColor={booth.selectedColor}
        onClearError={booth.clearError}
        onCreateCanvas={handleCreateCanvas}
        onJoinCanvas={handleJoinCanvas}
        onSelectColor={booth.setSelectedColor}
      />
      <InfinityNicknameModal
        open={nicknameModalOpen}
        onOpenChange={(open) => {
          setNicknameModalOpen(open)
          if (!open) setPendingAction(null)
        }}
        onSuccess={handleNicknameSuccess}
      />
    </div>
  );
}
