import { HTTPError } from 'ky'
import { useEffect, useMemo } from 'react'
import { useShallow } from 'zustand/react/shallow'

import {
  ApiError,
  getAnonymousProfile,
  getFortune,
  getFortuneTodayAvailability,
  patchAnonymousBirthInfo,
  postAnonymousBirthInfo,
  postFortune,
} from '@/shared/apis'
import { runtime } from '@/shared/config'
import { useUserStore } from '@/shared/stores'
import type {
  AnonymousUserBirthInfoRequest,
  AnonymousUserProfileResponse,
} from '@/shared/types'

import { FORTUNE_EMPTY_BIRTH_INFO, FORTUNE_RESET_QUERY_PARAM } from '../constants'
import { useFortuneSessionStore } from '../fortuneSessionStore'
import type { FortuneBirthInfo } from '../types'
import {
  calculateFortuneSaju,
  clearStoredFortune,
  createFortuneCreateRequest,
  createFortuneResultFromIssuedResponse,
  createMockFortuneResult,
  getKoreanDateKey,
  getNextKoreanMidnightLabel,
  isBirthInfoComplete,
  readStoredFortune,
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
    errorMessage,
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
      errorMessage: state.errorMessage,
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
      const nextResult = await createFortuneResult(birthInfo)
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
    birthInfo,
    completePrinting,
    editBirthInfo,
    errorMessage,
    hasHydrated,
    isBirthInfoReady,
    isDrawingFortune,
    isSubmittingBirthInfo,
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

async function saveBirthInfo(birthInfo: FortuneBirthInfo, hasServerBirthInfo: boolean) {
  if (!useUserStore.getState().userUuid) {
    throw new Error(USER_NOT_READY_ERROR_MESSAGE)
  }

  const payload = createBirthInfoRequest(birthInfo)

  if (hasServerBirthInfo) {
    await patchAnonymousBirthInfo(payload)
    return
  }

  try {
    await postAnonymousBirthInfo(payload)
  } catch (error) {
    if (!isConflictError(error)) {
      throw error
    }

    await patchAnonymousBirthInfo(payload)
  }
}

async function createFortuneResult(birthInfo: FortuneBirthInfo) {
  if (!useUserStore.getState().userUuid) {
    throw new Error(USER_NOT_READY_ERROR_MESSAGE)
  }

  const issuedFortune = await postFortune(createFortuneCreateRequest(birthInfo))

  return createFortuneResultFromIssuedResponse(issuedFortune, birthInfo)
}

async function getTodayFortuneResult(birthInfo: FortuneBirthInfo | null) {
  const availability = await getFortuneTodayAvailability()

  if (availability.canDraw || !availability.fortuneId || !birthInfo) {
    return null
  }

  const issuedFortune = await getFortune(availability.fortuneId)

  return createFortuneResultFromIssuedResponse(issuedFortune, birthInfo)
}

async function resolveAlreadyIssuedResult(error: unknown, birthInfo: FortuneBirthInfo) {
  if (!isConflictError(error)) {
    return null
  }

  try {
    return await getTodayFortuneResult(birthInfo)
  } catch {
    return null
  }
}

function createBirthInfoRequest(birthInfo: FortuneBirthInfo): AnonymousUserBirthInfoRequest {
  return {
    birthday: birthInfo.birthDate,
    birthtime: birthInfo.timeUnknown ? NOON_FALLBACK_BIRTH_TIME : `${birthInfo.birthTime}:00`,
    isLunar: birthInfo.calendarType === 'lunar',
  }
}

function createBirthInfoFromProfile(profile: AnonymousUserProfileResponse): FortuneBirthInfo | null {
  if (!profile.birthday || !profile.birthtime || profile.isLunar === null) {
    return null
  }

  return {
    birthDate: profile.birthday,
    birthTime: profile.birthtime.slice(0, 5),
    calendarType: profile.isLunar ? 'lunar' : 'solar',
    timeUnknown: false,
  }
}

function canUseLocalFortuneFallback(error: unknown) {
  if (!runtime.isDev) {
    return false
  }

  if (error instanceof Error && error.message === USER_NOT_READY_ERROR_MESSAGE) {
    return true
  }

  if (error instanceof ApiError) {
    return false
  }

  if (error instanceof HTTPError) {
    return error.response.status === 404 || error.response.status === 405 || error.response.status >= 500
  }

  return true
}

function isConflictError(error: unknown) {
  if (error instanceof HTTPError) {
    return error.response.status === 409
  }

  return error instanceof ApiError && error.message.includes('이미')
}

function resolveBirthInfoErrorMessage(error: unknown) {
  if (error instanceof ApiError || error instanceof HTTPError) {
    return '생년월일 정보를 저장하지 못했어요. 잠시 후 다시 시도해 주세요.'
  }

  return '사용자 정보를 준비하는 중이에요. 잠시 후 다시 시도해 주세요.'
}

function resolveFortuneErrorMessage(error: unknown) {
  if (error instanceof HTTPError && error.response.status === 412) {
    return '생년월일 등록이 필요해요. 정보를 다시 확인해 주세요.'
  }

  if (error instanceof HTTPError && error.response.status === 502) {
    return '운세 생성 서비스에 일시적 장애가 발생했어요. 잠시 후 다시 시도해 주세요.'
  }

  if (error instanceof ApiError) {
    return error.message
  }

  return '운세를 가져오지 못했어요. 잠시 후 다시 시도해 주세요.'
}

const USER_NOT_READY_ERROR_MESSAGE = 'USER_NOT_READY'
const NOON_FALLBACK_BIRTH_TIME = '12:00:00'
const USER_STORE_HYDRATION_FALLBACK_DELAY_MS = 1500
