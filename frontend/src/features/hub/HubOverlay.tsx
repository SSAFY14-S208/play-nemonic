'use client'

import {
  LayoutPanelLeft,
  Monitor,
  ScanEye,
  Sparkles,
  StickyNote,
} from 'lucide-react'
import { cn } from '@/shared/libs'
import { useHubRoomStore } from '@/shared/stores'
import type { HubFocusKey } from '@/shared/types'
import styles from './HubOverlay.module.css'

const FOCUS_BUTTONS: Array<{
  focusKey: HubFocusKey
  icon: typeof ScanEye
  label: string
}> = [
  { focusKey: 'overview', icon: ScanEye, label: '전체 보기' },
  { focusKey: 'mainDesk', icon: LayoutPanelLeft, label: '메인 데스크' },
  { focusKey: 'monitor', icon: Monitor, label: '모니터' },
  { focusKey: 'workspace', icon: StickyNote, label: '작업 공간' },
  { focusKey: 'pegboard', icon: LayoutPanelLeft, label: '타공판' },
]

export default function HubOverlay() {
  const focusKey = useHubRoomStore((state) => state.focusKey)
  const setFocus = useHubRoomStore((state) => state.setFocus)

  return (
    <>
      <header className={styles.brandPanel}>
        <div className={styles.brandMark}>
          <Sparkles className="h-4 w-4" strokeWidth={2.3} />
          NEMONIC
        </div>
      </header>

      <nav className={styles.focusControls} aria-label="허브 카메라 포커스">
        {FOCUS_BUTTONS.map(({ focusKey: buttonFocusKey, icon: Icon, label }) => {
          const isActive = focusKey === buttonFocusKey

          return (
            <button
              key={buttonFocusKey}
              type="button"
              aria-pressed={isActive}
              className={cn(styles.focusButton, isActive && styles.focusButtonActive)}
              onClick={() => setFocus(buttonFocusKey)}
            >
              <Icon className="h-4 w-4" strokeWidth={2.35} />
              <span>{label}</span>
            </button>
          )
        })}
      </nav>
    </>
  )
}
