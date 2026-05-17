'use client'

import {
  Gamepad2,
  Home,
  LayoutDashboard,
  Music2,
  Sparkles,
  Volume2,
  VolumeX,
} from 'lucide-react'
import { cn } from '@/shared/libs'
import { useHubRoomStore } from '@/shared/stores'
import type { HubFocusKey } from '@/shared/types'
import styles from './HubOverlay.module.css'
import { useHubBgm } from './useHubBgm'

const FOCUS_BUTTONS: Array<{
  focusKey: HubFocusKey
  icon: typeof Home
  label: string
  iconOnly?: boolean
}> = [
  { focusKey: 'overview', icon: Home, label: '홈', iconOnly: true },
  { focusKey: 'monitor', icon: Gamepad2, label: '게임 선택' },
  { focusKey: 'communityBoard', icon: LayoutDashboard, label: '커뮤니티 보드' },
]

export default function HubOverlay() {
  const focusKey = useHubRoomStore((state) => state.focusKey)
  const setFocus = useHubRoomStore((state) => state.setFocus)
  const { isBgmEnabled, isBgmPlaying, toggleHubBgm } = useHubBgm()
  const BgmIcon = isBgmEnabled ? Volume2 : VolumeX
  const bgmToggleLabel = isBgmEnabled ? '허브 음악 끄기' : '허브 음악 켜기'

  return (
    <>
      <header className={styles.brandPanel}>
        <div className={styles.brandMark}>
          <Sparkles className="h-4 w-4" strokeWidth={2.3} />
          NEMONIC
        </div>
      </header>

      <button
        type="button"
        aria-label={bgmToggleLabel}
        aria-pressed={isBgmEnabled}
        className={styles.musicButton}
        data-muted={!isBgmEnabled}
        data-playing={isBgmPlaying}
        title={`Pastel Puzzle Room · ${bgmToggleLabel}`}
        onClick={toggleHubBgm}
      >
        <Music2 className={styles.musicSignal} strokeWidth={2.35} />
        <BgmIcon className="h-4 w-4" strokeWidth={2.35} />
      </button>

      <nav className={styles.focusControls} aria-label="허브 카메라 포커스">
        {FOCUS_BUTTONS.map(
          ({ focusKey: buttonFocusKey, icon: Icon, label, iconOnly }) => {
            const isActive = focusKey === buttonFocusKey

            return (
              <button
                key={buttonFocusKey}
                type="button"
                aria-pressed={isActive}
                aria-label={label}
                className={cn(styles.focusButton, isActive && styles.focusButtonActive)}
                onClick={() => setFocus(buttonFocusKey)}
              >
                <Icon className="h-4 w-4" strokeWidth={2.35} />
                {!iconOnly && <span>{label}</span>}
              </button>
            )
          },
        )}
      </nav>
    </>
  )
}
