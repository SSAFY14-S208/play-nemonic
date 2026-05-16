'use client'

import { useState } from 'react'
import { Flag, X } from 'lucide-react'
import { cn } from '@/shared/libs'
import type { CommunityMemoReportReason } from '@/shared/types'
import {
  useCommunityCompactViewport,
  useCommunityModalFitScale,
} from './useCommunityModalFitScale'

const REPORT_MODAL_WIDTH = 760
const REPORT_MODAL_HEIGHT = 660
const REPORT_MODAL_MAX_WIDTH = 960

const REPORT_REASONS: CommunityMemoReportReason[] = [
  '부적절한 콘텐츠',
  '욕설/비방/혐오',
  '선정적/음란물',
  '폭력적/위협적 표현',
  '스팸/광고',
  '개인정보 노출',
  '도용/사칭',
  '기타',
]

interface CommunityReportModalProps {
  isOpen: boolean
  status: 'idle' | 'loading' | 'success' | 'error'
  onClose: () => void
  onSubmit: (reason: CommunityMemoReportReason, reasonDetail: string) => void
}

export function CommunityReportModal({
  isOpen,
  status,
  onClose,
  onSubmit,
}: CommunityReportModalProps) {
  const [reason, setReason] = useState<CommunityMemoReportReason>('부적절한 콘텐츠')
  const [reasonDetail, setReasonDetail] = useState('')
  const isCompactViewport = useCommunityCompactViewport()
  const modalScale = useCommunityModalFitScale({
    designWidth: REPORT_MODAL_WIDTH,
    designHeight: REPORT_MODAL_HEIGHT,
    maxWidth: REPORT_MODAL_MAX_WIDTH,
    viewportPadding: 16,
  })

  if (!isOpen) return null

  if (isCompactViewport) {
    return (
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="community-report-title"
        className="fixed inset-0 z-[calc(var(--z-overlay)+10)] overflow-y-auto overflow-x-hidden bg-[#19172a]/50 p-3 backdrop-blur-[2px]"
      >
        <section className="mx-auto flex min-h-full w-full max-w-[42rem] flex-col gap-4 rounded-[1.5rem] border-[0.35rem] border-[#b9b1ce] bg-[#eeeaf7] p-4 shadow-[0_18px_38px_rgb(25_20_40_/_24%)]">
          <header className="flex items-center justify-between gap-3 rounded-[1rem] border border-[#d5cee3] bg-[#fbfaff] p-4">
            <h2 id="community-report-title" className="h3-b text-fg-primary">
              메모 신고
            </h2>
            <button
              type="button"
              aria-label="신고 닫기"
              onClick={onClose}
              className="grid size-10 shrink-0 place-items-center rounded-full border border-[#b9b1ce] bg-[#fbfaff] text-fg-secondary shadow-[0_7px_16px_rgb(71_68_112_/_16%)]"
            >
              <X className="size-4" />
            </button>
          </header>

          <div className="grid gap-3 sm:grid-cols-2">
            {REPORT_REASONS.map((reportReason) => (
              <button
                key={reportReason}
                type="button"
                aria-pressed={reason === reportReason}
                onClick={() => setReason(reportReason)}
                className={cn(
                  'body-b flex min-h-12 items-center rounded-[0.45rem] border px-4 py-3 text-left transition',
                  reason === reportReason
                    ? 'border-[#b9b1ce] bg-[#f0ecfa] text-primary-2'
                    : 'border-border-default bg-white text-fg-secondary',
                )}
              >
                {reportReason}
              </button>
            ))}
          </div>

          <label className="body-b block text-fg-primary" htmlFor="report-detail-compact">
            상세 사유
          </label>
          <textarea
            id="report-detail-compact"
            value={reasonDetail}
            onChange={(event) => setReasonDetail(event.target.value)}
            maxLength={300}
            className="body-r min-h-44 resize-none rounded-[0.45rem] border border-[#c8c0d8]/70 bg-[#fbfaff] p-4 text-fg-primary outline-none focus:border-[#b9b1ce]"
          />

          <button
            type="button"
            onClick={() => onSubmit(reason, reasonDetail)}
            disabled={status === 'loading'}
            className="body-b sticky bottom-3 inline-flex h-12 w-full items-center justify-center gap-2 rounded-[0.45rem] bg-[#d9d2ea] text-fg-primary shadow-[0_8px_18px_rgb(73_55_93_/_14%)] transition disabled:cursor-not-allowed disabled:opacity-60"
          >
            <Flag className="size-4" />
            {status === 'loading' ? '접수 중' : '신고 접수'}
          </button>
        </section>
      </div>
    )
  }

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="community-report-title"
      className="fixed inset-0 z-[calc(var(--z-overlay)+10)] grid place-items-center overflow-y-auto overflow-x-hidden bg-[#19172a]/50 p-2 backdrop-blur-[2px]"
    >
      <div
        className="relative shrink-0"
        style={{
          width: REPORT_MODAL_WIDTH * modalScale,
          height: REPORT_MODAL_HEIGHT * modalScale,
        }}
      >
        <section
          className="absolute left-0 top-0 bg-contain bg-center bg-no-repeat"
          style={{
            width: REPORT_MODAL_WIDTH,
            height: REPORT_MODAL_HEIGHT,
            transform: `scale(${modalScale})`,
            transformOrigin: 'top left',
            backgroundImage: 'url("/images/community-canvas/ui/modal-report-frame.svg")',
            backgroundSize: '100% 100%',
          }}
        >
        <header className="absolute left-[9.8%] right-[18%] top-[7%] flex h-[7.4%] items-center">
          <h2 id="community-report-title" className="h3-b text-fg-primary">
            메모 신고
          </h2>
        </header>

        <button
          type="button"
          aria-label="신고 닫기"
          onClick={onClose}
          className="absolute right-[5.3%] top-[5.3%] grid size-9 place-items-center rounded-full border border-[#b9b1ce] bg-[#fbfaff] text-fg-secondary shadow-[0_6px_14px_rgb(71_68_112_/_16%)] transition hover:-translate-y-0.5 hover:bg-white"
        >
          <X className="size-4" />
        </button>

        <div className="absolute bottom-[8.8%] left-[9.8%] right-[9.8%] top-[14.3%] flex flex-col">
          <div className="grid grid-cols-2 gap-x-3 gap-y-2.5">
            {REPORT_REASONS.map((reportReason) => (
              <button
                key={reportReason}
                type="button"
                aria-pressed={reason === reportReason}
                onClick={() => setReason(reportReason)}
                className={cn(
                  'body-b flex h-11 items-center rounded-[0.45rem] border px-4 text-left transition',
                  reason === reportReason
                    ? 'border-[#b9b1ce] bg-[#f0ecfa] text-primary-2'
                    : 'border-border-default bg-white text-fg-secondary',
                )}
              >
                {reportReason}
              </button>
            ))}
          </div>

          <label className="body-b mt-5 block text-fg-primary" htmlFor="report-detail">
            상세 사유
          </label>
          <textarea
            id="report-detail"
            value={reasonDetail}
            onChange={(event) => setReasonDetail(event.target.value)}
            maxLength={300}
            className="body-r mt-2.5 min-h-0 flex-1 resize-none rounded-[0.45rem] border border-[#c8c0d8]/70 bg-[#fbfaff] p-4 text-fg-primary outline-none focus:border-[#b9b1ce]"
          />

          <button
            type="button"
            onClick={() => onSubmit(reason, reasonDetail)}
            disabled={status === 'loading'}
            className="body-b mt-5 inline-flex h-12 w-full items-center justify-center gap-2 rounded-[0.45rem] bg-[#d9d2ea] text-fg-primary transition hover:-translate-y-0.5 disabled:cursor-not-allowed disabled:opacity-60"
          >
            <Flag className="size-4" />
            {status === 'loading' ? '접수 중' : '신고 접수'}
          </button>
        </div>
        </section>
      </div>
    </div>
  )
}
