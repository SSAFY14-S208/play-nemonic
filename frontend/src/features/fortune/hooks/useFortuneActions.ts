'use client'

import { useCallback } from 'react'

import { runtime } from '@/shared/config'
import { completeFunnelStep, reachFunnelGoal } from '@/shared/libs'

import { FORTUNE_EMPTY_BIRTH_INFO } from '../constants'
import { useFortuneSessionStore } from '../fortuneSessionStore'
import {
  canUseLocalFortuneFallback,
  clearStoredFortune,
  createMockFortuneResult,
  isBirthInfoComplete,
  isFortuneConflictError,
  issueNewFortune,
  resolveAlreadyIssuedResult,
  resolveBirthInfoErrorMessage,
  resolveFortuneErrorMessage,
  saveBirthInfo,
  writeStoredFortune,
} from '../utils'

export function useFortuneActions() {
  const startBirthInfo = useCallback(() => {
    // intro(landing) → birthInfo 전환. landing step(step_index=0)이 완료된 시점.
    // funnel_started가 step 0 진입을 의미하므로 별도 viewed 이벤트는 발사하지 않는다.
    completeFunnelStep('landing', 0, { content_type: 'fortune' })
    useFortuneSessionStore.getState().setStep('birthInfo')
  }, [])

  const editBirthInfo = useCallback(() => {
    useFortuneSessionStore.getState().setStep('birthInfo')
  }, [])

  const returnToIntro = useCallback(() => {
    useFortuneSessionStore.getState().setStep('intro')
  }, [])

  const submitBirthInfo = useCallback(async () => {
    const {
      birthInfo,
      hasServerBirthInfo,
      isSubmittingBirthInfo,
      setStep,
      setIsSubmittingBirthInfo,
      setHasServerBirthInfo,
      setErrorMessage,
    } = useFortuneSessionStore.getState()

    if (!isBirthInfoComplete(birthInfo) || isSubmittingBirthInfo) {
      return
    }

    setIsSubmittingBirthInfo(true)
    setErrorMessage('')

    try {
      await saveBirthInfo(birthInfo, hasServerBirthInfo)
      setHasServerBirthInfo(true)
      completeFunnelStep('birth_info', 1, { content_type: 'fortune' })
      setStep('draw')
    } catch (error) {
      if (canUseLocalFortuneFallback(error)) {
        completeFunnelStep('birth_info', 1, { content_type: 'fortune' })
        setStep('draw')
      } else {
        setErrorMessage(resolveBirthInfoErrorMessage(error))
        setStep('error')
      }
    } finally {
      setIsSubmittingBirthInfo(false)
    }
  }, [])

  const startPrinting = useCallback(async () => {
    const {
      birthInfo,
      isDrawingFortune,
      setStep,
      setResult,
      setIsDrawingFortune,
      setErrorMessage,
    } = useFortuneSessionStore.getState()

    if (!isBirthInfoComplete(birthInfo) || isDrawingFortune) {
      return
    }

    setIsDrawingFortune(true)
    setErrorMessage('')

    try {
      const nextResult = await issueNewFortune(birthInfo)
      setResult(nextResult)
      completeFunnelStep('theme_select', 2, { content_type: 'fortune' })
      setStep('printing')
    } catch (error) {
      const alreadyIssuedResult = await resolveAlreadyIssuedResult(error, birthInfo)

      if (alreadyIssuedResult) {
        setResult(alreadyIssuedResult)
        setStep('limit')
      } else if (isFortuneConflictError(error)) {
        // 이미 오늘 발급된 사실은 명확하나 본문 복원 실패(다른 기기/storage 비움 등).
        // 에러 화면 대신 한도 안내 화면으로 보낸다.
        setStep('limit')
      } else if (canUseLocalFortuneFallback(error)) {
        const nextResult = createMockFortuneResult(birthInfo)
        setResult(nextResult)
        completeFunnelStep('theme_select', 2, { content_type: 'fortune' })
        setStep('printing')
      } else {
        setErrorMessage(resolveFortuneErrorMessage(error))
        setStep('error')
      }
    } finally {
      setIsDrawingFortune(false)
    }
  }, [])

  const completePrinting = useCallback(() => {
    const { result, birthInfo, setStep, setErrorMessage } = useFortuneSessionStore.getState()

    if (!result) {
      setErrorMessage('출력할 운세를 찾지 못했어요.')
      setStep('error')
      return
    }

    writeStoredFortune({
      dateKey: result.issuedDateKey,
      birthInfo,
      result,
      issuedAt: new Date().toISOString(),
    })

    completeFunnelStep('loading', 3, { content_type: 'fortune' })
    reachFunnelGoal('fortune_result_viewed', { content_type: 'fortune' })
    setStep('result')
  }, [])

  const showTodayResult = useCallback(() => {
    const { result, setStep } = useFortuneSessionStore.getState()
    if (result) {
      setStep('result')
    }
  }, [])

  const retryAfterError = useCallback(() => {
    const { birthInfo, setStep } = useFortuneSessionStore.getState()
    setStep(isBirthInfoComplete(birthInfo) ? 'draw' : 'birthInfo')
  }, [])

  const resetTodayFortune = useCallback(() => {
    if (!runtime.isDev) {
      return
    }

    const { setBirthInfo, setResult, setErrorMessage, setStep } = useFortuneSessionStore.getState()
    clearStoredFortune()
    setBirthInfo(FORTUNE_EMPTY_BIRTH_INFO)
    setResult(null)
    setErrorMessage('')
    setStep('intro')
  }, [])

  return {
    completePrinting,
    editBirthInfo,
    resetTodayFortune,
    retryAfterError,
    returnToIntro,
    showTodayResult,
    startBirthInfo,
    startPrinting,
    submitBirthInfo,
  }
}
