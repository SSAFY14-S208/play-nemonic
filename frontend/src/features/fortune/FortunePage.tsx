'use client'

import './fortune.css'

import { AnimatePresence, motion } from 'motion/react'
import { useRouter } from 'next/navigation'
import { useCallback, useEffect, useState } from 'react'

import { cn } from '@/shared/libs'

import {
  FORTUNE_DIALOGUES,
  FortuneBirthForm,
  FortuneDialoguePanel,
  FortuneDrawPanel,
  FortuneErrorView,
  FortuneLimitNotice,
  FortuneLoadingView,
  FortunePrintStatus,
  FortuneResultCard,
} from './components'
import FortuneVisual from './FortuneVisual'
import { useFortuneAudio, useFortuneBgm, useFortuneFlow } from './hooks'

const LAST_DIALOGUE_INDEX = FORTUNE_DIALOGUES.length - 1

export default function FortunePage() {
  const router = useRouter()
  const [noticeMessage, setNoticeMessage] = useState('')
  const [dialogueIndex, setDialogueIndex] = useState(0)
  const [isEntrySceneReady, setIsEntrySceneReady] = useState(false)
  const {
    completePrinting,
    editBirthInfo,
    hasHydrated,
    result,
    retryAfterError,
    resetTodayFortune,
    returnToIntro,
    showTodayResult,
    startBirthInfo,
    startPrinting,
    step,
    submitBirthInfo,
  } = useFortuneFlow()
  const { playPrintComplete, playPrintStart } = useFortuneAudio()
  const { isBgmMuted, toggleFortuneBgmMuted } = useFortuneBgm()

  const handleDialogueNext = () => {
    if (dialogueIndex < LAST_DIALOGUE_INDEX) {
      setDialogueIndex((currentDialogueIndex) => currentDialogueIndex + 1)
      return
    }

    startBirthInfo()
    setIsEntrySceneReady(false)
  }

  const handleReturnToDialogue = () => {
    setDialogueIndex(LAST_DIALOGUE_INDEX)
    setIsEntrySceneReady(false)
    returnToIntro()
  }

  const handleStartPrinting = () => {
    playPrintStart()
    void startPrinting()
  }

  const handlePrintComplete = () => {
    playPrintComplete()
    completePrinting()
  }

  const handleEntrySceneReady = useCallback(() => {
    setIsEntrySceneReady(true)
  }, [])

  const handleAttach = () => {
    setNoticeMessage('커뮤니티 캔버스 부착은 다음 통합 단계에서 연결할게요.')
  }

  const goBackToHub = () => {
    router.push('/hub')
  }

  const shouldPrepareEntrySpotlight = step === 'intro' && dialogueIndex === 0
  const shouldPlayEntrySpotlight = shouldPrepareEntrySpotlight && isEntrySceneReady

  useEffect(() => {
    if (!shouldPrepareEntrySpotlight || isEntrySceneReady) {
      return
    }

    let cancelled = false

    ;(async () => {
      await new Promise((resolve) => window.setTimeout(resolve, ENTRY_SCENE_READY_FALLBACK_DELAY_MS))

      if (!cancelled) {
        setIsEntrySceneReady(true)
      }
    })()

    return () => {
      cancelled = true
    }
  }, [isEntrySceneReady, shouldPrepareEntrySpotlight])

  return (
    <main className="fortune-page-shell relative min-h-dvh overflow-hidden bg-fortune-backdrop text-fortune-ink">
      <div className="fortune-magic-backdrop" aria-hidden />
      <FortuneVisual
        playEntrySpotlight={shouldPrepareEntrySpotlight}
        runEntrySpotlight={shouldPlayEntrySpotlight}
        onEntrySceneReady={handleEntrySceneReady}
        onPrintComplete={handlePrintComplete}
      />
      <section
        className={cn(
          'fortune-stage-overlay',
          shouldPlayEntrySpotlight && 'fortune-stage-overlay-entry',
          step === 'birthInfo' && 'fortune-stage-overlay-center fortune-stage-overlay-birth',
          step === 'intro' && 'fortune-stage-overlay-dialogue',
          (step === 'draw' || step === 'printing' || step === 'limit' || step === 'error') && 'fortune-stage-overlay-panel',
          step === 'result' && 'fortune-stage-overlay-scroll',
        )}
      >
        <AnimatePresence mode="sync">
          <motion.div
            key={`${hasHydrated}-${step}`}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: -8, scale: 0.992, transition: { duration: 0.08, ease: 'easeOut' } }}
            initial={{ opacity: 0, y: 18, scale: 0.985 }}
            transition={{ duration: 0.26, ease: 'easeOut' }}
          >
            {renderFortuneStep()}
          </motion.div>
        </AnimatePresence>
        {noticeMessage && (
          <p className="body-r mx-auto mt-4 max-w-[720px] rounded-[var(--radius-md)] bg-fortune-glow px-4 py-3 text-fortune-accent-strong" aria-live="polite">
            {noticeMessage}
          </p>
        )}
      </section>
      {shouldPrepareEntrySpotlight && (
        <div className={cn('fortune-entry-spotlight-cover', shouldPlayEntrySpotlight && 'is-lit')} aria-hidden />
      )}
      <button
        type="button"
        aria-label={isBgmMuted ? '타로 배경음악 켜기' : '타로 배경음악 음소거'}
        aria-pressed={isBgmMuted}
        className={cn('fortune-bgm-toggle', isBgmMuted && 'is-muted')}
        title={isBgmMuted ? '배경음악 켜기' : '배경음악 음소거'}
        onClick={toggleFortuneBgmMuted}
        onKeyDown={(event) => event.stopPropagation()}
        onPointerDown={(event) => event.stopPropagation()}
      >
        <span className="fortune-bgm-toggle-label">
          {isBgmMuted ? '배경음악 켜기' : '배경음악 음소거'}
        </span>
      </button>
    </main>
  )

  function renderFortuneStep() {
    if (!hasHydrated) {
      return <FortuneLoadingView />
    }

    if (step === 'intro') {
      return <FortuneDialoguePanel dialogueIndex={dialogueIndex} onNext={handleDialogueNext} />
    }

    if (step === 'birthInfo') {
      return (
        <FortuneBirthForm
          onBack={handleReturnToDialogue}
          onSubmit={submitBirthInfo}
        />
      )
    }

    if (step === 'draw') {
      return (
        <FortuneDrawPanel
          onDraw={handleStartPrinting}
          onEdit={editBirthInfo}
        />
      )
    }

    if (step === 'printing') {
      return <FortunePrintStatus />
    }

    if (step === 'result' && result) {
      return <FortuneResultCard onAttach={handleAttach} onBackToHub={goBackToHub} />
    }

    if (step === 'limit') {
      return (
        <FortuneLimitNotice
          onReset={resetTodayFortune}
          onShowResult={showTodayResult}
        />
      )
    }

    return <FortuneErrorView onRetry={retryAfterError} />
  }
}

const ENTRY_SCENE_READY_FALLBACK_DELAY_MS = 900
