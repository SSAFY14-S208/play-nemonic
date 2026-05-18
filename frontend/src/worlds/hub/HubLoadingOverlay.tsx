import type { CSSProperties } from 'react'
import { motion } from 'motion/react'
import { cn } from '@/shared/libs'
import HubLoadingHouseLottie from './HubLoadingHouseLottie'
import { useHubLoadingOverlay } from './hooks'

const HUB_LOADING_PRIMARY_COLOR = '#f49cc8'
const HUB_LOADING_PRIMARY_HOVER_COLOR = '#ed86bd'

const HUB_LOADING_GRADIENT = [
  'radial-gradient(circle at 28% 32%, rgba(255, 220, 232, 0.9) 0%, transparent 55%)',
  'radial-gradient(circle at 72% 28%, rgba(255, 240, 214, 0.8) 0%, transparent 55%)',
  'radial-gradient(circle at 50% 82%, rgba(220, 210, 255, 0.75) 0%, transparent 55%)',
].join(', ')

export default function HubLoadingOverlay({
  isCanvasReady,
}: {
  isCanvasReady: boolean
}) {
  const {
    displayProgress,
    enterHub,
    hasConfirmedHubEntry,
    isReady,
    isVisible,
    shouldShowPlayButton,
    statusText,
    subtitleText,
  } = useHubLoadingOverlay(isCanvasReady)

  return (
    <div
      data-hub-loading-overlay="true"
      className={cn(
        'fixed inset-0 z-[80] flex items-center justify-center overflow-hidden bg-surface-default',
        !isVisible && 'pointer-events-none',
      )}
      style={{
        opacity: isVisible ? 1 : 0,
        visibility: isVisible ? 'visible' : 'hidden',
        '--hub-loading-primary': HUB_LOADING_PRIMARY_COLOR,
        '--hub-loading-primary-hover': HUB_LOADING_PRIMARY_HOVER_COLOR,
      } as CSSProperties}
      aria-hidden={!isVisible}
    >
      {isVisible && (
        <motion.div
          aria-hidden
          className="pointer-events-none absolute -inset-[20%] blur-3xl"
          style={{ background: HUB_LOADING_GRADIENT }}
          animate={{
            x: ['0%', '4%', '-3%', '0%'],
            y: ['0%', '-3%', '4%', '0%'],
          }}
          transition={{
            duration: 16,
            ease: 'easeInOut',
            repeat: Infinity,
          }}
        />
      )}
      <div className="relative flex w-[min(21rem,calc(100vw-3rem))] flex-col items-center gap-5 text-center">
        {isVisible && <HubLoadingHouseLottie />}
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
        <div className="h-2 w-full overflow-hidden rounded-full bg-surface-subtle shadow-[inset_0_1px_4px_rgb(91_72_118_/_12%)]">
          <div
            className="h-full w-full origin-left rounded-full bg-[var(--hub-loading-primary)] transition-transform duration-300 ease-out"
            style={{ transform: `scaleX(${displayProgress / 100})` }}
          />
        </div>
        <span className="caption-b text-fg-primary">{displayProgress}%</span>
      </div>
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
