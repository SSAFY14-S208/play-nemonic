import type { RefObject, UIEventHandler } from 'react'

import type { FlipbookEntranceActionKey } from '../../constants'
import { EntranceActions } from '../EntranceActions'
import { EntranceDropScene } from '../EntranceDropScene'
import { EntranceSketchbook } from '../EntranceSketchbook'
import { EntranceTopControls } from '../EntranceTopControls'

interface EntranceDesktopSceneProps {
  visible: boolean
  interactive: boolean
  shouldInstantCompleteIntro: boolean
  isBusy: boolean
  isBgmMuted: boolean
  errorMessage: string | null
  activeFrameIndex: number
  scrollSpacerHeight: string
  scrollZoneRef: RefObject<HTMLDivElement | null>
  actionHandlers: Record<FlipbookEntranceActionKey, () => void>
  onScroll: UIEventHandler<HTMLDivElement>
  onOpenHowToPlay: () => void
  onToggleBgmMuted: () => void
}

export function EntranceDesktopScene({
  visible,
  interactive,
  shouldInstantCompleteIntro,
  isBusy,
  isBgmMuted,
  errorMessage,
  activeFrameIndex,
  scrollSpacerHeight,
  scrollZoneRef,
  actionHandlers,
  onScroll,
  onOpenHowToPlay,
  onToggleBgmMuted,
}: EntranceDesktopSceneProps) {
  return (
    <div className="absolute inset-0 z-10 hidden overflow-hidden sm:block">
      <EntranceDropScene shouldInstantCompleteIntro={shouldInstantCompleteIntro} />

      <EntranceSketchbook
        scrollZoneRef={scrollZoneRef}
        activeFrameIndex={activeFrameIndex}
        scrollSpacerHeight={scrollSpacerHeight}
        onScroll={onScroll}
        isInteractive={interactive}
        shouldInstantCompleteIntro={shouldInstantCompleteIntro}
      />

      <EntranceActions
        visible={visible}
        interactive={interactive}
        shouldInstantCompleteIntro={shouldInstantCompleteIntro}
        isBusy={isBusy}
        errorMessage={errorMessage}
        actionHandlers={actionHandlers}
      />

      <EntranceTopControls
        isBgmMuted={isBgmMuted}
        onOpenHowToPlay={onOpenHowToPlay}
        onToggleBgmMuted={onToggleBgmMuted}
      />
    </div>
  )
}
