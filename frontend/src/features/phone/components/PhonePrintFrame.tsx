'use client'

import Image from 'next/image'
import { useEffect, useState } from 'react'
import { createPortal } from 'react-dom'

interface PhonePrintFrameProps {
  imageUrl: string | null
  title: string
}

const PHONE_PRINT_STYLES = `
.phone-print-frame {
  clip-path: inset(50%);
  height: 1px;
  left: 0;
  opacity: 0;
  overflow: hidden;
  pointer-events: none;
  position: fixed;
  top: 0;
  width: 1px;
}

.phone-print-page {
  background: #ffffff;
  height: 100%;
  position: relative;
  width: 100%;
}

.phone-print-image {
  object-fit: contain;
}

@media print {
  @page {
    margin: 0;
    size: auto;
  }

  html,
  body {
    background: #ffffff !important;
    min-height: 100% !important;
    width: 100% !important;
  }

  body * {
    visibility: hidden !important;
  }

  .phone-print-frame,
  .phone-print-frame * {
    visibility: visible !important;
  }

  .phone-print-frame {
    align-items: center !important;
    background: #ffffff !important;
    clip-path: none !important;
    display: flex !important;
    height: 100vh !important;
    inset: 0 !important;
    justify-content: center !important;
    opacity: 1 !important;
    overflow: visible !important;
    position: fixed !important;
    width: 100vw !important;
    z-index: 2147483647 !important;
  }

  .phone-print-page {
    background: #ffffff !important;
    height: 100vh !important;
    position: relative !important;
    width: 100vw !important;
  }
}
`

export function PhonePrintFrame({ imageUrl, title }: PhonePrintFrameProps) {
  const [portalRoot, setPortalRoot] = useState<HTMLElement | null>(null)

  useEffect(() => {
    let isCancelled = false

    ;(async () => {
      await Promise.resolve()

      if (!isCancelled) {
        setPortalRoot(document.body)
      }
    })()

    return () => {
      isCancelled = true
    }
  }, [])

  if (!imageUrl || !portalRoot) return null

  return createPortal(
    <>
      <style>{PHONE_PRINT_STYLES}</style>
      <div className="phone-print-frame" aria-hidden>
        <div className="phone-print-page">
          <Image
            src={imageUrl}
            alt={`${title} 네모닉 출력 이미지`}
            fill
            priority
            unoptimized
            sizes="100vw"
            className="phone-print-image"
          />
        </div>
      </div>
    </>,
    portalRoot,
  )
}
