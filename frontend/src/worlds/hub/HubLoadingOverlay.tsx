import type { CSSProperties } from 'react'
import Image from 'next/image'
import { motion } from 'motion/react'
import { cn } from '@/shared/libs'
import {
  HUB_ROOM_REVEAL_DURATION_MS,
  PERCENT_FADE_OUT_DURATION_MS,
  useHubLoadingOverlay,
} from './hooks'
import styles from './HubLoadingOverlay.module.css'

const HUB_LOADING_PRIMARY_COLOR = '#f49cc8'
const HUB_LOADING_PRIMARY_HOVER_COLOR = '#ed86bd'

const HUB_LOADING_GRADIENT = [
  'radial-gradient(circle at 28% 32%, rgba(255, 220, 232, 0.9) 0%, transparent 55%)',
  'radial-gradient(circle at 72% 28%, rgba(255, 240, 214, 0.8) 0%, transparent 55%)',
  'radial-gradient(circle at 50% 82%, rgba(220, 210, 255, 0.75) 0%, transparent 55%)',
].join(', ')

const BAR_BASE_SHADOW = 'inset 0 1px 4px rgb(91 72 118 / 12%)'

const PERCENT_FADE_OUT_DURATION_SECONDS = PERCENT_FADE_OUT_DURATION_MS / 1000

export default function HubLoadingOverlay({
  isCanvasReady,
}: {
  isCanvasReady: boolean
}) {
  const {
    barFillRef,
    enterHub,
    hasConfirmedHubEntry,
    hasReachedFull,
    isReady,
    isRevealingRoom,
    isVisible,
    percentTextRef,
    shouldShowPlayButton,
    statusText,
    subtitleText,
  } = useHubLoadingOverlay(isCanvasReady)

  return (
    <div
      data-hub-loading-overlay="true"
      data-hub-loading-revealing={isRevealingRoom ? 'true' : 'false'}
      className={cn(
        'fixed inset-0 z-[80] flex items-center justify-center overflow-hidden',
        !isRevealingRoom && 'bg-surface-default',
        (!isVisible || isRevealingRoom) && 'pointer-events-none',
      )}
      style={{
        opacity: isVisible ? 1 : 0,
        visibility: isVisible ? 'visible' : 'hidden',
        '--hub-loading-gradient': HUB_LOADING_GRADIENT,
        '--hub-loading-primary': HUB_LOADING_PRIMARY_COLOR,
        '--hub-loading-primary-hover': HUB_LOADING_PRIMARY_HOVER_COLOR,
        '--hub-room-reveal-duration': `${HUB_ROOM_REVEAL_DURATION_MS}ms`,
      } as CSSProperties}
      aria-hidden={!isVisible}
    >
      {isVisible && !isRevealingRoom && (
        <div
          aria-hidden
          className="pointer-events-none absolute -inset-[20%] blur-2xl"
          style={{ background: HUB_LOADING_GRADIENT }}
        />
      )}
      {isVisible && isRevealingRoom && (
        <>
          <div
            aria-hidden
            className={styles.roomRevealCurtain}
            data-hub-room-reveal-curtain="true"
          />
          <div
            aria-hidden
            className={styles.roomRevealRing}
            data-hub-room-reveal-ring="true"
          />
        </>
      )}
      <motion.div
        className="relative z-10 flex w-[min(21rem,calc(100vw-3rem))] flex-col items-center gap-5 text-center"
        initial={false}
        animate={
          isRevealingRoom
            ? { opacity: 0, scale: 0.92, y: -6 }
            : { opacity: 1, scale: 1, y: 0 }
        }
        transition={{
          duration: isRevealingRoom ? 0.18 : 0.28,
          ease: [0.16, 1, 0.3, 1],
        }}
      >
        {isVisible && (
          <Image
            src="/images/play-nemonic-logo.png"
            alt="Play! Nemonic"
            width={1672}
            height={941}
            priority
            draggable={false}
            className="pointer-events-none h-28 w-auto"
          />
        )}
        <div className="flex flex-col items-center gap-3">
          <p className="h3-b text-fg-primary">
            {isReady
              ? hasConfirmedHubEntry
                ? '허브로 들어가는 중이에요'
                : '준비 완료. 이제 놀러 들어가요!'
              : statusText}
          </p>
          <p className="caption-m text-fg-secondary">{subtitleText}</p>
        </div>
        <div
          className="h-2 w-full overflow-hidden rounded-full bg-surface-subtle"
          style={{ boxShadow: BAR_BASE_SHADOW }}
        >
          <div
            ref={barFillRef}
            className="h-full w-full origin-left rounded-full bg-[var(--hub-loading-primary)]"
            style={{ transform: 'scaleX(0)', willChange: 'transform' }}
          />
        </div>
        <motion.span
          ref={percentTextRef}
          className="caption-b text-fg-primary inline-block"
          animate={hasReachedFull ? { opacity: 0 } : { opacity: 1 }}
          transition={{
            duration: PERCENT_FADE_OUT_DURATION_SECONDS,
            ease: 'easeOut',
          }}
        >
          0%
        </motion.span>
      </motion.div>
      {shouldShowPlayButton && (
        <motion.div
          className="absolute bottom-[clamp(2rem,8vh,5rem)] left-1/2 z-10 -translate-x-1/2"
          initial={{ opacity: 0, y: 14 }}
          animate={{ opacity: 1, y: 0 }}
          exit={{ opacity: 0, y: 8 }}
        >
          <motion.button
            type="button"
            className="body-l-b inline-flex min-h-12 min-w-32 items-center justify-center rounded-[var(--radius-full)] bg-[var(--hub-loading-primary)] px-8 text-fg-inverse shadow-[0_12px_28px_rgb(244_156_200_/_28%)] transition-colors hover:bg-[var(--hub-loading-primary-hover)] focus-visible:outline focus-visible:outline-3 focus-visible:outline-offset-4 focus-visible:outline-[var(--hub-loading-primary)] disabled:pointer-events-none disabled:opacity-60"
            whileHover={{ y: -2 }}
            whileTap={{ scale: 0.97 }}
            onClick={enterHub}
            aria-label="Play!"
          >
            Play!
          </motion.button>
        </motion.div>
      )}
    </div>
  )
}
