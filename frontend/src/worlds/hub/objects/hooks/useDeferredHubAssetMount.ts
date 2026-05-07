import { useEffect, useState } from 'react'

export function useDeferredHubAssetMount(delayMs: number) {
  const [canMountAsset, setCanMountAsset] = useState(delayMs <= 0)

  useEffect(() => {
    const timeoutId = window.setTimeout(() => {
      setCanMountAsset(true)
    }, Math.max(delayMs, 0))

    return () => {
      window.clearTimeout(timeoutId)
    }
  }, [delayMs])

  return canMountAsset
}
