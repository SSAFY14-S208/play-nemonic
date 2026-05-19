'use client'

import { AnimatePresence, motion } from 'motion/react'
import type { CSSProperties } from 'react'
import { cn } from '@/shared/libs'
import { HUB_GAMES } from '@/shared/constants'
import {
  useHubGameStore,
  useHubOnboardingStore,
  useHubRoomStore,
} from '@/shared/stores'
import styles from './MonitorGameInfoCard.module.css'

export default function MonitorGameInfoCard() {
  const focusKey = useHubRoomStore((state) => state.focusKey)
  const selectedGameIndex = useHubGameStore((state) => state.selectedGameIndex)
  const selectGame = useHubGameStore((state) => state.selectGame)
  // 온보딩이 떠 있을 때는 안내가 중복되지 않도록 모니터 정보 카드를 숨긴다.
  const isOnboardingActive = useHubOnboardingStore(
    (state) => state.hasEnteredHub && !state.hasSeenOnboarding,
  )
  const selectedGame = HUB_GAMES[selectedGameIndex] ?? HUB_GAMES[0]
  const isVisible = focusKey === 'monitor'

  return (
    <AnimatePresence>
      {isVisible && (
        <motion.aside
          key="monitor-game-info-card"
          className={styles.root}
          data-onboarding-active={isOnboardingActive ? 'true' : 'false'}
          style={
            {
              '--monitor-info-accent': selectedGame.accentColor,
              '--monitor-info-lighting': selectedGame.lightingColor,
            } as CSSProperties
          }
          // ⚠️ 가운데 정렬을 CSS의 transform: translateX(-50%)로 처리하므로
          // framer-motion이 inline transform을 덮어쓰지 않게 opacity만 사용.
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.24, ease: [0.16, 1, 0.3, 1] }}
          aria-live="polite"
          aria-label="모니터에서 선택한 게임 정보"
        >
          <header className={styles.header}>
            <span
              className={styles.progressDots}
              aria-label={`${HUB_GAMES.length}개 중 ${selectedGameIndex + 1}번째 게임`}
            >
              {HUB_GAMES.map((game, gameIndex) => (
                <button
                  key={game.id}
                  type="button"
                  className={cn(
                    styles.progressDot,
                    gameIndex === selectedGameIndex && styles.progressDotActive,
                  )}
                  style={
                    {
                      '--progress-dot-color': game.lightingColor,
                    } as CSSProperties
                  }
                  onClick={() => selectGame(gameIndex)}
                  aria-label={`${game.title}로 이동`}
                  aria-current={
                    gameIndex === selectedGameIndex ? 'true' : undefined
                  }
                />
              ))}
            </span>
          </header>

          <div className={styles.layout}>
            <AnimatePresence mode="wait">
              <motion.div
                key={selectedGame.id}
                className={styles.body}
                initial={{ opacity: 0, y: 4 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -3 }}
                transition={{ duration: 0.22, ease: 'easeOut' }}
              >
                <p className={styles.tagline}>{selectedGame.tagline}</p>
                <h2 className={styles.title}>{selectedGame.title}</h2>
                <p className={styles.detail}>{selectedGame.detail}</p>
              </motion.div>
            </AnimatePresence>
          </div>
        </motion.aside>
      )}
    </AnimatePresence>
  )
}
