import { useEffect, useMemo, useState } from 'react'

import { runtime } from '@/shared/config'

import { FORTUNE_EMPTY_BIRTH_INFO, FORTUNE_RESET_QUERY_PARAM } from '../constants'
import type { FortuneBirthInfo, FortuneResult, FortuneStep } from '../types'
import {
  clearStoredFortune,
  calculateFortuneSaju,
  createMockFortuneResult,
  getKoreanDateKey,
  getNextKoreanMidnightLabel,
  isBirthInfoComplete,
  readStoredFortune,
  writeStoredFortune,
} from '../utils'

export function useFortuneFlow() {
  const [step, setStep] = useState<FortuneStep>('intro')
  const [birthInfo, setBirthInfo] = useState<FortuneBirthInfo>(FORTUNE_EMPTY_BIRTH_INFO)
  const [result, setResult] = useState<FortuneResult | null>(null)
  const [hasHydrated, setHasHydrated] = useState(false)
  const [errorMessage, setErrorMessage] = useState('')

  useEffect(() => {
    let cancelled = false

    ;(async () => {
      await Promise.resolve()

      const searchParams = new URLSearchParams(window.location.search)
      const shouldResetStoredFortune = searchParams.get(FORTUNE_RESET_QUERY_PARAM) === '1'

      if (runtime.isDev && shouldResetStoredFortune) {
        clearStoredFortune()
        window.history.replaceState(null, '', `${window.location.pathname}${window.location.hash}`)
      }

      const storedFortune = readStoredFortune()
      const todayDateKey = getKoreanDateKey()

      if (cancelled) {
        return
      }

      if (storedFortune?.dateKey === todayDateKey) {
        setBirthInfo(storedFortune.birthInfo)
        setResult(storedFortune.result)
        setStep('limit')
      }

      setHasHydrated(true)
    })()

    return () => {
      cancelled = true
    }
  }, [])

  const isBirthInfoReady = useMemo(() => isBirthInfoComplete(birthInfo), [birthInfo])
  const sajuPreview = useMemo(() => {
    if (!isBirthInfoComplete(birthInfo)) {
      return null
    }

    try {
      return calculateFortuneSaju(birthInfo)
    } catch {
      return null
    }
  }, [birthInfo])
  const nextResetLabel = getNextKoreanMidnightLabel()

  const startBirthInfo = () => {
    setStep('birthInfo')
  }

  const returnToIntro = () => {
    setStep('intro')
  }

  const submitBirthInfo = () => {
    if (!isBirthInfoComplete(birthInfo)) {
      return
    }

    setStep('draw')
  }

  const editBirthInfo = () => {
    setStep('birthInfo')
  }

  const startPrinting = () => {
    try {
      setErrorMessage('')
      const nextResult = createMockFortuneResult(birthInfo)
      setResult(nextResult)
      setStep('printing')
    } catch {
      setErrorMessage('운세를 가져오지 못했어요. 잠시 후 다시 시도해 주세요.')
      setStep('error')
    }
  }

  const completePrinting = () => {
    if (!result) {
      setErrorMessage('출력할 운세를 찾지 못했어요.')
      setStep('error')
      return
    }

    const dateKey = getKoreanDateKey()

    // FIXME(backend-integration): Replace this localStorage-only daily issuance with server-side KST date idempotency.
    writeStoredFortune({
      dateKey,
      birthInfo,
      result,
      issuedAt: new Date().toISOString(),
    })

    setStep('result')
  }

  const showTodayResult = () => {
    if (result) {
      setStep('result')
    }
  }

  const retryAfterError = () => {
    setStep(isBirthInfoComplete(birthInfo) ? 'draw' : 'birthInfo')
  }

  const resetTodayFortune = () => {
    if (!runtime.isDev) {
      return
    }

    clearStoredFortune()
    setBirthInfo(FORTUNE_EMPTY_BIRTH_INFO)
    setResult(null)
    setErrorMessage('')
    setStep('intro')
  }

  return {
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
  }
}
