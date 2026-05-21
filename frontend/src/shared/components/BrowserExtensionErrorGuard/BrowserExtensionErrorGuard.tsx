'use client'

import { useEffect } from 'react'

function getErrorText(value: unknown) {
  if (value instanceof Error) return `${value.message}\n${value.stack ?? ''}`
  if (typeof value === 'string') return value
  return ''
}

function isMetaMaskExtensionError(text: string) {
  return text.includes('Failed to connect to MetaMask') && text.includes('chrome-extension://')
}

export default function BrowserExtensionErrorGuard() {
  useEffect(() => {
    const handleWindowError = (event: ErrorEvent) => {
      const errorText = `${event.message}\n${event.filename}\n${getErrorText(event.error)}`
      if (!isMetaMaskExtensionError(errorText)) return

      event.preventDefault()
      event.stopImmediatePropagation()
    }

    const handleUnhandledRejection = (event: PromiseRejectionEvent) => {
      const errorText = getErrorText(event.reason)
      if (!isMetaMaskExtensionError(errorText)) return

      event.preventDefault()
      event.stopImmediatePropagation()
    }

    window.addEventListener('error', handleWindowError, true)
    window.addEventListener('unhandledrejection', handleUnhandledRejection, true)

    return () => {
      window.removeEventListener('error', handleWindowError, true)
      window.removeEventListener('unhandledrejection', handleUnhandledRejection, true)
    }
  }, [])

  return null
}
