'use client'

import Image from 'next/image'
import { useState } from 'react'

import { INFINITE_CANVAS_COLOR_OPTIONS } from '..'
import { useInfiniteCanvasEntry } from '../hooks'
import { InfinityNicknameModal } from '../infinity-runtime/components'
import InfiniteCanvasActionButton from './InfiniteCanvasActionButton'
import InfiniteCanvasColorPicker from './InfiniteCanvasColorPicker'
import InfiniteCanvasInviteModal from './InfiniteCanvasInviteModal'

type PendingBoothAction = 'create' | 'openInvite' | 'submitInvite' | null

export default function InfiniteCanvasBoothView() {
  const {
    isUserReady,
    needsNicknameSetup,
    isPending,
    isInviteModalOpen,
    selectedColor,
    inviteCodeDraft,
    errorMessage,
    setSelectedColor,
    setInviteCodeDraft,
    createCanvas,
    openInviteModal,
    closeInviteModal,
    joinByInviteCode,
  } = useInfiniteCanvasEntry()
  const [isNicknameModalOpen, setIsNicknameModalOpen] = useState(false)
  const [pendingAction, setPendingAction] = useState<PendingBoothAction>(null)
  const isActionDisabled = isPending

  const handleCreateCanvas = () => {
    if (!isUserReady) {
      createCanvas()
      return
    }
    if (needsNicknameSetup) {
      setPendingAction('create')
      setIsNicknameModalOpen(true)
      return
    }
    createCanvas()
  }

  const handleOpenInviteModal = () => {
    if (!isUserReady) {
      openInviteModal()
      return
    }
    if (needsNicknameSetup) {
      setPendingAction('openInvite')
      setIsNicknameModalOpen(true)
      return
    }
    openInviteModal()
  }

  const handleJoinByInviteCode = () => {
    if (!isUserReady) {
      joinByInviteCode()
      return
    }
    if (needsNicknameSetup) {
      setPendingAction('submitInvite')
      setIsNicknameModalOpen(true)
      return
    }
    joinByInviteCode()
  }

  const handleNicknameSuccess = () => {
    const action = pendingAction
    setPendingAction(null)

    if (action === 'create') {
      createCanvas()
      return
    }

    if (action === 'openInvite') {
      openInviteModal()
      return
    }

    if (action === 'submitInvite') {
      joinByInviteCode()
    }
  }

  const handleNicknameModalChange = (open: boolean) => {
    setIsNicknameModalOpen(open)
    if (!open) setPendingAction(null)
  }

  return (
    <>
      <main className="infinite-canvas-page">
        <section className="infinite-canvas-scene" aria-labelledby="infinite-canvas-title">
          <Image
            src="/images/infinite-canvas/background.png"
            alt=""
            aria-hidden
            fill
            priority
            draggable={false}
            sizes="100vw"
            className="infinite-canvas-scene__background"
          />
          <div className="infinite-canvas-scene__shade" aria-hidden />
          <h1 id="infinite-canvas-title" className="sr-only">
            무한 캔버스
          </h1>

          <div className="infinite-canvas-logo-wrap">
            <Image
              src="/images/infinite-canvas/logo.png"
              alt="무한 캔버스"
              width={1885}
              height={656}
              priority
              draggable={false}
              className="infinite-canvas-logo"
            />
          </div>

          <div className="infinite-canvas-card">
            <div className="infinite-canvas-color-section">
              <p className="infinite-canvas-card__label">색 고르기</p>

              <InfiniteCanvasColorPicker
                options={INFINITE_CANVAS_COLOR_OPTIONS}
                selectedColor={selectedColor}
                onSelectColor={setSelectedColor}
              />
            </div>

            <div className="infinite-canvas-actions">
              <InfiniteCanvasActionButton
                imageSrc="/images/infinite-canvas/create-card.png"
                label={isPending ? '방 만드는 중' : '방 만들기'}
                disabled={isActionDisabled}
                onClick={handleCreateCanvas}
              />
              <InfiniteCanvasActionButton
                imageSrc="/images/infinite-canvas/enter-room-button.png"
                label="입장하기"
                disabled={isActionDisabled}
                onClick={handleOpenInviteModal}
              />
            </div>
            {errorMessage && !isInviteModalOpen && (
              <p
                className="infinite-canvas-status-message"
                role={errorMessage ? 'alert' : 'status'}
              >
                {errorMessage}
              </p>
            )}
          </div>
        </section>
      </main>
      <InfiniteCanvasInviteModal
        open={isInviteModalOpen}
        inviteCode={inviteCodeDraft}
        isPending={isPending}
        errorMessage={errorMessage}
        onInviteCodeChange={setInviteCodeDraft}
        onSubmit={handleJoinByInviteCode}
        onClose={closeInviteModal}
      />
      <InfinityNicknameModal
        open={isNicknameModalOpen}
        onOpenChange={handleNicknameModalChange}
        onSuccess={handleNicknameSuccess}
      />
    </>
  )
}
