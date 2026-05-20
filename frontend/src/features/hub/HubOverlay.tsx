'use client'

import Image from 'next/image'
import { useRouter } from 'next/navigation'
import {
  Gamepad2,
  HelpCircle,
  Home,
  LayoutDashboard,
  Music2,
  MousePointerClick,
  Printer,
  Volume2,
  VolumeX,
} from 'lucide-react'
import { cn } from '@/shared/libs'
import { useHubOnboardingStore, useHubRoomStore } from '@/shared/stores'
import type { HubFocusKey } from '@/shared/types'
import HubOnboardingTour from './HubOnboardingTour'
import styles from './HubOverlay.module.css'
import MonitorGameInfoCard from './MonitorGameInfoCard'
import { useHubBgm } from './useHubBgm'

const NEMONIC_ROOM_PATH = '/nemonic'

const FOCUS_BUTTONS: Array<{
  focusKey: HubFocusKey
  icon: typeof Home
  label: string
  iconOnly?: boolean
}> = [
  { focusKey: 'overview', icon: Home, label: '홈', iconOnly: true },
  { focusKey: 'monitor', icon: Gamepad2, label: '게임 선택' },
  { focusKey: 'communityBoard', icon: LayoutDashboard, label: '커뮤니티 보드' },
  { focusKey: 'printer', icon: Printer, label: '네모닉' },
]

export default function HubOverlay({
  disableBgm = false,
}: {
  disableBgm?: boolean
}) {
  const router = useRouter()
  const focusKey = useHubRoomStore((state) => state.focusKey)
  const setFocus = useHubRoomStore((state) => state.setFocus)
  const reopenOnboarding = useHubOnboardingStore(
    (state) => state.reopenOnboarding,
  )
  const { isBgmEnabled, isBgmPlaying, toggleHubBgm } = useHubBgm({
    disabled: disableBgm,
  })
  const BgmIcon = isBgmEnabled ? Volume2 : VolumeX
  const bgmToggleLabel = isBgmEnabled ? '허브 음악 끄기' : '허브 음악 켜기'

  const handleFocusButtonClick = (nextFocusKey: HubFocusKey) => {
    setFocus(nextFocusKey)
  }

  const handleOpenNemonic = () => {
    router.push(NEMONIC_ROOM_PATH)
  }

  return (
    <>
      <header className={styles.brandPanel}>
        <div className={styles.brandMark}>
          <Image
            src="/images/play-nemonic-logo.png"
            alt="Play! Nemonic"
            width={1672}
            height={941}
            priority
            draggable={false}
            className={styles.brandLogo}
          />
        </div>
      </header>

      <div className={styles.utilityCluster}>
        <button
          type="button"
          aria-label="허브 사용법 다시 보기"
          className={styles.utilityButton}
          title="허브 사용법 다시 보기"
          onClick={reopenOnboarding}
        >
          <HelpCircle className="h-4 w-4" strokeWidth={2.35} />
        </button>
        {!disableBgm && (
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
        )}
      </div>

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
                onClick={() => handleFocusButtonClick(buttonFocusKey)}
              >
                <Icon className="h-4 w-4" strokeWidth={2.35} />
                {!iconOnly && <span>{label}</span>}
              </button>
            )
          },
        )}
      </nav>

      {focusKey === 'printer' && (
        <div className={styles.printerPrompt} aria-live="polite">
          <button
            type="button"
            className={styles.printerPromptButton}
            onClick={handleOpenNemonic}
          >
            <MousePointerClick className={styles.printerPromptIcon} strokeWidth={2.35} />
            <span>네모닉 체험하기</span>
          </button>
          <span className={styles.printerPromptBeam} aria-hidden />
        </div>
      )}

      <MonitorGameInfoCard />
      <HubOnboardingTour />
    </>
  )
}
