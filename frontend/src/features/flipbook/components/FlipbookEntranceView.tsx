'use client'

import { useEffect, useRef, useState, type RefObject } from 'react'
import Image from 'next/image'
import { KeyRound, Sparkles, X } from 'lucide-react'
import { motion } from 'motion/react'
import { useFlipbookEntranceTimeline } from '../hooks'
import FlipbookPaperBackground from './FlipbookPaperBackground'

interface FlipbookEntranceViewProps {
  roomCodeDraft: string
  isBusy: boolean
  errorMessage: string | null
  onRoomCodeDraftChange: (roomCode: string) => void
  onCreateRoom: () => void
  onEnterRoom: () => void
}

const FLIPBOOK_ENTRANCE_FRAME_COUNT = 12

const FLIPBOOK_ENTRANCE_FRAMES = Array.from(
  { length: FLIPBOOK_ENTRANCE_FRAME_COUNT },
  (unusedValue, frameIndex) => {
    const frameNumber = String(frameIndex + 1).padStart(2, '0')

    return {
      src: `/images/flipbook-entrance/${frameNumber}.webp`,
      alt: `플립북 입장 애니메이션 ${frameIndex + 1}번째 장면`,
    }
  },
)
const FLIPBOOK_BUTTON_IMAGES = {
  createRoom: '/images/flipbook-buttons/create-room.png',
  enterRoom: '/images/flipbook-buttons/enter-room.png',
}
const FLIPBOOK_LOGO_IMAGE = '/images/flipbook-logo-v2.webp'

const FLIPBOOK_ENTRANCE_ACTIONS = [
  {
    key: 'create-room',
    imageSrc: FLIPBOOK_BUTTON_IMAGES.createRoom,
    label: '방 만들기',
  },
  {
    key: 'enter-room',
    imageSrc: FLIPBOOK_BUTTON_IMAGES.enterRoom,
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
  const sectionRef = useRef<HTMLElement>(null)
  const roomCodeInputRef = useRef<HTMLInputElement>(null)
  const [isRoomCodeModalOpen, setIsRoomCodeModalOpen] = useState(false)
  const timeline = useFlipbookEntranceTimeline(sectionRef, FLIPBOOK_ENTRANCE_FRAMES.length)
  const activeEntranceFrame =
    FLIPBOOK_ENTRANCE_FRAMES[timeline.activeFrameIndex] ?? FLIPBOOK_ENTRANCE_FRAMES.at(-1)
  const actionHandlers = {
    'create-room': onCreateRoom,
    'enter-room': () => setIsRoomCodeModalOpen(true),
  }

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
    <section ref={sectionRef} className="relative h-[260svh] bg-flipbook-background text-flipbook-ink">
      <div className="sticky top-0 grid h-[100svh] min-h-[620px] overflow-hidden">
        <FlipbookPaperBackground layerStyles={timeline.background} />
        <div className="relative z-10 grid h-full place-items-center px-5 py-8">
          <motion.div
            className="absolute inset-x-0 top-[max(3svh,18px)] z-20 mx-auto flex justify-center px-5"
            style={{
              opacity: timeline.actionOpacity,
              y: timeline.actionY,
            }}
          >
            <Image
              src={FLIPBOOK_LOGO_IMAGE}
              alt="플립북"
              width={979}
              height={646}
              priority
              sizes="(max-width: 640px) 44vw, 300px"
              className="h-auto w-[min(44vw,300px)] drop-shadow-[0_12px_22px_rgba(251,188,196,0.36)]"
            />
          </motion.div>

          <motion.div
            className="relative z-10 aspect-[626/480] w-[min(82vw,626px)]"
            style={{
              opacity: timeline.frameOpacity,
              scale: timeline.frameScale,
              y: timeline.frameY,
              rotate: timeline.frameRotate,
              transformOrigin: 'center center',
            }}
          >
            <div className="absolute inset-0 overflow-hidden rounded-[8px] border border-flipbook-light bg-flipbook-paper shadow-[0_16px_26px_var(--color-flipbook-shadow)]">
              {activeEntranceFrame && (
                <Image
                  src={activeEntranceFrame.src}
                  alt={activeEntranceFrame.alt}
                  fill
                  priority={timeline.activeFrameIndex === 0}
                  unoptimized
                  sizes="(max-width: 768px) 82vw, 626px"
                  className="object-cover"
                />
              )}
            </div>
            <motion.div
              key={`blank-paper-flight-${timeline.activeFrameIndex}`}
              aria-hidden
              className="absolute inset-0 origin-top-left rounded-[8px] bg-flipbook-paper shadow-[0_12px_24px_var(--color-flipbook-shadow)]"
              initial={{
                opacity: timeline.activeFrameIndex === 0 ? 0 : 0.92,
                x: 0,
                y: 34,
                rotate: 0,
                scale: 1,
              }}
              animate={{
                opacity: 0,
                x: -246,
                y: -178,
                rotate: -22,
                scale: 0.86,
                filter: 'blur(0.8px)',
              }}
              transition={{
                duration: 0.38,
                ease: [0.14, 0.76, 0.18, 1],
              }}
            />
          </motion.div>

          <motion.div
            className="absolute inset-x-0 bottom-[max(3.5svh,18px)] z-20 mx-auto grid w-full max-w-[680px] gap-4 px-5 sm:gap-5"
            style={{
              opacity: timeline.actionOpacity,
              y: timeline.actionY,
            }}
          >
            <div className="grid grid-cols-2 items-center gap-4 sm:gap-6">
              {FLIPBOOK_ENTRANCE_ACTIONS.map((action) => (
                <FlipbookEntranceImageButton
                  key={action.key}
                  imageSrc={action.imageSrc}
                  label={isBusy ? '처리 중' : action.label}
                  disabled={isBusy}
                  onClick={actionHandlers[action.key]}
                />
              ))}
            </div>
            {errorMessage && !isRoomCodeModalOpen && (
              <p className="caption-b mx-auto max-w-[520px] rounded-full border border-flipbook-light bg-flipbook-paper/88 px-5 py-3 text-center text-flipbook-deep shadow-[0_8px_18px_var(--color-flipbook-shadow)]">
                {errorMessage}
              </p>
            )}
          </motion.div>
        </div>
      </div>

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
    </section>
  )
}

function FlipbookEntranceImageButton({
  imageSrc,
  label,
  disabled,
  onClick,
}: {
  imageSrc: string
  label: string
  disabled: boolean
  onClick: () => void
}) {
  return (
    <motion.button
      type="button"
      onClick={onClick}
      disabled={disabled}
      aria-label={label}
      whileHover={{ y: -4, scale: 1.025 }}
      whileTap={{ y: 1, scale: 0.985 }}
      className="relative aspect-[649/255] w-full overflow-hidden rounded-[18px] transition-transform focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-flipbook-primary"
    >
      <Image
        src={imageSrc}
        alt=""
        fill
        sizes="(max-width: 640px) 42vw, 320px"
        className="object-contain"
      />
      <span className="h4-b pointer-events-none absolute left-[30%] right-[20%] top-1/2 -translate-y-1/2 text-center text-flipbook-ink drop-shadow-[0_2px_0_rgb(255_255_255_/_80%)]">
        {label}
      </span>
    </motion.button>
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
      className="fixed inset-0 z-50 grid place-items-center bg-[#2a1f3a]/28 px-5 backdrop-blur-[3px]"
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
        className="relative w-full max-w-[460px] overflow-hidden rounded-[28px] border border-[#f5bdca] bg-[#fffaf5] px-8 pb-8 pt-7 text-flipbook-ink shadow-[0_24px_70px_rgb(92_31_38_/_24%)]"
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
