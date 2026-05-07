'use client'

import { AnimatePresence, motion } from 'motion/react'
import { useRouter } from 'next/navigation'
import { useCallback, useState } from 'react'

import { runtime } from '@/shared/config'
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
import { useFortuneAudio, useFortuneFlow } from './hooks'

const LAST_DIALOGUE_INDEX = FORTUNE_DIALOGUES.length - 1

export default function FortunePage() {
  const router = useRouter()
  const [noticeMessage, setNoticeMessage] = useState('')
  const [dialogueIndex, setDialogueIndex] = useState(0)
  const [isEntrySceneReady, setIsEntrySceneReady] = useState(false)
  const {
    birthInfo,
    completePrinting,
    editBirthInfo,
    errorMessage,
    hasHydrated,
    isBirthInfoReady,
    nextResetLabel,
    result,
    retryAfterError,
    resetTodayFortune,
    returnToIntro,
    sajuPreview,
    setBirthInfo,
    showTodayResult,
    startBirthInfo,
    startPrinting,
    step,
    submitBirthInfo,
  } = useFortuneFlow()
  const { playPrintComplete, playPrintStart } = useFortuneAudio()

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
    startPrinting()
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

  return (
    <main className="fortune-page-shell relative min-h-dvh overflow-hidden bg-fortune-backdrop text-fortune-ink">
      <div className="fortune-magic-backdrop" aria-hidden />
      <FortuneVisual
        isPrinting={step === 'printing'}
        playEntrySpotlight={shouldPrepareEntrySpotlight}
        runEntrySpotlight={shouldPlayEntrySpotlight}
        result={result}
        onEntrySceneReady={handleEntrySceneReady}
        onPrintComplete={handlePrintComplete}
      />
      <section
        className={cn(
          'fortune-stage-overlay',
          shouldPlayEntrySpotlight && 'fortune-stage-overlay-entry',
          step === 'birthInfo' && 'fortune-stage-overlay-center fortune-stage-overlay-birth',
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
          birthInfo={birthInfo}
          isComplete={isBirthInfoReady}
          onBack={handleReturnToDialogue}
          onChange={setBirthInfo}
          onSubmit={submitBirthInfo}
        />
      )
    }

    if (step === 'draw') {
      return <FortuneDrawPanel birthInfo={birthInfo} saju={sajuPreview} onDraw={handleStartPrinting} onEdit={editBirthInfo} />
    }

    if (step === 'printing') {
      return <FortunePrintStatus isPrinting />
    }

    if (step === 'result' && result) {
      return <FortuneResultCard result={result} onAttach={handleAttach} onBackToHub={goBackToHub} />
    }

    if (step === 'limit') {
      return (
        <FortuneLimitNotice
          nextResetLabel={nextResetLabel}
          result={result}
          showResetAction={runtime.isDev}
          onReset={resetTodayFortune}
          onShowResult={showTodayResult}
        />
      )
    }

    return <FortuneErrorView message={errorMessage} onRetry={retryAfterError} />
  }
}
