import { motion } from 'motion/react'
import { cn } from '@/shared/libs'
import HubLoadingHouseLottie from './HubLoadingHouseLottie'
import { useHubLoadingOverlay } from './hooks'

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
    isReady,
    isVisible,
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
      }}
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
        <HubLoadingHouseLottie />
        <div className="flex flex-col items-center gap-3">
          <p className="h3-b text-fg-primary">
            {isReady ? '준비 완료. 이제 놀러 들어가요!' : statusText}
          </p>
          <p className="caption-m text-fg-secondary">{subtitleText}</p>
        </div>
        <div className="h-2 w-full overflow-hidden rounded-full bg-surface-subtle shadow-[inset_0_1px_4px_rgb(91_72_118_/_12%)]">
          <div
            className="h-full w-full origin-left rounded-full bg-primary-1 transition-transform duration-300 ease-out"
            style={{ transform: `scaleX(${displayProgress / 100})` }}
          />
        </div>
        <span className="caption-b text-fg-primary">{displayProgress}%</span>
      </div>
    </div>
  )
}
