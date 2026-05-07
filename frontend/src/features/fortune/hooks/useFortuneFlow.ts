import { useEffect } from 'react'
import { useShallow } from 'zustand/react/shallow'

import { getAnonymousProfile } from '@/shared/apis'
import { runtime } from '@/shared/config'
import { useUserStore } from '@/shared/stores'

import { FORTUNE_EMPTY_BIRTH_INFO, FORTUNE_RESET_QUERY_PARAM } from '../constants'
import { useFortuneSessionStore } from '../fortuneSessionStore'
import {
  canUseLocalFortuneFallback,
  clearStoredFortune,
  createBirthInfoFromProfile,
  createMockFortuneResult,
  getKoreanDateKey,
  getTodayFortuneResult,
  isBirthInfoComplete,
  issueNewFortune,
  readStoredFortune,
  resolveAlreadyIssuedResult,
  resolveBirthInfoErrorMessage,
  resolveFortuneErrorMessage,
  saveBirthInfo,
  writeStoredFortune,
} from '../utils'

export function useFortuneFlow() {
  const userUuid = useUserStore((state) => state.userUuid)
  const {
    step,
    birthInfo,
    result,
    hasUserStoreHydrated,
    hasHydrated,
    hasServerBirthInfo,
    isSubmittingBirthInfo,
    isDrawingFortune,
  } = useFortuneSessionStore(
    useShallow((state) => ({
      step: state.step,
      birthInfo: state.birthInfo,
      result: state.result,
      hasUserStoreHydrated: state.hasUserStoreHydrated,
      hasHydrated: state.hasHydrated,
      hasServerBirthInfo: state.hasServerBirthInfo,
      isSubmittingBirthInfo: state.isSubmittingBirthInfo,
      isDrawingFortune: state.isDrawingFortune,
    })),
  )
  const {
    setStep,
    setBirthInfo,
    setResult,
    setHasUserStoreHydrated,
    setHasHydrated,
    setHasServerBirthInfo,
    setIsSubmittingBirthInfo,
    setIsDrawingFortune,
    setErrorMessage,
  } = useFortuneSessionStore.getState()

  useEffect(() => {
    if (hasUserStoreHydrated) return
    if (typeof window === 'undefined') return

    let cancelled = false
    const finishHydration = () => {
      if (!cancelled) {
        setHasUserStoreHydrated(true)
      }
    }

    const fallbackTimerId = window.setTimeout(finishHydration, USER_STORE_HYDRATION_FALLBACK_DELAY_MS)

    if (!useUserStore.persist) {
      finishHydration()
      return () => {
        cancelled = true
        window.clearTimeout(fallbackTimerId)
      }
    }

    const unsubscribe = useUserStore.persist.onFinishHydration(finishHydration)

    ;(async () => {
      await Promise.resolve()

      if (!cancelled && useUserStore.persist?.hasHydrated() === true) {
        finishHydration()
      }
    })()

    return () => {
      cancelled = true
      window.clearTimeout(fallbackTimerId)
      unsubscribe()
    }
  }, [hasUserStoreHydrated, setHasUserStoreHydrated])

  useEffect(() => {
    if (!hasUserStoreHydrated || hasHydrated) {
      return
    }

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
      let shouldUseStoredFortune = storedFortune?.dateKey === todayDateKey

      if (userUuid) {
        try {
          const profile = await getAnonymousProfile()
          const profileBirthInfo = createBirthInfoFromProfile(profile)

          if (cancelled) {
            return
          }

          if (profileBirthInfo) {
            setBirthInfo(profileBirthInfo)
            setHasServerBirthInfo(true)
          }

          const todayResult = await getTodayFortuneResult(profileBirthInfo ?? storedFortune?.birthInfo ?? null)

          if (cancelled) {
            return
          }

          if (todayResult) {
            setBirthInfo(profileBirthInfo ?? storedFortune?.birthInfo ?? FORTUNE_EMPTY_BIRTH_INFO)
            setResult(todayResult)
            setStep('limit')
            shouldUseStoredFortune = false
          } else {
            shouldUseStoredFortune = false
          }
        } catch (error) {
          if (cancelled) {
            return
          }

          if (!canUseLocalFortuneFallback(error)) {
            shouldUseStoredFortune = false
          }
        }
      }

      if (cancelled) {
        return
      }

      if (shouldUseStoredFortune && storedFortune) {
        setBirthInfo(storedFortune.birthInfo)
        setResult(storedFortune.result)
        setStep('limit')
      }

      setHasHydrated(true)
    })()

    return () => {
      cancelled = true
    }
  }, [
    hasHydrated,
    hasUserStoreHydrated,
    setBirthInfo,
    setHasHydrated,
    setHasServerBirthInfo,
    setResult,
    setStep,
    userUuid,
  ])

  useEffect(() => {
    return () => {
      useFortuneSessionStore.getState().resetSession()
    }
  }, [])

  const startBirthInfo = () => {
    setStep('birthInfo')
  }

  const returnToIntro = () => {
    setStep('intro')
  }

  const submitBirthInfo = async () => {
    if (!isBirthInfoComplete(birthInfo) || isSubmittingBirthInfo) {
      return
    }

    setIsSubmittingBirthInfo(true)
    setErrorMessage('')

    try {
      await saveBirthInfo(birthInfo, hasServerBirthInfo)
      setHasServerBirthInfo(true)
      setStep('draw')
    } catch (error) {
      if (canUseLocalFortuneFallback(error)) {
        setStep('draw')
      } else {
        setErrorMessage(resolveBirthInfoErrorMessage(error))
        setStep('error')
      }
    } finally {
      setIsSubmittingBirthInfo(false)
    }
  }

  const editBirthInfo = () => {
    setStep('birthInfo')
  }

  const startPrinting = async () => {
    if (!isBirthInfoComplete(birthInfo) || isDrawingFortune) {
      return
    }

    setIsDrawingFortune(true)
    setErrorMessage('')

    try {
      const nextResult = await issueNewFortune(birthInfo)
      setResult(nextResult)
      setStep('printing')
    } catch (error) {
      const alreadyIssuedResult = await resolveAlreadyIssuedResult(error, birthInfo)

      if (alreadyIssuedResult) {
        setResult(alreadyIssuedResult)
        setStep('limit')
      } else if (canUseLocalFortuneFallback(error)) {
        const nextResult = createMockFortuneResult(birthInfo)
        setResult(nextResult)
        setStep('printing')
      } else {
        setErrorMessage(resolveFortuneErrorMessage(error))
        setStep('error')
      }
    } finally {
      setIsDrawingFortune(false)
    }
  }

  const completePrinting = () => {
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
  }
}

const USER_STORE_HYDRATION_FALLBACK_DELAY_MS = 1500
