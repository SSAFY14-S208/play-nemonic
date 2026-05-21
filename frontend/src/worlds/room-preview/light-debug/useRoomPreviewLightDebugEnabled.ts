'use client'

import { useEffect, useState } from 'react'

export const useRoomPreviewLightDebugEnabled = () => {
  const [isEnabled, setIsEnabled] = useState(false)

  useEffect(() => {
    let isCancelled = false

    const syncLightDebugParam = async () => {
      const searchParams = new URLSearchParams(window.location.search)
      const nextIsEnabled = searchParams.get('lightDebug') === '1'

      if (!isCancelled) {
        setIsEnabled(nextIsEnabled)
      }
    }

    void syncLightDebugParam()

    const onHistoryChange = () => {
      void syncLightDebugParam()
    }

    window.addEventListener('popstate', onHistoryChange)

    return () => {
      isCancelled = true
      window.removeEventListener('popstate', onHistoryChange)
    }
  }, [])

  return isEnabled
}
