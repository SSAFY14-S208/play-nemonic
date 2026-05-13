'use client'

import {
  AnimatePresence,
  motion,
  useReducedMotion,
  type Variants,
} from 'motion/react'
import { useCallback, useEffect, useState } from 'react'

import { cn } from '@/shared/libs'

import {
  RELAY_FINAL_STAGE_SIZE,
  RELAY_ROUND_ORDER,
  RELAY_ROUND_RULES,
  RELAY_STAGE_SIZE,
  type RelayResultSegment,
  type RelayRoundKey,
} from '../../constants'

import ResultSegmentTags from './ResultSegmentTags'

// 박스 비율 — 최종 합성 이미지(848:1920)와 동일하게 잡아 fade-in 전후 비율 점프 없음.
const BOX_ASPECT_RATIO = `${RELAY_STAGE_SIZE.width} / ${RELAY_FINAL_STAGE_SIZE.height}`

// 각 슬라이스의 합성 좌표 상 top 위치(박스 높이의 %).
function getSliceTopPct(roundKey: RelayRoundKey): number {
  return (
    (RELAY_ROUND_RULES[roundKey].finalOffsetY /
      RELAY_FINAL_STAGE_SIZE.height) *
    100
  )
}

// 슬라이스 높이(박스 높이의 %). 720/1920 = 37.5%.
const SLICE_HEIGHT_PCT =
  (RELAY_STAGE_SIZE.height / RELAY_FINAL_STAGE_SIZE.height) * 100

// 각 슬라이스 안착 시 기울기(°). 메모지가 손으로 비스듬히 붙은 듯한 느낌.
const TILT_BY_ROUND: Record<RelayRoundKey, number> = {
  face: 10,
  body: -15,
  legs: 5,
}

// 메모지 variants — custom prop으로 안착 시 기울기(°)를 받아 적용.
// 'hidden': 위에서 살짝 큰 채로 대기. rotate를 미리 tilt 값으로 두어, 등장 시
//   회전 애니메이션 없이 scale/y/opacity만 spring으로 안착.
// 'tilted': 안착 상태. rotate = 각자의 기울기.
// 'straight': 모든 슬라이스가 동시에 0°로 정렬되는 상태.
const memoVariants: Variants = {
  hidden: (tiltDeg: number) => ({
    opacity: 0,
    scale: 1.18,
    y: -12,
    rotate: tiltDeg,
  }),
  tilted: (tiltDeg: number) => ({
    opacity: 1,
    scale: 1,
    y: 0,
    rotate: tiltDeg,
    transition: {
      type: 'spring',
      stiffness: 320,
      damping: 16,
      mass: 0.9,
      opacity: { duration: 0.18 },
    },
  }),
  straight: {
    opacity: 1,
    scale: 1,
    y: 0,
    rotate: 0,
    transition: {
      type: 'spring',
      stiffness: 220,
      damping: 24,
      mass: 0.8,
    },
  },
}

// 부모 stack — 자식들을 stagger로 순차 reveal.
// 'tilted' 진입 시: face → body → legs 순으로 0.45s 간격 stagger.
// 'straight' 진입 시: stagger 없이 동시에 회전 정렬.
const stackVariants: Variants = {
  hidden: {},
  tilted: {
    transition: {
      delayChildren: 0.15,
      staggerChildren: 0.45,
    },
  },
  straight: {
    transition: {},
  },
}

interface MemoSliceProps {
  roundKey: RelayRoundKey
  resultImageUrl: string
  tiltDeg: number
  onTilted: () => void
  onStraightened: () => void
}

// 한 라운드의 슬라이스(메모지). 합성 좌표상 finalOffsetY 위치에 절대 배치되어,
// 3장이 모두 안착하면 시각적으로 최종 합성 이미지와 동일한 픽셀 배치가 된다.
function MemoSlice({
  roundKey,
  resultImageUrl,
  tiltDeg,
  onTilted,
  onStraightened,
}: MemoSliceProps) {
  return (
    <motion.div
      custom={tiltDeg}
      variants={memoVariants}
      className="absolute inset-x-[2%]"
      style={{
        top: `${getSliceTopPct(roundKey)}%`,
        height: `${SLICE_HEIGHT_PCT}%`,
      }}
      onAnimationComplete={(definition) => {
        if (definition === 'tilted') onTilted()
        else if (definition === 'straight') onStraightened()
      }}
    >
      <div className="h-full w-full overflow-hidden rounded-md bg-relay-paper shadow-[0_6px_14px_rgba(184,121,22,0.18)]">
        <svg
          className="block h-full w-full"
          viewBox={`0 0 ${RELAY_STAGE_SIZE.width} ${RELAY_STAGE_SIZE.height}`}
          preserveAspectRatio="xMidYMid meet"
          role="img"
          aria-label={`${RELAY_ROUND_RULES[roundKey].label} 슬라이스`}
        >
          <image
            href={resultImageUrl}
            x={0}
            y={-RELAY_ROUND_RULES[roundKey].finalOffsetY}
            width={RELAY_STAGE_SIZE.width}
            height={RELAY_FINAL_STAGE_SIZE.height}
          />
        </svg>
      </div>
    </motion.div>
  )
}

// 자동 인터렉션 phase.
//   tilting:       메모지 3장이 face → body → legs 순으로 기울어진 채 stagger 안착.
//   straightening: 3장이 동시에 0°로 회전 정렬.
//   overlay:       최종 합성 이미지가 opacity 페이드인으로 덮어씌움.
//   final:         segment 태그 페이드인 + 메모지/스킵 unmount.
type Phase = 'tilting' | 'straightening' | 'overlay' | 'final'

// 모든 슬라이스 tilt 안착 후 straightening으로 전환되기까지 머무는 시간(ms).
const TILT_HOLD_MS = 400
// 모든 슬라이스 straight 정렬 후 overlay phase로 전환되기까지 머무는 시간(ms).
const STRAIGHTEN_HOLD_MS = 250

interface ResultRevealAnimationProps {
  resultImageUrl: string
  segments: RelayResultSegment[]
  // 캔버스(앨범) 전환 시 부모가 다른 값으로 갱신 → 시퀀스 처음부터 재생.
  replayKey: number | string
  className?: string
}

export default function ResultRevealAnimation({
  resultImageUrl,
  segments,
  replayKey,
  className,
}: ResultRevealAnimationProps) {
  const prefersReducedMotion = useReducedMotion()
  const [skipped, setSkipped] = useState(false)
  const [phase, setPhase] = useState<Phase>('tilting')
  // 각 슬라이스 tilt 안착/straighten 완료마다 increment.
  const [tiltedCount, setTiltedCount] = useState(0)
  const [straightenedCount, setStraightenedCount] = useState(0)

  // replayKey 변경 시 시퀀스 리셋. setState 동기 호출 금지 → rAF로.
  useEffect(() => {
    const raf = requestAnimationFrame(() => {
      setSkipped(false)
      setPhase('tilting')
      setTiltedCount(0)
      setStraightenedCount(0)
    })
    return () => cancelAnimationFrame(raf)
  }, [replayKey])

  // prefers-reduced-motion → 즉시 최종 상태.
  useEffect(() => {
    if (!prefersReducedMotion) return
    const raf = requestAnimationFrame(() => setSkipped(true))
    return () => cancelAnimationFrame(raf)
  }, [prefersReducedMotion])

  // 모든 슬라이스 tilt 안착 → 잠시 머무름 후 straightening으로.
  useEffect(() => {
    if (skipped) return
    if (phase !== 'tilting') return
    if (tiltedCount < RELAY_ROUND_ORDER.length) return
    const timer = window.setTimeout(() => {
      setPhase('straightening')
    }, TILT_HOLD_MS)
    return () => window.clearTimeout(timer)
  }, [phase, skipped, tiltedCount])

  // 모든 슬라이스 straighten 완료 → 잠시 머무름 후 overlay phase로.
  useEffect(() => {
    if (skipped) return
    if (phase !== 'straightening') return
    if (straightenedCount < RELAY_ROUND_ORDER.length) return
    const timer = window.setTimeout(() => {
      setPhase('overlay')
    }, STRAIGHTEN_HOLD_MS)
    return () => window.clearTimeout(timer)
  }, [phase, skipped, straightenedCount])

  const handleTiltLanded = useCallback(() => {
    setTiltedCount((current) => current + 1)
  }, [])

  const handleStraightened = useCallback(() => {
    setStraightenedCount((current) => current + 1)
  }, [])

  const handleSkip = useCallback(() => {
    setSkipped(true)
  }, [])

  const isOverlayActive =
    skipped || phase === 'overlay' || phase === 'final'
  // stack의 부모 variant target. 'tilting'이면 'tilted', 그 외 단계는 'straight'.
  const stackTarget: 'tilted' | 'straight' =
    phase === 'tilting' ? 'tilted' : 'straight'
  const showMemos = !skipped && phase !== 'final'
  const showSegmentTags = skipped || phase === 'final'
  const showSkipButton = !skipped && phase !== 'final'

  return (
    <div
      className={cn(
        'relative overflow-hidden rounded-[14px] border-[1.5px] border-relay-line bg-relay-background',
        className,
      )}
      style={{ aspectRatio: BOX_ASPECT_RATIO }}
    >
      {/* ── Layer 1 — 메모지 스택 (face → body → legs 순차 tilt → 동시 straight) ── */}
      {showMemos && (
        <motion.div
          key={`stack-${replayKey}`}
          className="absolute inset-0"
          initial="hidden"
          animate={stackTarget}
          variants={stackVariants}
        >
          {RELAY_ROUND_ORDER.map((roundKey) => (
            <MemoSlice
              key={roundKey}
              roundKey={roundKey}
              resultImageUrl={resultImageUrl}
              tiltDeg={TILT_BY_ROUND[roundKey]}
              onTilted={handleTiltLanded}
              onStraightened={handleStraightened}
            />
          ))}
        </motion.div>
      )}

      {/* ── Layer 2 — 최종 합성 이미지 opacity 페이드인 ───────────────────── */}
      <motion.div
        key={`final-${replayKey}`}
        className="absolute inset-0"
        initial={{ opacity: 0 }}
        animate={{ opacity: isOverlayActive ? 1 : 0 }}
        transition={
          skipped
            ? { duration: 0 }
            : { duration: 0.55, delay: 0.1, ease: 'easeOut' }
        }
        onAnimationComplete={() => {
          if (isOverlayActive && phase !== 'final') setPhase('final')
        }}
      >
        <svg
          className="h-full w-full"
          viewBox={`0 0 ${RELAY_STAGE_SIZE.width} ${RELAY_FINAL_STAGE_SIZE.height}`}
          preserveAspectRatio="xMidYMid meet"
          role="img"
          aria-label="완성된 릴레이 드로잉"
        >
          <image
            href={resultImageUrl}
            x={0}
            y={0}
            width={RELAY_STAGE_SIZE.width}
            height={RELAY_FINAL_STAGE_SIZE.height}
          />
        </svg>
      </motion.div>

      {/* ── Layer 3 — segment 태그 (final phase에서 페이드인) ──────────── */}
      <AnimatePresence>
        {showSegmentTags && segments.length > 0 && (
          <motion.div
            key="segment-tags"
            className="pointer-events-none absolute inset-0"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={
              skipped ? { duration: 0 } : { duration: 0.4, ease: 'easeOut' }
            }
          >
            <ResultSegmentTags segments={segments} />
          </motion.div>
        )}
      </AnimatePresence>

      {/* ── Layer 4 — 스킵 버튼 (시퀀스 진행 중에만 노출) ───────────────── */}
      <AnimatePresence>
        {showSkipButton && (
          <motion.button
            key="skip"
            type="button"
            onClick={handleSkip}
            className="caption-b absolute right-3 top-3 cursor-pointer rounded-full border border-relay-line bg-relay-paper/90 px-3 py-1.5 text-relay-accent-strong shadow-sm backdrop-blur-sm transition-all hover:-translate-y-0.5 hover:brightness-95"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.2 }}
          >
            건너뛰기
          </motion.button>
        )}
      </AnimatePresence>
    </div>
  )
}
