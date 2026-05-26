'use client'

import { AnimatePresence, motion } from 'motion/react'
import { cn } from '@/shared/libs'
import { HUB_GAMES } from '@/shared/constants'
import {
  useHubGameStore,
  useHubMonitorTransitionStore,
  useHubOnboardingStore,
  useHubRoomStore,
} from '@/shared/stores'

function toRgba(hex: string, alpha: number): string {
  const r = parseInt(hex.slice(1, 3), 16)
  const g = parseInt(hex.slice(3, 5), 16)
  const b = parseInt(hex.slice(5, 7), 16)
  return `rgba(${r},${g},${b},${alpha})`
}

const KEYCAP =
  'inline-flex items-center justify-center h-[1.55rem] min-w-[1.55rem] px-[0.32rem] rounded-[0.38rem] border border-b-2 border-[#d8d0ec] bg-gradient-to-b from-[rgb(255_255_255/76%)] to-[rgb(255_255_255/42%)] shadow-[inset_0_1px_0_rgb(255_255_255/68%),0_0.18rem_0.46rem_rgb(54_45_80/9%)] font-[inherit] text-[0.72rem] font-black leading-none text-[#51466f]'

export default function MonitorGameInfoCard() {
  const focusKey = useHubRoomStore((state) => state.focusKey)
  const selectedGameIndex = useHubGameStore((state) => state.selectedGameIndex)
  const pendingGameIndex = useHubMonitorTransitionStore(
    (state) => state.currentRequest?.gameIndex,
  )
  const requestGameTransition = useHubMonitorTransitionStore(
    (state) => state.requestGameTransition,
  )
  const isOnboardingActive = useHubOnboardingStore(
    (state) => state.hasEnteredHub && !state.hasSeenOnboarding,
  )
  const selectedGame = HUB_GAMES[selectedGameIndex] ?? HUB_GAMES[0]
  const isVisible = focusKey === 'monitor' && !isOnboardingActive

  return (
    <AnimatePresence>
      {isVisible && (
        <motion.aside
          key="monitor-game-info-card"
          className={cn(
            'fixed left-1/2 -translate-x-1/2 z-[25] pointer-events-auto',
            'flex flex-col gap-[0.42rem] overflow-hidden isolate',
            'w-[min(34rem,calc(100vw-2rem))] top-14',
            'rounded-[0.9rem] border border-[#e5def0]',
            'bg-gradient-to-b from-[rgb(255_255_255/72%)] to-[rgb(255_255_255/58%)]',
            'px-[0.95rem] pt-[0.72rem] pb-[0.78rem] text-[#2c2447]',
            'shadow-[0_0.9rem_2rem_rgb(54_45_80/14%),0_0.25rem_0.8rem_rgb(54_45_80/7%),inset_0_1px_0_rgb(255_255_255/54%),inset_0_0_0_1px_rgb(255_255_255/16%)]',
            'backdrop-blur-[18px] backdrop-saturate-110',
            'transition-[background] duration-[360ms] ease-out motion-reduce:transition-none',
            'sm:w-[min(24rem,calc(100vw-1.6rem))] sm:px-[0.85rem]',
            '[@media(max-height:560px)]:top-12',
            '[@media(max-height:560px)]:w-[min(31rem,calc(100vw-1.4rem))]',
            '[@media(max-height:560px)]:px-[0.78rem] [@media(max-height:560px)]:pt-[0.62rem] [@media(max-height:560px)]:pb-[0.66rem] [@media(max-height:560px)]:gap-[0.34rem]',
          )}
          // ⚠️ centering via CSS translateX(-50%) — framer-motion animates opacity only
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.24, ease: [0.16, 1, 0.3, 1] }}
          aria-live="polite"
          aria-label="모니터에서 선택한 게임 정보"
        >
          <header className="absolute top-[0.58rem] right-[0.7rem] flex items-center justify-end [@media(max-height:560px)]:top-[0.48rem] [@media(max-height:560px)]:right-[0.58rem]">
            <span
              className="inline-flex items-center gap-1 rounded-[0.45rem] px-[0.26rem] py-[0.2rem] bg-[rgb(255_255_255/30%)] shadow-[inset_0_0_0_1px_rgb(102_85_126/10%)]"
              aria-label={`${HUB_GAMES.length}개 중 ${selectedGameIndex + 1}번째 게임`}
            >
              {HUB_GAMES.map((game, gameIndex) => {
                const isActive = gameIndex === selectedGameIndex
                const isPending = gameIndex === pendingGameIndex && !isActive
                return (
                  <button
                    key={game.id}
                    type="button"
                    className={cn(
                      'relative h-[0.46rem] cursor-pointer rounded-[0.24rem] border-0 bg-transparent p-0',
                      'transition-[width,background-color,box-shadow,transform] duration-[240ms] ease-out',
                      'hover:scale-[1.18] motion-reduce:transition-none',
                      "after:absolute after:content-[''] after:inset-[-0.6rem]",
                      isActive ? 'w-[1.08rem]' : 'w-[0.46rem]',
                      isPending && 'scale-[1.08]',
                    )}
                    style={
                      isActive
                        ? {
                            backgroundColor: game.lightingColor,
                            boxShadow: `0 0 0 2px ${toRgba(game.lightingColor, 0.16)}, 0 2px 6px ${toRgba(game.lightingColor, 0.28)}`,
                          }
                        : isPending
                          ? {
                              backgroundColor: toRgba(game.lightingColor, 0.72),
                              boxShadow: `0 0 0 2px ${toRgba(game.lightingColor, 0.14)}, 0 0 0.7rem ${toRgba(game.lightingColor, 0.34)}`,
                            }
                          : {
                              backgroundColor: toRgba(game.lightingColor, 0.28),
                              boxShadow: `inset 0 0 0 1px ${toRgba(game.lightingColor, 0.38)}`,
                            }
                    }
                    onClick={() => requestGameTransition(gameIndex)}
                    aria-label={`${game.title}로 이동`}
                    aria-current={isActive ? 'true' : undefined}
                  />
                )
              })}
            </span>
          </header>

          <div>
            <AnimatePresence mode="wait">
              <motion.div
                key={selectedGame.id}
                className={cn(
                  'flex flex-col gap-[0.28rem] min-w-0 min-h-0',
                  'pr-[4.35rem] sm:pr-16 [@media(max-height:560px)]:pr-[3.75rem]',
                )}
                initial={{ opacity: 0, y: 4 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -3 }}
                transition={{ duration: 0.22, ease: 'easeOut' }}
              >
                <p
                  className="block text-[0.72rem] font-extrabold [@media(max-height:560px)]:text-[0.68rem]"
                  style={{ color: selectedGame.accentColor }}
                >
                  {selectedGame.tagline}
                </p>
                <h2 className="text-[1.06rem] font-extrabold leading-[1.24] text-[#251f3e] sm:text-[1rem] [@media(max-height:560px)]:text-[0.98rem]">
                  {selectedGame.title}
                </h2>
                <p className="text-[0.8rem] leading-[1.45] text-[#564d75] line-clamp-2 sm:text-[0.78rem] [@media(max-height:560px)]:text-[0.76rem]">
                  {selectedGame.detail}
                </p>
              </motion.div>
            </AnimatePresence>
          </div>

          <div
            className={cn(
              'flex flex-wrap items-center gap-x-3 gap-y-2',
              'mt-[0.18rem] pt-[0.52rem] border-t border-[rgb(102_85_126/10%)]',
              '[@media(max-height:560px)]:hidden',
              '[@media(hover:none)_and_(pointer:coarse)]:hidden',
            )}
            aria-label="모니터 키보드 조작 안내"
          >
            <span className="inline-flex min-w-0 items-center gap-[0.38rem]">
              <span className="inline-flex items-center gap-[0.22rem]" aria-hidden>
                <kbd className={KEYCAP}>←</kbd>
                <kbd className={KEYCAP}>→</kbd>
              </span>
              <span className="whitespace-nowrap text-[0.72rem] font-extrabold leading-[1.25] text-[#554b73]">
                미니게임 넘기기
              </span>
            </span>
            <span className="inline-flex min-w-0 items-center gap-[0.38rem]">
              <kbd className={cn(KEYCAP, 'min-w-[3.65rem] tracking-[0.02em]')}>
                Space
              </kbd>
              <span className="whitespace-nowrap text-[0.72rem] font-extrabold leading-[1.25] text-[#554b73]">
                선택한 미니게임 시작
              </span>
            </span>
          </div>
        </motion.aside>
      )}
    </AnimatePresence>
  )
}
