'use client'

import { useEffect, useState } from 'react'

const ONE_MINUTE_MS = 60_000

function formatPhoneClockTime(date = new Date()) {
  const twelveHour = date.getHours() % 12 || 12
  const minutes = String(date.getMinutes()).padStart(2, '0')

  return `${twelveHour}:${minutes}`
}

function getMillisecondsUntilNextMinute() {
  const currentTime = new Date()

  return (
    ONE_MINUTE_MS -
    (currentTime.getSeconds() * 1000 + currentTime.getMilliseconds())
  )
}

export function usePhoneClock() {
  const [timeLabel, setTimeLabel] = useState(formatPhoneClockTime)

  useEffect(() => {
    let timeoutId: number | undefined
    let intervalId: number | undefined
    let isDisposed = false

    const updateTimeLabel = () => {
      if (!isDisposed) {
        setTimeLabel(formatPhoneClockTime())
      }
    }

    void (async () => {
      updateTimeLabel()
      timeoutId = window.setTimeout(() => {
        updateTimeLabel()
        intervalId = window.setInterval(updateTimeLabel, ONE_MINUTE_MS)
      }, getMillisecondsUntilNextMinute())
    })()

    return () => {
      isDisposed = true

      if (timeoutId !== undefined) {
        window.clearTimeout(timeoutId)
      }

      if (intervalId !== undefined) {
        window.clearInterval(intervalId)
      }
    }
  }, [])

  return timeLabel
}
