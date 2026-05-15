'use client'

import { useState } from 'react'
import { ExternalLink } from 'lucide-react'

import { runtime } from '@/shared/config'
import { cn } from '@/shared/libs'

type IframeStatus = 'loading' | 'loaded' | 'error'

// 특정 대시보드 직접 진입 + kiosk 모드 (상단 nav·사이드바 제거)
const EMBED_URL = `${runtime.grafanaUrl}d/nemonic-overview?kiosk`
// 새 탭에서 열 때는 kiosk 없이 전체 UI 제공
const NEW_TAB_URL = `${runtime.grafanaUrl}d/nemonic-overview`

export default function AdminDashboardPage() {
  const [iframeStatus, setIframeStatus] = useState<IframeStatus>('loading')

  return (
    // -mx-8 -my-6: 부모 컨테이너(px-8 py-6) 패딩 상쇄
    // h-[calc(100%+3rem)]: py-6(1.5rem) × 2 = 3rem 보정
    <div className="-mx-8 -my-6 flex h-[calc(100%+3rem)] flex-col">
      <div className="flex shrink-0 items-center justify-between border-b border-border-default px-6 py-3">
        <p className="caption-r text-fg-secondary">
          화면이 표시되지 않으면 새 탭에서 먼저 열어 인증해 주세요.
        </p>
        <a
          href={NEW_TAB_URL}
          target="_blank"
          rel="noopener noreferrer"
          className="caption-b inline-flex items-center gap-1 rounded-[var(--radius-md)] border border-border-default px-3 py-1.5 text-fg-primary transition-colors hover:bg-surface-subtle"
        >
          <ExternalLink className="h-3.5 w-3.5" />
          새 탭에서 열기
        </a>
      </div>

      <div className="relative flex-1">
        {iframeStatus !== 'loaded' && (
          <div className="absolute inset-0 flex flex-col items-center justify-center gap-4 bg-surface-default">
            {iframeStatus === 'loading' && (
              <p className="body-r text-fg-secondary">대시보드를 불러오는 중…</p>
            )}
            {iframeStatus === 'error' && (
              <>
                <p className="body-r text-fg-secondary">
                  대시보드를 불러오지 못했습니다.
                </p>
                <a
                  href={NEW_TAB_URL}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="body-b inline-flex items-center gap-1.5 rounded-[var(--radius-md)] bg-primary-1 px-4 py-2 text-fg-inverse transition-opacity hover:opacity-90"
                >
                  <ExternalLink className="h-4 w-4" />
                  새 탭에서 열기
                </a>
              </>
            )}
          </div>
        )}
        <iframe
          src={EMBED_URL}
          title="Grafana 대시보드"
          className={cn('h-full w-full border-0', iframeStatus !== 'loaded' && 'invisible')}
          onLoad={() => setIframeStatus('loaded')}
          onError={() => setIframeStatus('error')}
        />
      </div>
    </div>
  )
}
