'use client'

import { useMemo, type CSSProperties } from 'react'
import { useRouter } from 'next/navigation'
import {
  HUB_CONTENT_VIEWS,
  type HubContentKey,
  useHubViewStore,
} from '@/shared/stores'
import { cn } from '@/shared/libs'
import styles from './HubOverlay.module.css'

const HUB_BUTTONS: Array<{ key: HubContentKey; label: string }> = [
  { key: 'community', label: '커뮤니티' },
  { key: 'fortune', label: '운세' },
  { key: 'relay', label: '릴레이' },
  { key: 'infinite', label: '무한' },
  { key: 'flipbook', label: '플립북' },
]

const HUB_FORTUNE_PATH = '/fortune'

const PLATFORM_BUTTON_STYLES: Record<HubContentKey, CSSProperties> = {
  community: {
    '--hub-chip-bg': 'rgba(183, 235, 163, 0.72)',
    '--hub-chip-border': 'rgba(111, 168, 87, 0.34)',
    '--hub-chip-text': '#4d7a42',
  } as CSSProperties,
  fortune: {
    '--hub-chip-bg': 'rgba(204, 173, 238, 0.72)',
    '--hub-chip-border': 'rgba(128, 99, 178, 0.34)',
    '--hub-chip-text': '#6c5596',
  } as CSSProperties,
  relay: {
    '--hub-chip-bg': 'rgba(255, 157, 168, 0.74)',
    '--hub-chip-border': 'rgba(204, 96, 108, 0.34)',
    '--hub-chip-text': '#98525b',
  } as CSSProperties,
  infinite: {
    '--hub-chip-bg': 'rgba(185, 224, 246, 0.78)',
    '--hub-chip-border': 'rgba(93, 154, 190, 0.34)',
    '--hub-chip-text': '#527c94',
  } as CSSProperties,
  flipbook: {
    '--hub-chip-bg': 'rgba(255, 225, 143, 0.76)',
    '--hub-chip-border': 'rgba(204, 154, 53, 0.34)',
    '--hub-chip-text': '#8a6b31',
  } as CSSProperties,
}

export default function HubOverlay() {
  const router = useRouter()
  const selectedContentKey = useHubViewStore((state) => state.selectedContentKey)
  const currentCopy = useHubViewStore((state) => state.currentCopy)
  const selectContent = useHubViewStore((state) => state.selectContent)
  const copyKey = `${currentCopy.eyebrow}-${currentCopy.title}-${currentCopy.description}`

  const renderedButtons = useMemo(
    () =>
      HUB_BUTTONS.map(({ key, label }) => {
        const platform = HUB_CONTENT_VIEWS[key].platform
        const isActive = selectedContentKey === key

        return (
          <button
            key={key}
            type="button"
            aria-pressed={isActive}
            data-platform={platform}
            style={PLATFORM_BUTTON_STYLES[key]}
            onClick={() => selectContent(key)}
            className={cn(styles.platformButton, isActive && styles.active)}
          >
            {label}
          </button>
        )
      }),
    [selectContent, selectedContentKey],
  )

  const handleEnterFortune = () => {
    router.push(HUB_FORTUNE_PATH)
  }

  return (
    <>
      <header className={styles.viewerCopy}>
        <div
          key={copyKey}
          className={styles.copySwap}
        >
          <p className={styles.eyebrow}>
            {currentCopy.eyebrow}
          </p>
          <h1 className={styles.viewerTitle}>
            {currentCopy.title}
          </h1>
          <p className={styles.viewerDescription}>
            {currentCopy.description}
          </p>
          {selectedContentKey === 'fortune' && (
            <button
              type="button"
              className={styles.entryButton}
              onClick={handleEnterFortune}
            >
              운세 부스 입장
            </button>
          )}
        </div>
      </header>

      <footer className={styles.viewerControls}>
        <div className={styles.platformButtons}>
          {renderedButtons}
        </div>
      </footer>
    </>
  )
}
