'use client'

import { KeyRound, Sparkles, X } from 'lucide-react'
import { motion } from 'motion/react'
import Image from 'next/image'
import { useEffect, useRef, useState, type RefObject } from 'react'
import Lottie from 'lottie-react'
import { downArrowAnimation } from '@/shared/assets'
import { HowToPlayModal, PhoneLauncherButton } from '@/shared/components'
import { FLIPBOOK_HOW_TO_PLAY_PANELS, FLIPBOOK_SOUND_PATHS } from '../constants'
import {
  useFlipbookEntranceBgm,
  useFlipbookEntranceIntro,
  useFlipbookEntrancePreload,
  useFlipbookEntranceWheelFrames,
} from '../hooks'

interface FlipbookEntranceViewProps {
  roomCodeDraft: string
  isBusy: boolean
  errorMessage: string | null
  onRoomCodeDraftChange: (roomCode: string) => void
  onCreateRoom: () => void
  onEnterRoom: () => void
}

const FLIPBOOK_ENTRANCE_FRAME_COUNT = 12
const FLIPBOOK_SCROLL_HINT_FRAME_INDEX = 0
const FLIPBOOK_SCROLL_HINT_LABEL = '아래로 스크롤 하세요'
const FLIPBOOK_ENTRANCE_FRAMES = Array.from(
  { length: FLIPBOOK_ENTRANCE_FRAME_COUNT },
  (unusedValue, frameIndex) => {
    const frameNumber = String(frameIndex + 1).padStart(2, '0')

    return {
      src: `/images/flipbook-entrance/${frameNumber}.webp`,
      alt: `플립북 스케치북 재생 ${frameIndex + 1}번째 장면`,
    }
  },
)
const FLIPBOOK_ENTRANCE_FRAME_SOURCES = FLIPBOOK_ENTRANCE_FRAMES.map(
  (entranceFrame) => entranceFrame.src,
)
const FLIPBOOK_ENTRANCE_PRELOAD_LINK_SOURCES = FLIPBOOK_ENTRANCE_FRAME_SOURCES.slice(1)
const FLIPBOOK_SCENE_IMAGES = {
  background: '/images/flipbook-entrance-scene/room-background.png',
  furnitureSprite: '/images/flipbook-entrance-scene/furniture-sprite.png',
  titleLogoSprite: '/images/flipbook-entrance-scene/title-logo-sprite.png',
  actionButtonsSprite: '/images/flipbook-entrance-scene/action-buttons-sprite.png',
  sketchbook: '/images/flipbook-entrance-scene/sketchbook.png',
  howToPlayButton: '/images/flipbook-entrance-scene/how-to-play-button.png',
  soundOnButton: '/images/flipbook-entrance-scene/sound-on-button.png',
  soundMutedButton: '/images/flipbook-entrance-scene/sound-muted-button.png',
}
const FLIPBOOK_ACTION_BUTTON_IMAGE_QUALITY = 92
const FLIPBOOK_ACTION_BUTTON_IMAGE_SIZES = '(max-width: 639px) 100vw, 40vw'

const DROP_SPRING_TRANSITION = {
  type: 'spring',
  stiffness: 96,
  damping: 13,
  mass: 0.82,
} as const

const DROP_LAYERS = [
  {
    key: 'furniture-top',
    className: 'left-[17.43%] top-[-5.33%] h-[22.04%] w-[57.76%]',
    imageClassName: 'h-[593.26%] w-[191.05%] -left-[36.96%] -top-[33.16%]',
    rotate: 3.2,
    fallDistance: 720,
    delay: 0.12,
  },
  {
    key: 'furniture-left',
    className: 'left-[-5.05%] top-[44.91%] h-[62.50%] w-[47.97%]',
    imageClassName: 'h-[181.75%] w-[199.94%] -left-[0.03%] -top-[81.75%]',
    rotate: 0,
    fallDistance: 860,
    delay: 0.34,
  },
  {
    key: 'furniture-right',
    className: 'left-[63.13%] top-[3.98%] h-[103.06%] w-[39.22%]',
    imageClassName: 'h-[125.96%] w-[279.19%] -left-[165.03%] -top-[25.96%]',
    rotate: 0,
    fallDistance: 940,
    delay: 0.24,
  },
] as const

const FLIPBOOK_ENTRANCE_ACTIONS = [
  {
    key: 'create-room',
    buttonClassName: 'left-[11.90%] top-[62.69%] aspect-[635/267] w-[15.21%]',
    imageCropClassName: 'h-[383.52%] -left-[17.95%] -top-[150.56%]',
    label: '방 만들기',
  },
  {
    key: 'enter-room',
    buttonClassName: 'left-[28.36%] top-[62.69%] aspect-[635/267] w-[15.21%]',
    imageCropClassName: 'h-[383.52%] -left-[128.98%] -top-[150.56%]',
    label: '입장하기',
  },
] as const

export default function FlipbookEntranceView({
  roomCodeDraft,
  isBusy,
  errorMessage,
  onRoomCodeDraftChange,
  onCreateRoom,
  onEnterRoom,
}: FlipbookEntranceViewProps) {
  const roomCodeInputRef = useRef<HTMLInputElement>(null)
  const scrollZoneRef = useRef<HTMLDivElement>(null)
  const [isEntranceMounted, setIsEntranceMounted] = useState(false)
  const [isRoomCodeModalOpen, setIsRoomCodeModalOpen] = useState(false)
  const [isHowToPlayModalOpen, setIsHowToPlayModalOpen] = useState(false)
  const { isActionVisible, isIntroComplete, wasIntroSkipped } = useFlipbookEntranceIntro()
  const { audioRef, isBgmMuted, toggleFlipbookEntranceBgmMuted } = useFlipbookEntranceBgm({
    shouldStart: isEntranceMounted && isIntroComplete,
  })
  const shouldInstantCompleteIntro = isIntroComplete && wasIntroSkipped
  const { activeFrameIndex, handleScroll, scrollSpacerHeight } = useFlipbookEntranceWheelFrames(
    FLIPBOOK_ENTRANCE_FRAMES.length,
    isIntroComplete,
  )
  useFlipbookEntrancePreload(FLIPBOOK_ENTRANCE_FRAME_SOURCES)
  const actionHandlers = {
    'create-room': onCreateRoom,
    'enter-room': () => setIsRoomCodeModalOpen(true),
  }

  useEffect(() => {
    let cancelled = false

    ;(async () => {
      await Promise.resolve()

      if (!cancelled) {
        setIsEntranceMounted(true)
      }
    })()

    return () => {
      cancelled = true
    }
  }, [])

  useEffect(() => {
    if (!isRoomCodeModalOpen) return

    const focusInputFrame = window.requestAnimationFrame(() => {
      roomCodeInputRef.current?.focus()
    })

    return () => {
      window.cancelAnimationFrame(focusInputFrame)
    }
  }, [isRoomCodeModalOpen])

  const closeRoomCodeModal = () => {
    if (!isBusy) {
      setIsRoomCodeModalOpen(false)
    }
  }

  const submitRoomCode = () => {
    onEnterRoom()
  }

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

      <audio ref={audioRef} src={FLIPBOOK_SOUND_PATHS.entranceBgm} preload="auto" loop aria-hidden />

      {isEntranceMounted && (
        <FlipbookEntranceMobileScene
          visible={isActionVisible}
          isBusy={isBusy}
          isBgmMuted={isBgmMuted}
          errorMessage={!isRoomCodeModalOpen ? errorMessage : null}
          onCreateRoom={onCreateRoom}
          onOpenRoomCodeModal={() => setIsRoomCodeModalOpen(true)}
          onOpenHowToPlay={() => setIsHowToPlayModalOpen(true)}
          onToggleBgmMuted={toggleFlipbookEntranceBgmMuted}
        />
      )}

      {isEntranceMounted && (
        <div
          className="absolute inset-0 z-10 hidden overflow-hidden sm:block"
        >
          <FlipbookEntranceDropScene shouldInstantCompleteIntro={shouldInstantCompleteIntro} />

          <FlipbookEntranceSketchbook
            scrollZoneRef={scrollZoneRef}
            activeFrameIndex={activeFrameIndex}
            scrollSpacerHeight={scrollSpacerHeight}
            onScroll={handleScroll}
            isInteractive={isIntroComplete}
            shouldInstantCompleteIntro={shouldInstantCompleteIntro}
          />

          <FlipbookEntranceActions
            visible={isActionVisible}
            interactive={isIntroComplete}
            shouldInstantCompleteIntro={shouldInstantCompleteIntro}
            isBusy={isBusy}
            errorMessage={!isRoomCodeModalOpen ? errorMessage : null}
            actionHandlers={actionHandlers}
          />

          <FlipbookEntranceTopControls
            isBgmMuted={isBgmMuted}
            onOpenHowToPlay={() => setIsHowToPlayModalOpen(true)}
            onToggleBgmMuted={toggleFlipbookEntranceBgmMuted}
          />
        </div>
      )}

      <FlipbookRoomCodeModal
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

function FlipbookEntranceTopControls({
  isBgmMuted,
  onOpenHowToPlay,
  onToggleBgmMuted,
}: {
  isBgmMuted: boolean
  onOpenHowToPlay: () => void
  onToggleBgmMuted: () => void
}) {
  return (
    <div className="absolute right-[3.02%] top-[8.15%] z-30 flex items-center gap-[0.63vw]">
      <FlipbookEntranceIconButton
        imageSrc={FLIPBOOK_SCENE_IMAGES.howToPlayButton}
        imageWidth={63}
        imageHeight={70}
        label="게임 설명"
        onClick={onOpenHowToPlay}
      />
      <FlipbookEntranceIconButton
        imageSrc={
          isBgmMuted
            ? FLIPBOOK_SCENE_IMAGES.soundMutedButton
            : FLIPBOOK_SCENE_IMAGES.soundOnButton
        }
        imageWidth={67}
        imageHeight={70}
        label={isBgmMuted ? '배경음악 켜기' : '배경음악 음소거'}
        pressed={isBgmMuted}
        onClick={onToggleBgmMuted}
      />
      <PhoneLauncherButton className="size-[clamp(48px,4.6vw,70px)]" />
    </div>
  )
}

function FlipbookEntranceMobileScene({
  visible,
  isBusy,
  isBgmMuted,
  errorMessage,
  onCreateRoom,
  onOpenRoomCodeModal,
  onOpenHowToPlay,
  onToggleBgmMuted,
}: {
  visible: boolean
  isBusy: boolean
  isBgmMuted: boolean
  errorMessage: string | null
  onCreateRoom: () => void
  onOpenRoomCodeModal: () => void
  onOpenHowToPlay: () => void
  onToggleBgmMuted: () => void
}) {
  return (
    <div className="absolute inset-0 z-10 overflow-hidden sm:hidden">
        <div
          aria-hidden
          className="pointer-events-none absolute overflow-hidden"
          style={{
            height: '16.76%',
            left: '21.12%',
            top: '-3.49%',
            width: '139.47%',
          }}
        >
          <div className="relative h-[79.46%] w-[98.97%] rotate-[3.2deg] overflow-hidden">
            <Image
              src={FLIPBOOK_SCENE_IMAGES.furnitureSprite}
              alt=""
              width={1536}
              height={1024}
              loading="eager"
              sizes="140vw"
              className="absolute h-[593.26%] w-[191.05%] max-w-none -left-[36.96%] -top-[33.16%]"
            />
          </div>
        </div>

        <div
          aria-hidden
          className="pointer-events-none absolute overflow-hidden"
          style={{
            height: '55.94%',
            left: '-10.68%',
            top: '42.09%',
            width: '169.66%',
          }}
        >
          <Image
            src={FLIPBOOK_SCENE_IMAGES.furnitureSprite}
            alt=""
            width={1536}
            height={1024}
            loading="eager"
            sizes="170vw"
            className="absolute h-[181.75%] w-[199.94%] max-w-none -left-[0.03%] -top-[81.75%]"
          />
        </div>

      <motion.div
        className="absolute inset-0 z-20"
        initial={false}
        animate={visible ? { opacity: 1, y: 0 } : { opacity: 0, y: '1.96%' }}
        transition={{ duration: 0.42, ease: [0.22, 0.8, 0.22, 1] }}
      >
        <div className="absolute right-[7.52%] top-[5.34%] flex items-center justify-end gap-2">
          <FlipbookEntranceMobileIconButton
            imageSrc={FLIPBOOK_SCENE_IMAGES.howToPlayButton}
            imageWidth={63}
            imageHeight={70}
            label="게임 설명"
            className="aspect-[49/54] w-12"
            onClick={onOpenHowToPlay}
          />
          <FlipbookEntranceMobileIconButton
            imageSrc={
              isBgmMuted
                ? FLIPBOOK_SCENE_IMAGES.soundMutedButton
                : FLIPBOOK_SCENE_IMAGES.soundOnButton
            }
            imageWidth={67}
            imageHeight={70}
            label={isBgmMuted ? '배경음악 켜기' : '배경음악 음소거'}
            pressed={isBgmMuted}
            className="aspect-[51/54] w-12"
            onClick={onToggleBgmMuted}
          />
          <PhoneLauncherButton className="size-12" />
        </div>

        <div className="absolute left-1/2 top-[24.54%] w-[86.17%] -translate-x-1/2">
          <div className="relative aspect-[486/238] w-full overflow-hidden">
            <Image
              src={FLIPBOOK_SCENE_IMAGES.titleLogoSprite}
              alt="플립북"
              width={1536}
              height={1024}
              priority
              sizes="86vw"
              className="absolute h-[184.45%] w-auto max-w-none -left-[17.95%] -top-[56.72%]"
            />
          </div>
        </div>

        <div className="absolute left-1/2 top-[58.43%] flex w-[41.5%] -translate-x-1/2 flex-col items-center gap-[2.18svh]">
          <FlipbookEntranceMobileActionButton
            imageCropClassName="h-[383.52%] w-auto -left-[17.95%] -top-[150.56%]"
            label={isBusy ? '처리 중' : '방 만들기'}
            disabled={isBusy}
            onClick={onCreateRoom}
          />
          <FlipbookEntranceMobileActionButton
            imageCropClassName="h-[383.52%] w-auto -left-[128.98%] -top-[150.56%]"
            label={isBusy ? '처리 중' : '입장하기'}
            disabled={isBusy}
            onClick={onOpenRoomCodeModal}
          />
        </div>

        {errorMessage && (
          <p className="caption-b absolute left-1/2 top-[76%] w-[86.17%] -translate-x-1/2 rounded-full border border-flipbook-light bg-flipbook-paper/88 px-4 py-3 text-center text-flipbook-deep shadow-[0_8px_18px_var(--color-flipbook-shadow)]">
            {errorMessage}
          </p>
        )}
      </motion.div>
    </div>
  )
}

function FlipbookEntranceMobileIconButton({
  imageSrc,
  imageWidth,
  imageHeight,
  label,
  pressed,
  className,
  onClick,
}: {
  imageSrc: string
  imageWidth: number
  imageHeight: number
  label: string
  pressed?: boolean
  className: string
  onClick: () => void
}) {
  return (
    <motion.button
      type="button"
      aria-label={label}
      aria-pressed={pressed}
      title={label}
      whileTap={{ scale: 0.96 }}
      className={`relative ${className}`}
      onClick={onClick}
    >
      <Image
        src={imageSrc}
        alt=""
        width={imageWidth}
        height={imageHeight}
        sizes="54px"
        className="h-full w-auto object-contain"
      />
      <span className="sr-only">{label}</span>
    </motion.button>
  )
}

function FlipbookEntranceMobileActionButton({
  imageCropClassName,
  label,
  disabled,
  onClick,
}: {
  imageCropClassName: string
  label: string
  disabled: boolean
  onClick: () => void
}) {
  return (
    <motion.button
      type="button"
      aria-label={label}
      disabled={disabled}
      whileTap={{ scale: 0.97 }}
      className="relative aspect-[635/267] w-full overflow-visible disabled:cursor-not-allowed disabled:opacity-70"
      onClick={onClick}
    >
      <span className="absolute inset-0 overflow-hidden" aria-hidden>
        <Image
          src={FLIPBOOK_SCENE_IMAGES.actionButtonsSprite}
          alt=""
          width={1536}
          height={1024}
          loading="eager"
          quality={FLIPBOOK_ACTION_BUTTON_IMAGE_QUALITY}
          sizes={FLIPBOOK_ACTION_BUTTON_IMAGE_SIZES}
          className={`absolute w-auto max-w-none ${imageCropClassName}`}
        />
      </span>
      <span className="sr-only">{label}</span>
    </motion.button>
  )
}

function FlipbookEntranceIconButton({
  imageSrc,
  imageWidth,
  imageHeight,
  label,
  pressed,
  onClick,
}: {
  imageSrc: string
  imageWidth: number
  imageHeight: number
  label: string
  pressed?: boolean
  onClick: () => void
}) {
  return (
    <motion.button
      type="button"
      aria-label={label}
      aria-pressed={pressed}
      title={label}
      whileHover={{ y: -3, scale: 1.035 }}
      whileTap={{ y: 1, scale: 0.97 }}
      className="relative grid size-[clamp(48px,4.6vw,70px)] place-items-center focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-flipbook-primary"
      onClick={onClick}
    >
      <Image
        src={imageSrc}
        alt=""
        width={imageWidth}
        height={imageHeight}
        sizes="70px"
        className="h-full w-auto object-contain"
      />
      <span className="sr-only">{label}</span>
    </motion.button>
  )
}

function FlipbookEntranceDropScene({
  shouldInstantCompleteIntro,
}: {
  shouldInstantCompleteIntro: boolean
}) {
  return (
    <div aria-hidden className="pointer-events-none absolute inset-0">
      {DROP_LAYERS.map((layer) => (
        <motion.div
          key={`${layer.key}-${shouldInstantCompleteIntro ? 'done' : 'drop'}`}
          className={`absolute overflow-hidden ${layer.className}`}
          initial={{
            opacity: 0,
            y: -layer.fallDistance,
            scale: 0.98,
          }}
          animate={
            shouldInstantCompleteIntro
              ? {
                  opacity: 1,
                  y: 0,
                  scale: 1,
                  transition: { duration: 0 },
                }
              : {
                  opacity: 1,
                  y: 0,
                  scale: 1,
                  transition: {
                    y: {
                      ...DROP_SPRING_TRANSITION,
                      delay: layer.delay,
                    },
                    opacity: {
                      delay: layer.delay,
                      duration: 0.18,
                      ease: 'easeOut',
                    },
                    scale: {
                      delay: layer.delay,
                      duration: 0.36,
                      ease: [0.22, 0.8, 0.22, 1],
                    },
                  },
                }
          }
        >
          <div
            className="absolute inset-0"
            style={{ rotate: `${layer.rotate}deg` }}
          >
            <Image
              src={FLIPBOOK_SCENE_IMAGES.furnitureSprite}
              alt=""
              width={1536}
              height={1024}
              loading="eager"
              sizes="100vw"
              className={`absolute max-w-none ${layer.imageClassName}`}
            />
          </div>
        </motion.div>
      ))}
    </div>
  )
}

function FlipbookEntranceSketchbook({
  scrollZoneRef,
  activeFrameIndex,
  scrollSpacerHeight,
  onScroll,
  isInteractive,
  shouldInstantCompleteIntro,
}: {
  scrollZoneRef: RefObject<HTMLDivElement | null>
  activeFrameIndex: number
  scrollSpacerHeight: string
  onScroll: ReturnType<typeof useFlipbookEntranceWheelFrames>['handleScroll']
  isInteractive: boolean
  shouldInstantCompleteIntro: boolean
}) {
  const activeEntranceFrame =
    FLIPBOOK_ENTRANCE_FRAMES[activeFrameIndex] ?? FLIPBOOK_ENTRANCE_FRAMES[0]

  return (
    <motion.div
      key={shouldInstantCompleteIntro ? 'sketchbook-done' : 'sketchbook-drop'}
      className="absolute left-[43.94%] top-[26.82%] z-10 flex h-[63.30%] w-[48.48%] items-center justify-center"
      aria-label="스케치북 플립북 재생 구역"
      role="region"
      tabIndex={isInteractive ? 0 : -1}
      initial={{ opacity: 0, y: -820, scale: 0.98 }}
      animate={
        shouldInstantCompleteIntro
          ? {
              opacity: 1,
              y: 0,
              scale: 1,
              transition: { duration: 0 },
            }
          : {
              opacity: 1,
              y: 0,
              scale: 1,
              transition: {
                y: {
                  ...DROP_SPRING_TRANSITION,
                  delay: 0.54,
                },
                opacity: {
                  delay: 0.54,
                  duration: 0.18,
                  ease: 'easeOut',
                },
                scale: {
                  delay: 0.54,
                  duration: 0.36,
                  ease: [0.22, 0.8, 0.22, 1],
                },
              },
            }
      }
    >
      <div className="relative h-[98.01%] w-[98.94%]" style={{ rotate: '0.86deg' }}>
        <Image
          src={FLIPBOOK_SCENE_IMAGES.sketchbook}
          alt=""
          fill
          priority
          sizes="50vw"
          className="pointer-events-none object-contain"
        />
        <div
          ref={scrollZoneRef}
          aria-label="플립북애니메이션재생구역"
          className={`absolute z-10 overflow-hidden rounded-[8px] ${isInteractive ? 'pointer-events-auto' : 'pointer-events-none'}`}
          role="region"
          tabIndex={isInteractive ? 0 : -1}
          style={{
            left: '20.2%',
            top: '25.8%',
            width: '57.6%',
            height: '43.8%',
            rotate: '5.9deg',
          }}
        >
          <Image
            key={activeEntranceFrame.src}
            src={activeEntranceFrame.src}
            alt={activeEntranceFrame.alt}
            fill
            priority={activeFrameIndex === 0}
            loading={activeFrameIndex === 0 ? undefined : 'eager'}
            unoptimized
            sizes="35vw"
            className="object-contain"
          />
          {activeFrameIndex === FLIPBOOK_SCROLL_HINT_FRAME_INDEX && (
            <FlipbookEntranceScrollHint />
          )}
          <div
            aria-label="스케치북 프레임 스크롤"
            className="absolute inset-0 z-10 overflow-y-scroll overscroll-contain [scrollbar-width:none] [&::-webkit-scrollbar]:hidden"
            role="region"
            tabIndex={isInteractive ? 0 : -1}
            onScroll={onScroll}
          >
            <div style={{ height: scrollSpacerHeight }} />
          </div>
        </div>
      </div>
    </motion.div>
  )
}

function FlipbookEntranceScrollHint() {
  return (
    <div className="pointer-events-none absolute left-1/2 top-1/2 z-20 w-[12%] -translate-x-1/2 -translate-y-1/2 text-[#111111]">
      <p className="absolute bottom-[calc(100%+0.18rem)] left-1/2 w-max -translate-x-1/2 text-center [font-family:var(--font-paperlogy)] text-[clamp(0.56rem,1.06vw,0.86rem)] font-semibold leading-none text-[#111111]">
        {FLIPBOOK_SCROLL_HINT_LABEL}
      </p>
      <Lottie
        animationData={downArrowAnimation}
        loop
        autoplay
        aria-hidden
        rendererSettings={{ preserveAspectRatio: 'xMidYMid meet' }}
        className="h-auto w-full min-w-8 max-w-14"
      />
    </div>
  )
}

function FlipbookEntranceActions({
  visible,
  interactive,
  shouldInstantCompleteIntro,
  isBusy,
  errorMessage,
  actionHandlers,
}: {
  visible: boolean
  interactive: boolean
  shouldInstantCompleteIntro: boolean
  isBusy: boolean
  errorMessage: string | null
  actionHandlers: Record<(typeof FLIPBOOK_ENTRANCE_ACTIONS)[number]['key'], () => void>
}) {
  return (
    <motion.div
      className="pointer-events-none absolute inset-0 z-20"
      initial={false}
      animate={
        visible
          ? {
              opacity: 1,
              y: 0,
              transition: {
                duration: shouldInstantCompleteIntro ? 0 : 0.62,
                ease: [0.22, 0.8, 0.22, 1],
              },
            }
          : {
              opacity: 0,
              y: 28,
              transition: { duration: 0.18 },
            }
      }
    >
      <div className="absolute left-[13.49%] top-[31.76%] aspect-[486/238] w-[28.49%] overflow-hidden">
        <Image
          src={FLIPBOOK_SCENE_IMAGES.titleLogoSprite}
          alt="플립북"
          width={1536}
          height={1024}
          priority
          sizes="30vw"
          className="absolute h-[184.45%] w-auto max-w-none -left-[17.95%] -top-[56.72%]"
        />
      </div>

      {FLIPBOOK_ENTRANCE_ACTIONS.map((action) => (
        <FlipbookEntranceImageButton
          key={action.key}
          buttonClassName={action.buttonClassName}
          imageCropClassName={action.imageCropClassName}
          label={isBusy ? '처리 중' : action.label}
          disabled={isBusy || !interactive}
          onClick={actionHandlers[action.key]}
        />
      ))}

      {errorMessage && (
        <p className="caption-b absolute left-[11.90%] top-[75.8%] w-[31.67%] rounded-full border border-flipbook-light bg-flipbook-paper/88 px-5 py-3 text-center text-flipbook-deep shadow-[0_8px_18px_var(--color-flipbook-shadow)]">
          {errorMessage}
        </p>
      )}
    </motion.div>
  )
}

function FlipbookEntranceImageButton({
  buttonClassName,
  imageCropClassName,
  label,
  disabled,
  onClick,
}: {
  buttonClassName: string
  imageCropClassName: string
  label: string
  disabled: boolean
  onClick: () => void
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      aria-label={label}
      className={`pointer-events-auto absolute overflow-visible transform-gpu transition-transform duration-150 ease-out will-change-transform hover:-translate-y-1 active:translate-y-px active:scale-[0.99] focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-flipbook-primary disabled:cursor-not-allowed disabled:opacity-70 disabled:hover:translate-y-0 disabled:active:translate-y-0 disabled:active:scale-100 ${buttonClassName}`}
    >
      <span className="absolute inset-0 overflow-hidden" aria-hidden>
        <Image
          src={FLIPBOOK_SCENE_IMAGES.actionButtonsSprite}
          alt=""
          width={1536}
          height={1024}
          loading="eager"
          quality={FLIPBOOK_ACTION_BUTTON_IMAGE_QUALITY}
          sizes={FLIPBOOK_ACTION_BUTTON_IMAGE_SIZES}
          className={`absolute w-auto max-w-none ${imageCropClassName}`}
        />
      </span>
      <span className="sr-only">{label}</span>
    </button>
  )
}

function FlipbookRoomCodeModal({
  open,
  inputRef,
  roomCodeDraft,
  isBusy,
  errorMessage,
  onRoomCodeDraftChange,
  onClose,
  onSubmit,
}: {
  open: boolean
  inputRef: RefObject<HTMLInputElement | null>
  roomCodeDraft: string
  isBusy: boolean
  errorMessage: string | null
  onRoomCodeDraftChange: (roomCode: string) => void
  onClose: () => void
  onSubmit: () => void
}) {
  if (!open) return null

  return (
    <motion.div
      className="fixed inset-0 z-50 grid place-items-start bg-[#2a1f3a]/28 px-5 py-[calc(1rem+env(safe-area-inset-top))] backdrop-blur-[3px] sm:place-items-center"
      role="presentation"
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) {
          onClose()
        }
      }}
    >
      <motion.form
        className="relative max-h-[calc(100dvh-2rem)] w-full max-w-[460px] overflow-y-auto rounded-[28px] border border-[#f5bdca] bg-[#fffaf5] px-6 pb-7 pt-7 text-flipbook-ink shadow-[0_24px_70px_rgb(92_31_38_/_24%)] sm:px-8 sm:pb-8"
        initial={{ y: 20, scale: 0.96, opacity: 0 }}
        animate={{ y: 0, scale: 1, opacity: 1 }}
        transition={{ duration: 0.22, ease: [0.22, 0.8, 0.2, 1] }}
        onSubmit={(event) => {
          event.preventDefault()
          onSubmit()
        }}
        onKeyDown={(event) => {
          if (event.key === 'Escape') {
            onClose()
          }
        }}
      >
        <div
          aria-hidden
          className="absolute -right-10 -top-10 size-32 rounded-full bg-[#ffe7ef]"
        />
        <div
          aria-hidden
          className="absolute -bottom-16 -left-14 size-40 rounded-full bg-[#fff3a8]/70"
        />

        <button
          type="button"
          onClick={onClose}
          disabled={isBusy}
          className="absolute right-5 top-5 grid size-10 place-items-center rounded-full bg-white text-flipbook-deep shadow-[0_5px_14px_rgb(92_31_38_/_12%)] transition hover:-translate-y-0.5 disabled:cursor-not-allowed disabled:opacity-60"
          aria-label="입장 코드 모달 닫기"
        >
          <X className="size-5" aria-hidden />
        </button>

        <div className="relative">
          <div className="mx-auto grid size-16 place-items-center rounded-[20px] bg-[#fdebf0] text-[#f45d8d] shadow-[inset_0_0_0_1px_rgb(244_93_141_/_16%)]">
            <KeyRound className="size-8" aria-hidden />
          </div>
          <h2 className="h2-b mt-5 text-center text-flipbook-ink">입장 코드 입력</h2>
          <p className="body-r mt-2 text-center text-flipbook-deep/75">
            친구가 알려준 코드를 입력하면 바로 같은 플립북 방으로 들어가요.
          </p>

          <label className="caption-b mt-7 block text-flipbook-deep" htmlFor="flipbook-room-code-modal">
            입장 코드
          </label>
          <input
            ref={inputRef}
            id="flipbook-room-code-modal"
            value={roomCodeDraft}
            onChange={(event) => onRoomCodeDraftChange(event.target.value.toUpperCase())}
            maxLength={12}
            placeholder="예: AB3K9Q"
            className="h3-b mt-2 h-14 w-full rounded-[16px] border border-[#f5bdca] bg-white px-5 text-center uppercase tracking-[0.08em] text-flipbook-ink outline-none shadow-[inset_0_2px_8px_rgb(92_31_38_/_6%)] transition focus:border-[#f45d8d] focus:ring-4 focus:ring-[#f45d8d]/15"
          />

          {errorMessage && (
            <p className="caption-b mt-3 rounded-[12px] bg-[#fdebf0] px-4 py-3 text-center text-flipbook-deep">
              {errorMessage}
            </p>
          )}

          <button
            type="submit"
            disabled={isBusy}
            className="body-l-b mt-6 inline-flex h-14 w-full items-center justify-center gap-2 rounded-[18px] bg-[#f45d8d] text-white shadow-[0_12px_24px_rgb(244_93_141_/_30%)] transition hover:-translate-y-0.5 disabled:cursor-not-allowed disabled:opacity-70"
          >
            <Sparkles className="size-5" aria-hidden />
            {isBusy ? '입장 중' : '입장하기'}
          </button>
        </div>
      </motion.form>
    </motion.div>
  )
}
