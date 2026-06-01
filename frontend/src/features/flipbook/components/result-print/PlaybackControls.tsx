import Image from 'next/image'

import {
  ARTIST_BADGE_IMAGE_SRC,
  SKIP_BUTTON_IMAGE_SRC,
  resultPrintStageStyles as styles,
} from './constants'
import type { FlipbookPrintFrame, FlipbookPrintParticipant } from './types'

interface SkipPlaybackButtonProps {
  disabled: boolean
  onClick: () => void
}

interface FrameArtistBadgeProps {
  frame: FlipbookPrintFrame
  participant: FlipbookPrintParticipant
}

export function SkipPlaybackButton({
  disabled,
  onClick,
}: SkipPlaybackButtonProps) {
  return (
    <button
      type="button"
      disabled={disabled}
      className={styles.skipPlaybackButton}
      aria-label="현재 플립북 GIF 화면으로 건너뛰기"
      title="skip"
      onClick={onClick}
    >
      <Image
        src={SKIP_BUTTON_IMAGE_SRC}
        alt=""
        fill
        sizes="86px"
        unoptimized
        draggable={false}
        className={styles.skipPlaybackButtonImage}
        aria-hidden
      />
      <span className={styles.skipPlaybackButtonText}>
        skip
      </span>
    </button>
  )
}

export function FrameArtistBadge({
  frame,
  participant,
}: FrameArtistBadgeProps) {
  const isGifPlaybackFrame = frame.outputMode === 'gif-playback'
  const artistName = isGifPlaybackFrame
    ? participant.name
    : frame.drawnByName?.trim() || participant.name
  const labelText = isGifPlaybackFrame ? '완성본' : '그린 사람'

  return (
    <div className={styles.frameArtistBadge} aria-live="polite">
      <Image
        src={ARTIST_BADGE_IMAGE_SRC}
        alt=""
        fill
        sizes="150px"
        unoptimized
        draggable={false}
        className={styles.frameArtistBadgeImage}
        aria-hidden
      />
      <span className={styles.frameArtistBadgeLabel}>{labelText}</span>
      <span className={styles.frameArtistBadgeName}>{artistName}</span>
    </div>
  )
}
