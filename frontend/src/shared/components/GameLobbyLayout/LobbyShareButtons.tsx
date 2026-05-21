'use client'

import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import Image from 'next/image'
import { Copy, Link2, QrCode } from 'lucide-react'
import QRCodeLib from 'qrcode'

import type { GameLobbyTheme } from './GameLobbyLayout.types'
import { createLobbyToast } from './lobbyToast'

interface LobbyShareButtonsProps {
  theme: GameLobbyTheme
  roomCode: string
}

type ShareActionKey = 'copyLink' | 'copyRoomCode' | 'qrCode'

interface ShareAction {
  key: ShareActionKey
  defaultLabel: string
  Icon: typeof Link2
}

const SHARE_ACTIONS: ShareAction[] = [
  { key: 'copyLink', defaultLabel: '링크 복사', Icon: Link2 },
  { key: 'copyRoomCode', defaultLabel: '코드 복사', Icon: Copy },
]

const QR_ACTION: ShareAction = {
  key: 'qrCode',
  defaultLabel: 'QR 코드',
  Icon: QrCode,
}

export function LobbyShareButtons({ theme, roomCode }: LobbyShareButtonsProps) {
  const [feedbackKey, setFeedbackKey] = useState<ShareActionKey | null>(null)
  const [qrCodeDataUrl, setQrCodeDataUrl] = useState<string | null>(null)
  const resetTimerRef = useRef<number | null>(null)

  useEffect(() => {
    return () => {
      if (resetTimerRef.current !== null) {
        window.clearTimeout(resetTimerRef.current)
      }
    }
  }, [])

  const runAction = (actionKey: ShareActionKey) => {
    if (!roomCode || typeof window === 'undefined') return
    // QR로 진입한 사용자는 카메라→브라우저로 점프해 document.referrer가 비어
    // detectEntryType이 direct로 떨어지는 문제가 있다. QR 코드에 들어가는 URL에만
    // ?qr=1 마커를 붙여 LogBootstrap이 entry_type='qr'로 분류할 수 있게 한다.
    // 링크 복사 흐름은 카톡/메신저 등 다양한 매체에서 사용되므로 referrer 기반
    // 분류를 유지한다.
    const shareUrl = createShareUrl(roomCode, { qr: actionKey === 'qrCode' })

    void (async () => {
      try {
        if (actionKey === 'qrCode') {
          const dataUrl = await QRCodeLib.toDataURL(shareUrl, {
            margin: 2,
            scale: 8,
            color: {
              dark: theme.qrDark ?? theme.ink,
              light: theme.qrLight ?? '#ffffff',
            },
          })
          setQrCodeDataUrl(dataUrl)
          return
        }

        const textToCopy =
          actionKey === 'copyRoomCode' ? roomCode : shareUrl
        await copyTextWithFallback(textToCopy)
        setFeedbackKey(actionKey)

        const lobbyToast = createLobbyToast(theme)
        const toastMessage =
          actionKey === 'copyRoomCode'
            ? `입장 코드 ${roomCode}를 복사했어요`
            : '초대 링크를 복사했어요'
        lobbyToast.success(toastMessage, { position: 'bottom-center' })

        if (resetTimerRef.current !== null) {
          window.clearTimeout(resetTimerRef.current)
        }
        resetTimerRef.current = window.setTimeout(() => {
          setFeedbackKey(null)
          resetTimerRef.current = null
        }, 1400)
      } catch {
        // 복사 실패 시 무시
      }
    })()
  }

  const buttonStyle = {
    borderColor: theme.line,
    backgroundColor: theme.active ?? theme.paper,
    color: theme.accentStrong ?? theme.accent,
  }

  return (
    <>
      <div className="mt-5 grid grid-cols-2 gap-3">
        {SHARE_ACTIONS.map((action) => (
          <button
            key={action.key}
            type="button"
            onClick={() => runAction(action.key)}
            className="body-b inline-flex min-h-11 cursor-pointer items-center justify-center gap-1.5 rounded-full border px-3.5 transition-all hover:-translate-y-0.5 hover:brightness-95 disabled:cursor-not-allowed disabled:opacity-55 disabled:hover:translate-y-0 disabled:hover:brightness-100"
            style={buttonStyle}
          >
            <action.Icon className="size-[17px]" aria-hidden />
            {feedbackKey === action.key ? '복사됨' : action.defaultLabel}
          </button>
        ))}
      </div>
      <div className="mt-3">
        <button
          type="button"
          onClick={() => runAction('qrCode')}
          className="body-b inline-flex min-h-11 w-full cursor-pointer items-center justify-center gap-1.5 rounded-full border px-3.5 transition-all hover:-translate-y-0.5 hover:brightness-95"
          style={buttonStyle}
        >
          <QR_ACTION.Icon className="size-[17px]" aria-hidden />
          {QR_ACTION.defaultLabel}
        </button>
      </div>

      {qrCodeDataUrl &&
        createPortal(
          <div
            className="fixed inset-0 z-50 grid place-items-center px-5 backdrop-blur-[3px]"
            style={{ backgroundColor: `color-mix(in srgb, ${theme.ink} 30%, transparent)` }}
            role="presentation"
            onMouseDown={(event) => {
              if (event.target === event.currentTarget) {
                setQrCodeDataUrl(null)
              }
            }}
          >
            <div
              className="w-full max-w-[360px] rounded-[34px] border p-7 text-center shadow-[0_24px_60px_rgba(0,0,0,0.24)]"
              style={{
                borderColor: theme.line,
                backgroundColor: theme.paper,
                color: theme.ink,
              }}
            >
              <p className="h3-b">QR 코드</p>
              <Image
                src={qrCodeDataUrl}
                alt="초대 QR 코드"
                width={256}
                height={256}
                unoptimized
                className="mx-auto mt-5 rounded-[18px] border bg-white p-3"
                style={{ borderColor: theme.line }}
              />
              <p className="caption-m mt-4" style={{ color: theme.muted }}>
                친구가 스캔하면 바로 입장할 수 있어요.
              </p>
              <button
                type="button"
                onClick={() => setQrCodeDataUrl(null)}
                className="body-b mt-6 h-12 w-full cursor-pointer rounded-full shadow-[0_8px_18px_rgba(0,0,0,0.18)]"
                style={{ backgroundColor: theme.accent, color: theme.ink }}
              >
                닫기
              </button>
            </div>
          </div>,
          document.body,
        )}
    </>
  )
}

function createShareUrl(
  roomCode: string,
  options: { qr?: boolean } = {},
): string {
  const shareUrl = new URL(window.location.href)
  shareUrl.searchParams.set('roomCode', roomCode)
  if (options.qr) {
    shareUrl.searchParams.set('qr', '1')
  }
  shareUrl.hash = ''
  return shareUrl.toString()
}

async function copyTextWithFallback(text: string) {
  if (window.navigator.clipboard?.writeText) {
    try {
      await window.navigator.clipboard.writeText(text)
      return
    } catch {
      // Clipboard API 차단 시 DOM fallback
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
