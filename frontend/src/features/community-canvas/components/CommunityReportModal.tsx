'use client'

import { useState } from 'react'
import { Flag, X } from 'lucide-react'
import { cn } from '@/shared/libs'
import type { CommunityMemoReportReason } from '@/shared/types'

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

  if (!isOpen) return null

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="community-report-title"
      className="fixed inset-0 z-[calc(var(--z-overlay)+10)] grid place-items-center bg-[#1f160d]/58 p-4 backdrop-blur-[2px]"
    >
      <section
        className="w-full max-w-md bg-contain bg-center bg-no-repeat px-10 pb-10 pt-9"
        style={{
          backgroundImage: 'url("/images/community-canvas/ui/modal-report-frame.svg")',
          backgroundSize: '100% 100%',
        }}
      >
        <header className="flex items-center justify-between px-3 py-3">
          <h2 id="community-report-title" className="h3-b text-fg-primary">
            메모 신고
          </h2>
          <button
            type="button"
            aria-label="신고 닫기"
            onClick={onClose}
            className="grid size-9 place-items-center rounded-full border border-[#e0b46f] bg-[#fff6df] text-fg-secondary shadow-[0_6px_14px_rgb(84_45_18_/_16%)]"
          >
            <X className="size-5" />
          </button>
        </header>

        <div className="mt-8 grid grid-cols-2 gap-2 px-2">
          {REPORT_REASONS.map((reportReason) => (
            <button
              key={reportReason}
              type="button"
              aria-pressed={reason === reportReason}
              onClick={() => setReason(reportReason)}
              className={cn(
                'caption-b rounded-[0.4rem] border px-3 py-2 text-left transition',
                reason === reportReason
                  ? 'border-primary-1 bg-primary-5 text-primary-2'
                  : 'border-border-default bg-white text-fg-secondary',
              )}
            >
              {reportReason}
            </button>
          ))}
        </div>

        <label className="body-b mt-4 block text-fg-primary" htmlFor="report-detail">
          상세 사유
        </label>
        <textarea
          id="report-detail"
          value={reasonDetail}
          onChange={(event) => setReasonDetail(event.target.value)}
          maxLength={300}
          className="body-r mt-2 min-h-24 w-full resize-none rounded-[0.45rem] border border-[#d9994a]/70 bg-[#fff8e8]/88 p-3 text-fg-primary outline-none focus:border-primary-1"
        />

        <button
          type="button"
          onClick={() => onSubmit(reason, reasonDetail)}
          disabled={status === 'loading'}
          className="body-b mt-4 inline-flex h-11 w-full items-center justify-center gap-2 rounded-[0.45rem] bg-fg-primary text-fg-inverse disabled:cursor-not-allowed disabled:opacity-60"
        >
          <Flag className="size-4" />
          {status === 'loading' ? '접수 중' : '신고 접수'}
        </button>
      </section>
    </div>
  )
}
