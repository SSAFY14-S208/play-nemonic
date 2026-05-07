'use client'

import { useRef } from 'react'
import Image from 'next/image'
import { motion } from 'motion/react'
import { useFlipbookEntranceTimeline } from '../hooks'
import FlipbookPaperBackground from './FlipbookPaperBackground'

interface FlipbookEntranceViewProps {
  onCreateRoom: () => void
  onEnterRoom: () => void
}

const FLIPBOOK_ENTRANCE_FRAMES = Array.from({ length: 12 }, (unusedValue, frameIndex) => {
  const frameNumber = String(frameIndex + 1).padStart(2, '0')

  return {
    src: `/images/flipbook-entrance/${frameNumber}.png`,
    alt: `플립북 입장 애니메이션 ${frameIndex + 1}번째 장면`,
  }
})
const FLIPBOOK_BUTTON_IMAGES = {
  createRoom: '/images/flipbook-buttons/create-room.png',
  enterRoom: '/images/flipbook-buttons/enter-room.png',
}

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
  onCreateRoom,
  onEnterRoom,
}: FlipbookEntranceViewProps) {
  const sectionRef = useRef<HTMLElement>(null)
  const timeline = useFlipbookEntranceTimeline(sectionRef, FLIPBOOK_ENTRANCE_FRAMES.length)
  const activeEntranceFrame =
    FLIPBOOK_ENTRANCE_FRAMES[timeline.activeFrameIndex] ?? FLIPBOOK_ENTRANCE_FRAMES.at(-1)
  const actionHandlers = {
    'create-room': onCreateRoom,
    'enter-room': onEnterRoom,
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
            <h1
              className="h1-b origin-center scale-150 text-flipbook-ink drop-shadow-[0_5px_0_rgba(251,188,196,0.62)]"
            >
              플립북
            </h1>
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
            className="absolute inset-x-0 bottom-[max(3.5svh,18px)] z-20 mx-auto grid w-full max-w-[680px] grid-cols-2 items-center gap-4 px-5 sm:gap-6"
            style={{
              opacity: timeline.actionOpacity,
              y: timeline.actionY,
            }}
          >
            {FLIPBOOK_ENTRANCE_ACTIONS.map((action) => (
              <FlipbookEntranceImageButton
                key={action.key}
                imageSrc={action.imageSrc}
                label={action.label}
                onClick={actionHandlers[action.key]}
              />
            ))}
          </motion.div>
        </div>
      </div>
    </section>
  )
}

function FlipbookEntranceImageButton({
  imageSrc,
  label,
  onClick,
}: {
  imageSrc: string
  label: string
  onClick: () => void
}) {
  return (
    <motion.button
      type="button"
      onClick={onClick}
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
