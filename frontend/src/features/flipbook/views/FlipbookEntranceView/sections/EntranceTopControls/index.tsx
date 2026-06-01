import { PhoneLauncherButton } from '@/shared/components'
import { FLIPBOOK_SCENE_IMAGES } from '../../constants'
import { EntranceIconButton } from '../EntranceIconButton'

interface EntranceTopControlsProps {
  isBgmMuted: boolean
  onOpenHowToPlay: () => void
  onToggleBgmMuted: () => void
}

export function EntranceTopControls({
  isBgmMuted,
  onOpenHowToPlay,
  onToggleBgmMuted,
}: EntranceTopControlsProps) {
  return (
    <div className="absolute right-[3.02%] top-[8.15%] z-30 flex items-center gap-[0.63vw]">
      <EntranceIconButton
        imageSrc={FLIPBOOK_SCENE_IMAGES.howToPlayButton}
        imageWidth={63}
        imageHeight={70}
        label="게임 설명"
        onClick={onOpenHowToPlay}
      />
      <EntranceIconButton
        imageSrc={
          isBgmMuted
            ? FLIPBOOK_SCENE_IMAGES.soundMutedButton
            : FLIPBOOK_SCENE_IMAGES.soundOnButton
        }
        imageWidth={67}
        imageHeight={70}
        label={isBgmMuted ? '배경음악 켜기' : '배경음악 음소거'}
        pressed={isBgmMuted}
        onClick={onToggleBgmMuted}
      />
      <PhoneLauncherButton className="size-[clamp(48px,4.6vw,70px)]" />
    </div>
  )
}
