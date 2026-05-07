'use client'

import { useRef, type ReactNode } from 'react'
import Image from 'next/image'
import { motion } from 'motion/react'
import { ArrowRight, BookOpen, DoorOpen, Sparkles } from 'lucide-react'
import { cn } from '@/shared/libs'
import { useFlipbookEntranceTimeline } from '../hooks'

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
const FLIPBOOK_BACKGROUND_IMAGES = {
  paper: '/images/flipbook-background/paper-background.png',
  stars: '/images/flipbook-background/stars.png',
  dots: '/images/flipbook-background/dots.png',
  crayon: '/images/flipbook-background/crayon-corners.png',
}

export default function FlipbookEntranceView({
  onCreateRoom,
  onEnterRoom,
}: FlipbookEntranceViewProps) {
  const sectionRef = useRef<HTMLElement>(null)
  const timeline = useFlipbookEntranceTimeline(sectionRef, FLIPBOOK_ENTRANCE_FRAMES.length)
  const activeEntranceFrame =
    FLIPBOOK_ENTRANCE_FRAMES[timeline.activeFrameIndex] ?? FLIPBOOK_ENTRANCE_FRAMES.at(-1)

    //svh 범위 260~340(클수록 느림)
  return (
    <section ref={sectionRef} className="relative h-[260svh] bg-flipbook-background text-flipbook-ink">
      <div className="sticky top-0 grid h-[100svh] min-h-[620px] overflow-hidden px-5 py-8">
        <div className="relative grid h-full place-items-center">
          <FlipbookEntranceBackground timeline={timeline} />

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
            <div
              aria-hidden
              className="absolute inset-0 -translate-x-3 -translate-y-3 rounded-[18px] bg-flipbook-light/55 shadow-[0_18px_32px_var(--color-flipbook-shadow)]"
            />
            <div
              aria-hidden
              className="absolute inset-0 -translate-x-1.5 -translate-y-1.5 rounded-[18px] bg-flipbook-result-soft shadow-[0_14px_26px_var(--color-flipbook-shadow)]"
            />
            <div className="absolute inset-0 overflow-hidden rounded-[18px] bg-flipbook-paper shadow-[0_22px_48px_var(--color-flipbook-shadow)]">
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
              className="absolute inset-0 origin-top-left rounded-[18px] bg-flipbook-paper shadow-[0_18px_34px_rgb(94_31_37_/_20%)]"
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
            className="absolute inset-x-0 top-[5.5svh] z-20 mx-auto flex w-full max-w-[1180px] flex-col items-center px-4 text-center"
            style={{
              opacity: timeline.actionOpacity,
              y: timeline.actionY,
            }}
          >
            <div className="relative z-10">
                <Sparkles
                  className="absolute -right-8 top-0 size-5 rotate-12 text-flipbook-primary"
                  aria-hidden
                />
                <h1
                  className="h1-b text-flipbook-ink drop-shadow-[0_5px_0_rgba(251,188,196,0.62)]"
                  style={{ fontSize: '62px', lineHeight: 1.02 }}
                >
                  플립북
                </h1>
            </div>

            <div className="relative z-20 mt-7 grid w-full max-w-[820px] items-start gap-5 sm:grid-cols-2">
              <FlipbookEntranceActionButton
                icon={<BookOpen className="size-5" aria-hidden />}
                label="방 만들기"
                variant="primary"
                onClick={onCreateRoom}
              />
              <FlipbookEntranceActionButton
                icon={<DoorOpen className="size-5" aria-hidden />}
                label="방 입장"
                variant="secondary"
                onClick={onEnterRoom}
              />
            </div>

          </motion.div>
        </div>
      </div>
    </section>
  )
}

function FlipbookEntranceBackground({
  timeline,
}: {
  timeline: ReturnType<typeof useFlipbookEntranceTimeline>
}) {
  return (
    <div aria-hidden className="pointer-events-none absolute inset-0 z-0 overflow-hidden">
      <Image
        src={FLIPBOOK_BACKGROUND_IMAGES.paper}
        alt=""
        fill
        priority
        sizes="100vw"
        className="object-cover"
      />
      <div className="absolute inset-0 bg-flipbook-background/10" />
      <motion.div
        className="absolute inset-[-3%]"
        style={timeline.background.crayon}
      >
        <Image
          src={FLIPBOOK_BACKGROUND_IMAGES.crayon}
          alt=""
          fill
          sizes="106vw"
          className="object-fill"
        />
      </motion.div>
      <motion.div
        className="absolute inset-[-3%]"
        style={timeline.background.stars}
      >
        <Image
          src={FLIPBOOK_BACKGROUND_IMAGES.stars}
          alt=""
          fill
          sizes="106vw"
          className="object-fill"
        />
      </motion.div>
      <motion.div
        className="absolute inset-[-3%]"
        style={timeline.background.dots}
      >
        <Image
          src={FLIPBOOK_BACKGROUND_IMAGES.dots}
          alt=""
          fill
          sizes="106vw"
          className="object-fill"
        />
      </motion.div>
    </div>
  )
}

function FlipbookEntranceActionButton({
  icon,
  label,
  variant,
  onClick,
}: {
  icon: ReactNode
  label: string
  variant: 'primary' | 'secondary'
  onClick: () => void
}) {
  return (
    <motion.button
      type="button"
      onClick={onClick}
      whileHover={{ y: -5, scale: 1.025 }}
      whileTap={{ y: 1, scale: 0.985 }}
      className={cn(
        'group relative inline-flex min-h-[86px] items-center justify-between overflow-hidden rounded-[18px] border-[3px] px-5 text-left shadow-[0_12px_0_rgb(94_31_37_/_18%),0_20px_34px_var(--color-flipbook-shadow)] transition-colors',
        'h-[86px] self-start',
        variant === 'primary' &&
          'border-flipbook-ink bg-flipbook-paper text-flipbook-ink',
        variant === 'secondary' &&
          'border-flipbook-ink bg-flipbook-paper text-flipbook-ink',
      )}
    >
      <span
        aria-hidden
        className={cn(
          'absolute inset-x-0 bottom-0 h-2 transition-transform group-hover:scale-x-100',
          variant === 'primary' && 'bg-flipbook-primary',
          variant === 'secondary' && 'bg-flipbook-light',
        )}
      />
      <span
        aria-hidden
        className="absolute left-4 top-4 size-2 rounded-full bg-flipbook-light"
      />
      <span
        aria-hidden
        className="absolute right-4 top-4 size-2 rounded-full bg-flipbook-light"
      />
      <span className="flex items-center gap-4">
        <span
          className={cn(
            'grid size-12 place-items-center rounded-[14px] border-2 border-flipbook-ink shadow-[inset_0_-4px_0_rgb(94_31_37_/_12%)]',
            variant === 'primary' && 'bg-flipbook-primary',
            variant === 'secondary' && 'bg-flipbook-result-soft',
          )}
        >
          {icon}
        </span>
        <span className="body-l-b text-flipbook-ink">{label}</span>
      </span>
      <ArrowRight
        className="size-5 text-flipbook-muted transition-transform group-hover:translate-x-1 group-hover:text-flipbook-ink"
        aria-hidden
      />
    </motion.button>
  )
}
