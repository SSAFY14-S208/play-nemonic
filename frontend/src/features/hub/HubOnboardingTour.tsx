'use client'

import { useCallback, useEffect, useMemo, type CSSProperties } from 'react'
import {
  ArrowLeft,
  ArrowRight,
  X,
} from 'lucide-react'
import { AnimatePresence, motion } from 'motion/react'
import { cn } from '@/shared/libs'
import { useHubOnboardingStore, useHubRoomStore } from '@/shared/stores'
import type { HubFocusKey } from '@/shared/types'
import './hub.css'

const styles = {
  root: 'hot-root',
  backdrop: 'hot-backdrop',
  panel: 'hot-panel',
  accentBar: 'hot-accent-bar',
  panelHeader: 'hot-panel-header',
  skipButton: 'hot-skip-button',
  stepRail: 'hot-step-rail',
  stepRailItem: 'hot-step-rail-item',
  stepRailDot: 'hot-step-rail-dot',
  stepRailLabel: 'hot-step-rail-label',
  stepRailItemCurrent: 'hot-step-rail-item-current',
  stepRailItemCompleted: 'hot-step-rail-item-completed',
  stepRailTrack: 'hot-step-rail-track',
  stepBody: 'hot-step-body',
  title: 'hot-title',
  description: 'hot-description',
  highlightList: 'hot-highlight-list',
  highlightItem: 'hot-highlight-item',
  highlightBullet: 'hot-highlight-bullet',
  footer: 'hot-footer',
  keyboardHint: 'hot-keyboard-hint',
  keycap: 'hot-keycap',
  navButton: 'hot-nav-button',
  navButtonGhost: 'hot-nav-button-ghost',
  navButtonPrimary: 'hot-nav-button-primary',
}

/** Where the panel sits on screen.
 * - `centered`: 화면 정중앙 + 약한 백드롭. 첫인상이 임팩트 있게 들어오는 welcome 단계용.
 * - `corner`: 좌하단 사이드 패널 + 백드롭 없음. 룸이 보여야 하는 투어 단계용. */
type PanelAnchor = 'centered' | 'corner'

interface OnboardingStep {
  id: string
  shortLabel: string
  title: string
  description: string
  focusKey?: HubFocusKey
  highlights: string[]
  // Pastel color tied to that area's lighting. Used to tint the badge, accent
  // line, and active indicator so each step feels like a different "spot" in
  // the room rather than four identical purple cards.
  accentColor: string
  accentSoftColor: string
  anchor: PanelAnchor
}

const ONBOARDING_STEPS: OnboardingStep[] = [
  {
    id: 'welcome',
    shortLabel: '환영',
    title: '방 안에서 원하는 미니게임을 골라보세요',
    description:
      '책상 모니터에서 미니게임을 시작하고, 네모닉으로 결과를 뽑고, 벽 보드에서 친구들의 흔적을 둘러볼 수 있어요.',
    focusKey: 'overview',
    highlights: [
      '처음이라면 핵심만 짧게 보여드릴게요',
      '하단 버튼으로 원하는 공간으로 바로 이동할 수 있어요',
    ],
    accentColor: '#b89cff',
    accentSoftColor: '#efe6ff',
    anchor: 'centered',
  },
  {
    id: 'monitor',
    shortLabel: '모니터',
    title: '책상 위 모니터에서 미니게임을 고를 수 있어요',
    description:
      '운세, 플립북, 릴레이 드로잉, 무한 캔버스를 넘겨 보다가 마음에 드는 미니게임이 보이면 바로 시작해요.',
    focusKey: 'monitor',
    highlights: [
      '방향키 ← → 로 미니게임 넘기기',
      'Space 또는 START로 시작',
    ],
    accentColor: '#5ec0ff',
    accentSoftColor: '#dff1ff',
    anchor: 'corner',
  },
  {
    id: 'printer',
    shortLabel: '프린터',
    title: '네모닉 기기를 직접 체험해보세요',
    description:
      '허브에서 네모닉 프린터를 클릭하면 줌인되고, 확대된 상태에서 다시 클릭하면 단독 프린터 체험 화면으로 이동해요.',
    focusKey: 'printer',
    highlights: [
      '기기를 클릭해 네모닉 프린터를 가까이 볼 수 있어요',
      '단독 화면에서 소리와 움직임을 실제처럼 체험해요',
    ],
    accentColor: '#ffae72',
    accentSoftColor: '#fff0e2',
    anchor: 'corner',
  },
  {
    id: 'communityBoard',
    shortLabel: '보드',
    title: '벽 보드에서 모두의 결과를 둘러봐요',
    description:
      '운세 메모, 함께 그린 그림, 짧은 GIF가 한곳에 모여요. 마음에 드는 결과 옆에 내 결과도 붙일 수 있어요.',
    focusKey: 'communityBoard',
    highlights: [
      '완성한 결과를 보드에 남기기',
      '친구들이 만든 그림과 메모 둘러보기',
    ],
    accentColor: '#7fc77a',
    accentSoftColor: '#e1f3df',
    anchor: 'corner',
  },
]

export default function HubOnboardingTour() {
  const hasEnteredHub = useHubOnboardingStore((state) => state.hasEnteredHub)
  const hasHydratedFromStorage = useHubOnboardingStore(
    (state) => state.hasHydratedFromStorage,
  )
  const hasSeenOnboarding = useHubOnboardingStore(
    (state) => state.hasSeenOnboarding,
  )
  const stepIndex = useHubOnboardingStore((state) => state.currentStepIndex)
  const setCurrentStepIndex = useHubOnboardingStore(
    (state) => state.setCurrentStepIndex,
  )
  const dismissOnboarding = useHubOnboardingStore(
    (state) => state.dismissOnboarding,
  )
  const hydrateFromStorage = useHubOnboardingStore(
    (state) => state.hydrateFromStorage,
  )
  const setFocus = useHubRoomStore((state) => state.setFocus)

  useEffect(() => {
    hydrateFromStorage()
  }, [hydrateFromStorage])

  const isVisible =
    hasHydratedFromStorage && hasEnteredHub && !hasSeenOnboarding
  const totalSteps = ONBOARDING_STEPS.length
  const safeStepIndex = Math.min(Math.max(0, stepIndex), totalSteps - 1)
  const currentStep = ONBOARDING_STEPS[safeStepIndex]
  const isLastStep = safeStepIndex === totalSteps - 1

  useEffect(() => {
    if (!isVisible || !currentStep.focusKey) return
    setFocus(currentStep.focusKey)
  }, [currentStep.focusKey, isVisible, setFocus])

  const goPrevious = useCallback(() => {
    setCurrentStepIndex(Math.max(0, safeStepIndex - 1))
  }, [safeStepIndex, setCurrentStepIndex])

  const goNext = useCallback(() => {
    setCurrentStepIndex(Math.min(totalSteps - 1, safeStepIndex + 1))
  }, [safeStepIndex, setCurrentStepIndex, totalSteps])

  const finishTour = useCallback(() => {
    dismissOnboarding()
    setFocus('overview')
  }, [dismissOnboarding, setFocus])

  useEffect(() => {
    if (!isVisible) return

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'ArrowRight') {
        event.preventDefault()
        if (isLastStep) {
          finishTour()
        } else {
          goNext()
        }
      } else if (event.key === 'ArrowLeft') {
        event.preventDefault()
        goPrevious()
      } else if (event.key === 'Escape') {
        event.preventDefault()
        finishTour()
      }
    }

    window.addEventListener('keydown', handleKeyDown)

    return () => {
      window.removeEventListener('keydown', handleKeyDown)
    }
  }, [finishTour, goNext, goPrevious, isLastStep, isVisible])

  const progressRatio = useMemo(
    () => (safeStepIndex + 1) / totalSteps,
    [safeStepIndex, totalSteps],
  )
  const accentStyle = {
    '--onboarding-accent': currentStep.accentColor,
    '--onboarding-accent-soft': currentStep.accentSoftColor,
  } as CSSProperties

  // 첫 등장(welcome)은 가운데에서 임팩트, 이후 모니터 단계부터는 좌하단 사이드.
  // 위치 자체는 즉시 고정하고, 단계 전환은 내용/색 변화만 부드럽게 처리한다.
  // ⚠️ centered anchor는 CSS의 transform: translate(-50%, 50%)에 의존해 가운데
  // 정렬되므로, framer-motion이 inline transform을 덮어쓰지 않도록 mount/unmount
  // 모션은 opacity만 사용한다.
  const anchor = currentStep.anchor

  return (
    <AnimatePresence>
      {isVisible && (
        <motion.div
          key="hub-onboarding-tour"
          className={styles.root}
          data-anchor={anchor}
          style={accentStyle}
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.3, ease: [0.16, 1, 0.3, 1] }}
          aria-live="polite"
        >
          {/* welcome 단계에서만 약한 백드롭. 룸이 살짝 비치면서 카드가 가운데
              떠 있는 첫인상을 만든다. 코너로 이동하면 자동으로 사라진다.
              backdrop 클릭으로 dismiss는 일부러 비활성 — 첫 단계에서 실수로
              튜토리얼이 꺼지지 않도록. 명시적 "나중에" 버튼만 dismiss. */}
          <div className={styles.backdrop} aria-hidden />
          <motion.aside
            role="dialog"
            aria-modal="false"
            aria-labelledby="hub-onboarding-title"
            className={styles.panel}
            data-anchor={anchor}
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.28, ease: [0.16, 1, 0.3, 1] }}
          >
            <span className={styles.accentBar} aria-hidden />

            <header className={styles.panelHeader}>
              <button
                type="button"
                className={styles.skipButton}
                onClick={finishTour}
                aria-label="둘러보기 건너뛰기"
              >
                <span>나중에</span>
                <X className="h-3.5 w-3.5" strokeWidth={2.6} />
              </button>
            </header>

            <nav
              className={styles.stepRail}
              aria-label="온보딩 단계"
            >
              {ONBOARDING_STEPS.map((step, index) => {
                const isCompleted = index < safeStepIndex
                const isCurrent = index === safeStepIndex
                const stepStyle = {
                  '--rail-step-accent': step.accentColor,
                } as CSSProperties

                return (
                  <button
                    key={step.id}
                    type="button"
                    className={cn(
                      styles.stepRailItem,
                      isCurrent && styles.stepRailItemCurrent,
                      isCompleted && styles.stepRailItemCompleted,
                    )}
                    style={stepStyle}
                    onClick={() => setCurrentStepIndex(index)}
                    aria-current={isCurrent ? 'step' : undefined}
                    aria-label={`${index + 1}단계 ${step.shortLabel}`}
                  >
                    <span className={styles.stepRailDot} aria-hidden />
                    <span className={styles.stepRailLabel}>
                      {step.shortLabel}
                    </span>
                  </button>
                )
              })}
              <span
                className={styles.stepRailTrack}
                aria-hidden
                style={{ transform: `scaleX(${progressRatio})` }}
              />
            </nav>

            <AnimatePresence mode="wait">
              <motion.div
                key={currentStep.id}
                className={styles.stepBody}
                initial={{ opacity: 0, y: 6 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -4 }}
                transition={{ duration: 0.24, ease: 'easeOut' }}
              >
                <h2 id="hub-onboarding-title" className={styles.title}>
                  {currentStep.title}
                </h2>
                <p className={styles.description}>{currentStep.description}</p>
                <ul className={styles.highlightList}>
                  {currentStep.highlights.map((highlight) => (
                    <li key={highlight} className={styles.highlightItem}>
                      <span className={styles.highlightBullet} aria-hidden />
                      <span>{highlight}</span>
                    </li>
                  ))}
                </ul>
              </motion.div>
            </AnimatePresence>

            <footer className={styles.footer}>
              <button
                type="button"
                className={cn(styles.navButton, styles.navButtonGhost)}
                onClick={goPrevious}
                disabled={safeStepIndex === 0}
                aria-label="이전 단계"
              >
                <ArrowLeft className="h-4 w-4" strokeWidth={2.5} />
              </button>
              <div
                className={styles.keyboardHint}
                role="group"
                aria-label="키보드 방향키로 이전 또는 다음 단계 이동"
              >
                <kbd className={styles.keycap}>←</kbd>
                <kbd className={styles.keycap}>→</kbd>
              </div>
              {isLastStep ? (
                <button
                  type="button"
                  className={cn(styles.navButton, styles.navButtonPrimary)}
                  onClick={finishTour}
                  aria-label="둘러보기 시작"
                >
                  <span>둘러보기 시작</span>
                </button>
              ) : (
                <button
                  type="button"
                  className={cn(styles.navButton, styles.navButtonPrimary)}
                  onClick={goNext}
                  aria-label="다음 단계"
                >
                  <span>다음</span>
                  <ArrowRight className="h-4 w-4" strokeWidth={2.5} />
                </button>
              )}
            </footer>
          </motion.aside>
        </motion.div>
      )}
    </AnimatePresence>
  )
}
