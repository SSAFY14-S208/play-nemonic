import { useCallback, useEffect, useState } from 'react'

interface UseFortuneTypewriterTextOptions {
  characterCount: number
  characterDelayMs?: number
  prefersReducedMotion?: boolean
  startDelayMs?: number
}

const DEFAULT_CHARACTER_DELAY_MS = 34
const DEFAULT_START_DELAY_MS = 0

export function useFortuneTypewriterText({
  characterCount,
  characterDelayMs = DEFAULT_CHARACTER_DELAY_MS,
  prefersReducedMotion = false,
  startDelayMs = DEFAULT_START_DELAY_MS,
}: UseFortuneTypewriterTextOptions) {
  const [visibleCharacterCount, setVisibleCharacterCount] = useState(
    prefersReducedMotion ? characterCount : 0,
  )
  const isComplete = visibleCharacterCount >= characterCount

  useEffect(() => {
    let cancelled = false

    if (prefersReducedMotion || characterCount <= 0) {
      ;(async () => {
        await Promise.resolve()

        if (!cancelled) {
          setVisibleCharacterCount(characterCount)
        }
      })()

      return () => {
        cancelled = true
      }
    }

    if (visibleCharacterCount >= characterCount) {
      return () => {
        cancelled = true
      }
    }

    const delayMs = visibleCharacterCount === 0 ? startDelayMs : characterDelayMs
    const timeoutId = window.setTimeout(() => {
      if (!cancelled) {
        setVisibleCharacterCount((currentCharacterCount) =>
          Math.min(currentCharacterCount + 1, characterCount),
        )
      }
    }, delayMs)

    return () => {
      cancelled = true
      window.clearTimeout(timeoutId)
    }
  }, [characterCount, characterDelayMs, prefersReducedMotion, startDelayMs, visibleCharacterCount])

  const completeText = useCallback(() => {
    setVisibleCharacterCount(characterCount)
  }, [characterCount])

  return {
    completeText,
    isComplete,
    visibleCharacterCount,
  }
}
