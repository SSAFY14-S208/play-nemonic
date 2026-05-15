'use client'

import Image from 'next/image'
import { useState } from 'react'
import { Copy, X } from 'lucide-react'
import { cn } from '@/shared/libs'
import type { ShareCreateResponse } from '@/shared/types'

interface ShareSheetProps {
  shareInfo: ShareCreateResponse
  onClose: () => void
}

type CopyFeedback = { tone: 'success' | 'error'; message: string } | null

export function ShareSheet({ shareInfo, onClose }: ShareSheetProps) {
  const [copyFeedback, setCopyFeedback] = useState<CopyFeedback>(null)

  const handleCopy = async () => {
    const targetUrl = shareInfo.kakaoUrl || shareInfo.siteUrl
    try {
      if (!navigator.clipboard) throw new Error('clipboard unavailable')
      await navigator.clipboard.writeText(targetUrl)
      setCopyFeedback({ tone: 'success', message: '링크를 복사했어요.' })
    } catch {
      setCopyFeedback({
        tone: 'error',
        message: '링크를 복사하지 못했어요.',
      })
    }
    window.setTimeout(() => setCopyFeedback(null), 2000)
  }

  return (
    <div className="absolute inset-0 z-40 flex items-end">
      <button
        type="button"
        aria-label="공유 시트 닫기"
        onClick={onClose}
        className="absolute inset-0 bg-black/40 backdrop-blur-[1px]"
      />
      <section className="relative w-full rounded-t-[var(--radius-xl)] border-t border-border-default bg-surface-default px-5 pb-7 pt-3 shadow-lg">
        <div className="mx-auto mb-4 h-1 w-10 rounded-full bg-border-default" />
        <header className="mb-4 flex items-center justify-between">
          <h3 className="h3-b text-fg-primary">공유하기</h3>
          <button
            type="button"
            aria-label="닫기"
            onClick={onClose}
            className="grid size-9 place-items-center rounded-full bg-surface-subtle text-fg-secondary transition hover:text-fg-primary"
          >
            <X className="size-5" />
          </button>
        </header>

        <div className="relative mb-5 aspect-square overflow-hidden rounded-[var(--radius-md)] bg-surface-subtle">
          <Image
            src={shareInfo.imageUrl}
            alt="공유할 산출물 미리보기"
            fill
            unoptimized
            sizes="100vw"
            className="object-contain"
          />
        </div>

        <button
          type="button"
          onClick={() => void handleCopy()}
          className="body-l-b flex h-12 w-full items-center justify-center gap-2 rounded-[var(--radius-md)] bg-primary-1 text-fg-inverse transition hover:-translate-y-0.5"
        >
          <Copy className="size-5" />
          링크 복사
        </button>

        {copyFeedback && (
          <p
            role="status"
            aria-live="polite"
            className={cn(
              'caption-r mt-3 text-center',
              copyFeedback.tone === 'success'
                ? 'text-fg-secondary'
                : 'text-red-500',
            )}
          >
            {copyFeedback.message}
          </p>
        )}
      </section>
    </div>
  )
}
