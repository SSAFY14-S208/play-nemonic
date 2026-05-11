'use client'

import { useMemo, type CSSProperties } from 'react'
import { useRouter } from 'next/navigation'
import { motion } from 'motion/react'
import { ArrowRight, BookOpen, Film, Sparkles, Users } from 'lucide-react'
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
const HUB_COMMUNITY_CANVAS_PATH = '/community-canvas'
const HUB_FLIPBOOK_PATH = '/flipbook'
const HUB_RELAY_DRAWING_PATH = '/relay-drawing'

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

interface EntryConfig {
  label: string
  Icon: typeof Sparkles
  /** 입장 버튼 베이스 그라디언트(콘텐츠 색상에 맞춰 따뜻하게 살림) */
  gradient: string
  /** 텍스트 + 아이콘 색 (충분한 대비로) */
  text: string
  /** 호버 시 외곽 글로우 색 */
  glow: string
}

const ENTRY_CONFIGS: Partial<Record<HubContentKey, EntryConfig>> = {
  community: {
    label: '커뮤니티 게시판 입장',
    Icon: BookOpen,
    gradient: 'linear-gradient(135deg, #d6f5be 0%, #a3e186 100%)',
    text: '#3a6b2f',
    glow: 'rgba(125, 198, 90, 0.55)',
  },
  fortune: {
    label: '운세 부스 입장',
    Icon: Sparkles,
    gradient: 'linear-gradient(135deg, #f4e6ff 0%, #c8a8ee 100%)',
    text: '#5a3f8a',
    glow: 'rgba(168, 122, 224, 0.55)',
  },
  flipbook: {
    label: '플립북 입장',
    Icon: Film,
    gradient: 'linear-gradient(135deg, #fff4cc 0%, #ffd87a 100%)',
    text: '#7a5a1f',
    glow: 'rgba(238, 188, 84, 0.55)',
  },
  relay: {
    label: '릴레이 드로잉 입장',
    Icon: Users,
    gradient: 'linear-gradient(135deg, #ffe1e4 0%, #ff9da8 100%)',
    text: '#8a3a44',
    glow: 'rgba(228, 116, 130, 0.55)',
  },
}

const ENTRY_ROUTES: Partial<Record<HubContentKey, string>> = {
  community: HUB_COMMUNITY_CANVAS_PATH,
  fortune: HUB_FORTUNE_PATH,
  flipbook: HUB_FLIPBOOK_PATH,
  relay: HUB_RELAY_DRAWING_PATH,
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

  const entryConfig = selectedContentKey ? ENTRY_CONFIGS[selectedContentKey] : undefined
  const entryRoute = selectedContentKey ? ENTRY_ROUTES[selectedContentKey] : undefined

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
          {entryConfig && entryRoute && (
            <motion.button
              key={selectedContentKey}
              type="button"
              className={styles.entryButton}
              onClick={() => router.push(entryRoute)}
              style={
                {
                  '--hub-entry-gradient': entryConfig.gradient,
                  '--hub-entry-text': entryConfig.text,
                  '--hub-entry-glow': entryConfig.glow,
                } as CSSProperties
              }
              initial={{ opacity: 0, y: 8, scale: 0.96 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              transition={{ type: 'spring', stiffness: 320, damping: 22 }}
              whileHover={{ y: -3, scale: 1.04 }}
              whileTap={{ scale: 0.97 }}
            >
              <entryConfig.Icon
                className={styles.entryIcon}
                aria-hidden
                strokeWidth={2.4}
              />
              <span className={styles.entryLabel}>{entryConfig.label}</span>
              <ArrowRight
                className={styles.entryArrow}
                aria-hidden
                strokeWidth={2.6}
              />
            </motion.button>
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
