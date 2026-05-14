'use client'

import { useEffect, useRef, useState } from 'react'
import QRCode from 'qrcode'

export type RelayShareActionKey = 'copyLink' | 'copyRoomCode' | 'qrCode'

export function useRelayShareAction({
  actionKey,
  label,
  roomCode,
}: {
  actionKey: RelayShareActionKey
  label: string
  roomCode: string | null
}) {
  const [feedbackLabel, setFeedbackLabel] = useState(label)
  const [qrCodeDataUrl, setQrCodeDataUrl] = useState<string | null>(null)
  const resetLabelTimerRef = useRef<number | null>(null)

  useEffect(() => {
    return () => {
      if (resetLabelTimerRef.current !== null) {
        window.clearTimeout(resetLabelTimerRef.current)
      }
    }
  }, [])

  const closeQrCode = () => {
    setQrCodeDataUrl(null)
  }

  const runShareAction = () => {
    if (!roomCode || typeof window === 'undefined') return
    const shareUrl = createShareUrl(roomCode)

    void (async () => {
      try {
        if (actionKey === 'qrCode') {
          const nextQrCodeDataUrl = await QRCode.toDataURL(shareUrl, {
            margin: 2,
            scale: 8,
            color: {
              dark: '#5b3e2b',
              light: '#fffaf3',
            },
          })
          setQrCodeDataUrl(nextQrCodeDataUrl)
          return
        }

        const textToCopy = actionKey === 'copyRoomCode' ? roomCode : shareUrl
        await copyTextWithFallback(textToCopy)
        setFeedbackLabel('복사됨')

        if (resetLabelTimerRef.current !== null) {
          window.clearTimeout(resetLabelTimerRef.current)
        }

        resetLabelTimerRef.current = window.setTimeout(() => {
          setFeedbackLabel(label)
          resetLabelTimerRef.current = null
        }, 1400)
      } catch {
        setFeedbackLabel('복사 실패')
      }
    })()
  }

  return {
    feedbackLabel,
    qrCodeDataUrl,
    closeQrCode,
    runShareAction,
  }
}

function createShareUrl(roomCode: string) {
  const shareUrl = new URL(window.location.href)
  shareUrl.searchParams.set('roomCode', roomCode)
  shareUrl.hash = ''
  return shareUrl.toString()
}

async function copyTextWithFallback(text: string) {
  if (window.navigator.clipboard?.writeText) {
    try {
      await window.navigator.clipboard.writeText(text)
      return
    } catch {
      // Browser permission policies can block Clipboard API, so fall back to DOM copy.
    }
  }

  const textarea = document.createElement('textarea')
  textarea.value = text
  textarea.setAttribute('readonly', '')
  textarea.style.position = 'fixed'
  textarea.style.top = '-9999px'
  textarea.style.opacity = '0'
  document.body.appendChild(textarea)
  textarea.focus()
  textarea.select()
  textarea.setSelectionRange(0, text.length)
  const copied = document.execCommand('copy')
  document.body.removeChild(textarea)

  if (!copied) {
    throw new Error('클립보드 복사에 실패했습니다.')
  }
}
