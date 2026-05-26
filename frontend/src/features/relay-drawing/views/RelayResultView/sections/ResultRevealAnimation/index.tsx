'use client'

import { cn } from '@/shared/libs'

import type { RelayResultSegment } from '../../../../constants'
import { BOX_ASPECT_RATIO } from './constants'
import FinalResultImage from './FinalResultImage'
import { useResultRevealAnimation } from './hooks'
import MemoStack from './MemoStack'
import SegmentTagsOverlay from './SegmentTagsOverlay'
import SkipRevealButton from './SkipRevealButton'

interface ResultRevealAnimationProps {
  resultImageUrl: string
  segments: RelayResultSegment[]
  replayKey: number | string
  className?: string
}

export default function ResultRevealAnimation({
  resultImageUrl,
  segments,
  replayKey,
  className,
}: ResultRevealAnimationProps) {
  const {
    skipped,
    phase,
    setPhase,
    handleTiltLanded,
    handleStraightened,
    handleSkip,
  } = useResultRevealAnimation(replayKey)

  const isOverlayActive = skipped || phase === 'overlay' || phase === 'final'
  const stackTarget = phase === 'tilting' ? 'tilted' : 'straight'
  const showMemos = !skipped && phase !== 'final'
  const showSegmentTags = skipped || phase === 'final'
  const showSkipButton = !skipped && phase !== 'final'

  return (
    <>
      <div
        className={cn('relative overflow-hidden', className)}
        style={{ aspectRatio: BOX_ASPECT_RATIO }}
      >
        {showMemos && (
          <MemoStack
            replayKey={replayKey}
            resultImageUrl={resultImageUrl}
            stackTarget={stackTarget}
            onTilted={handleTiltLanded}
            onStraightened={handleStraightened}
          />
        )}
        <FinalResultImage
          replayKey={replayKey}
          resultImageUrl={resultImageUrl}
          skipped={skipped}
          isOverlayActive={isOverlayActive}
          phase={phase}
          onFinal={() => setPhase('final')}
        />
        <SegmentTagsOverlay
          segments={segments}
          isVisible={showSegmentTags}
          skipped={skipped}
        />
      </div>
      <SkipRevealButton isVisible={showSkipButton} onClick={handleSkip} />
    </>
  )
}
