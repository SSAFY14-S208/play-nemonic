'use client'

import { useState } from 'react'
import { Flag } from 'lucide-react'
import { cn } from '@/shared/libs'
import type { CommunityMemoReportReason } from '@/shared/types'
import { CommunityModalFrame } from './CommunityModalFrame'

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
    <CommunityModalFrame
      isOpen={isOpen}
      titleId="community-report-title"
      frameImageUrl="/images/community-canvas/ui/modal-report-frame.svg"
      aspectRatio={760 / 660}
      maxWidth="60rem"
      overlayClassName="z-[calc(var(--z-overlay)+10)] p-3"
      frameClassName="max-md:aspect-auto max-md:h-[calc(100dvh_-_1.5rem)]"
      contentClassName="bottom-[7%] left-[9%] right-[9%] top-[7.4%] max-md:inset-4 max-md:pt-12"
      closeButtonClassName="right-[7%] top-[3.8%] max-md:right-4 max-md:top-4"
      closeButtonLabel="신고 창 닫기"
      onClose={onClose}
    >
      <div className="flex h-full min-h-0 flex-col gap-3">
        <header className="shrink-0">
          <h2 id="community-report-title" className="h3-b text-fg-primary">
            메모 신고
          </h2>
        </header>

        <div className="min-h-0 flex-1 overflow-y-auto pr-1">
          <div className="grid grid-cols-2 gap-3 max-sm:grid-cols-1">
            {REPORT_REASONS.map((reportReason) => (
              <button
                key={reportReason}
                type="button"
                aria-pressed={reason === reportReason}
                onClick={() => setReason(reportReason)}
                className={cn(
                  'body-b flex h-12 items-center rounded-[0.45rem] border px-4 text-left transition',
                  reason === reportReason
                    ? 'border-[#FFD95D] bg-[#FFF8E1] text-primary-2'
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
            className="body-r mt-3 h-[clamp(7rem,28dvh,16rem)] w-full resize-none rounded-[0.45rem] border border-[#FFD66B]/70 bg-[#fffdf1] p-4 text-fg-primary outline-none focus:border-[#FFD95D]"
          />
        </div>

        <button
          type="button"
          onClick={() => onSubmit(reason, reasonDetail)}
          disabled={status === 'loading'}
          className="body-b inline-flex h-12 w-full shrink-0 items-center justify-center gap-2 rounded-[0.45rem] bg-[#FFD95D] text-fg-primary transition hover:-translate-y-0.5 disabled:cursor-not-allowed disabled:opacity-60"
        >
          <Flag className="size-4" />
          {status === 'loading' ? '접수 중이에요' : '신고 접수'}
        </button>
      </div>
    </CommunityModalFrame>
  )
}
