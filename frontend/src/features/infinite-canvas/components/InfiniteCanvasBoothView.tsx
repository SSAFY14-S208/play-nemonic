'use client'

import Image from 'next/image'

import { INFINITE_CANVAS_COLOR_OPTIONS } from '../constants'
import { useInfiniteCanvasEntry } from '../hooks'
import InfiniteCanvasActionButton from './InfiniteCanvasActionButton'
import InfiniteCanvasColorPicker from './InfiniteCanvasColorPicker'
import InfiniteCanvasInviteModal from './InfiniteCanvasInviteModal'

export default function InfiniteCanvasBoothView() {
  const {
    isUserReady,
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
  const isActionDisabled = !isUserReady || isPending

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
            <p className="infinite-canvas-card__label">색 고르기</p>

            <InfiniteCanvasColorPicker
              options={INFINITE_CANVAS_COLOR_OPTIONS}
              selectedColor={selectedColor}
              onSelectColor={setSelectedColor}
            />

            <div className="infinite-canvas-actions">
              <InfiniteCanvasActionButton
                imageSrc="/images/infinite-canvas/enter-room-button.png"
                label="초대코드로 입장하기"
                disabled={isActionDisabled}
                onClick={openInviteModal}
              />
              <InfiniteCanvasActionButton
                imageSrc="/images/infinite-canvas/create-card.png"
                label={isPending ? '방 만드는 중' : '방 만들기'}
                disabled={isActionDisabled}
                onClick={createCanvas}
              />
            </div>
            {(isPending || (errorMessage && !isInviteModalOpen)) && (
              <p
                className="infinite-canvas-status-message"
                role={errorMessage ? 'alert' : 'status'}
              >
                {errorMessage ?? '무한 캔버스 방을 만들고 있어요'}
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
        onSubmit={joinByInviteCode}
        onClose={closeInviteModal}
      />
    </>
  )
}
