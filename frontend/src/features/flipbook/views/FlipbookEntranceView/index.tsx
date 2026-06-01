'use client'

import Image from 'next/image'

import { HowToPlayModal } from '@/shared/components'
import { FLIPBOOK_HOW_TO_PLAY_PANELS } from '@/features/flipbook/constants'
import {
  FLIPBOOK_ENTRANCE_PRELOAD_LINK_SOURCES,
  FLIPBOOK_SCENE_IMAGES,
} from './constants'
import { useFlipbookEntranceViewModel } from './hooks'
import {
  EntranceDesktopScene,
  EntranceMobileScene,
  RoomCodeModal,
} from './sections'

interface FlipbookEntranceViewProps {
  roomCodeDraft: string
  isBusy: boolean
  errorMessage: string | null
  onRoomCodeDraftChange: (roomCode: string) => void
  onCreateRoom: () => void
  onEnterRoom: () => void
}

export default function FlipbookEntranceView({
  roomCodeDraft,
  isBusy,
  errorMessage,
  onRoomCodeDraftChange,
  onCreateRoom,
  onEnterRoom,
}: FlipbookEntranceViewProps) {
  const {
    activeFrameIndex,
    actionHandlers,
    audioRef,
    closeRoomCodeModal,
    entranceBgmSource,
    handleScroll,
    isActionVisible,
    isBgmMuted,
    isEntranceMounted,
    isHowToPlayModalOpen,
    isIntroComplete,
    isRoomCodeModalOpen,
    openHowToPlayModal,
    openRoomCodeModal,
    roomCodeInputRef,
    scrollSpacerHeight,
    scrollZoneRef,
    setIsHowToPlayModalOpen,
    shouldInstantCompleteIntro,
    shouldShowEntranceError,
    submitRoomCode,
    toggleFlipbookBgmMuted,
  } = useFlipbookEntranceViewModel({
    isBusy,
    isRoomCodeErrorVisible: errorMessage !== null,
    onCreateRoom,
    onEnterRoom,
  })

  const entranceErrorMessage = shouldShowEntranceError ? errorMessage : null

  return (
    <section
      className="relative min-h-[100svh] overflow-hidden bg-flipbook-room-base text-flipbook-ink"
    >
      {FLIPBOOK_ENTRANCE_PRELOAD_LINK_SOURCES.map((imageSource) => (
        <link key={imageSource} rel="preload" as="image" href={imageSource} type="image/webp" />
      ))}

      <Image
        src={FLIPBOOK_SCENE_IMAGES.background}
        alt=""
        fill
        priority
        sizes="100vw"
        className="pointer-events-none absolute inset-0 z-0 object-cover"
        aria-hidden
      />

      <audio ref={audioRef} src={entranceBgmSource} preload="auto" loop aria-hidden />

      {isEntranceMounted && (
        <EntranceMobileScene
          visible={isActionVisible}
          isBusy={isBusy}
          isBgmMuted={isBgmMuted}
          errorMessage={entranceErrorMessage}
          onCreateRoom={onCreateRoom}
          onOpenRoomCodeModal={openRoomCodeModal}
          onOpenHowToPlay={openHowToPlayModal}
          onToggleBgmMuted={toggleFlipbookBgmMuted}
        />
      )}

      {isEntranceMounted && (
        <EntranceDesktopScene
          visible={isActionVisible}
          interactive={isIntroComplete}
          shouldInstantCompleteIntro={shouldInstantCompleteIntro}
          isBusy={isBusy}
          isBgmMuted={isBgmMuted}
          errorMessage={entranceErrorMessage}
          activeFrameIndex={activeFrameIndex}
          scrollSpacerHeight={scrollSpacerHeight}
          scrollZoneRef={scrollZoneRef}
          actionHandlers={actionHandlers}
          onScroll={handleScroll}
          onOpenHowToPlay={openHowToPlayModal}
          onToggleBgmMuted={toggleFlipbookBgmMuted}
        />
      )}

      <RoomCodeModal
        open={isRoomCodeModalOpen}
        inputRef={roomCodeInputRef}
        roomCodeDraft={roomCodeDraft}
        isBusy={isBusy}
        errorMessage={errorMessage}
        onRoomCodeDraftChange={onRoomCodeDraftChange}
        onClose={closeRoomCodeModal}
        onSubmit={submitRoomCode}
      />

      <HowToPlayModal
        open={isHowToPlayModalOpen}
        onOpenChange={setIsHowToPlayModalOpen}
        panels={FLIPBOOK_HOW_TO_PLAY_PANELS}
        title="플립북 게임 설명"
        subtitle="실제 예시 프레임을 넘기며 플립북 흐름을 확인해요."
        accentColor="#ff7182"
      />
    </section>
  )
}
