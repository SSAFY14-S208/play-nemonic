import Image from 'next/image'
import { motion } from 'motion/react'
import Lottie from 'lottie-react'
import type { RefObject, UIEventHandler } from 'react'

import { downArrowAnimation } from '@/shared/assets'
import {
  DROP_SPRING_TRANSITION,
  FLIPBOOK_ENTRANCE_FRAMES,
  FLIPBOOK_SCENE_IMAGES,
  FLIPBOOK_SCROLL_HINT_FRAME_INDEX,
  FLIPBOOK_SCROLL_HINT_LABEL,
} from '../../constants'

interface EntranceSketchbookProps {
  scrollZoneRef: RefObject<HTMLDivElement | null>
  activeFrameIndex: number
  scrollSpacerHeight: string
  onScroll: UIEventHandler<HTMLDivElement>
  isInteractive: boolean
  shouldInstantCompleteIntro: boolean
}

export function EntranceSketchbook({
  scrollZoneRef,
  activeFrameIndex,
  scrollSpacerHeight,
  onScroll,
  isInteractive,
  shouldInstantCompleteIntro,
}: EntranceSketchbookProps) {
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
          {activeFrameIndex === FLIPBOOK_SCROLL_HINT_FRAME_INDEX && <EntranceScrollHint />}
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

function EntranceScrollHint() {
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
