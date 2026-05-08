'use client'

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
  getKoreanDateKey,
  getTodayFortuneResult,
  readStoredFortune,
} from '../utils'

const USER_STORE_HYDRATION_FALLBACK_DELAY_MS = 1500

export function useFortuneSessionHydration() {
  const userUuid = useUserStore((state) => state.userUuid)
  const { hasUserStoreHydrated, hasHydrated } = useFortuneSessionStore(
    useShallow((state) => ({
      hasUserStoreHydrated: state.hasUserStoreHydrated,
      hasHydrated: state.hasHydrated,
    })),
  )
  const {
    setStep,
    setBirthInfo,
    setResult,
    setHasUserStoreHydrated,
    setHasHydrated,
    setHasServerBirthInfo,
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
}
