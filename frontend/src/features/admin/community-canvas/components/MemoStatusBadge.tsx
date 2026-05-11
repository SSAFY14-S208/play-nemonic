import { cn } from '@/shared/libs'
import type { AdminMemoModerationStatus } from '@/shared/types'

// 메모의 가시성 + moderation 상태를 한 줄로 표시.

interface MemoStatusBadgeProps {
  isHidden: boolean
  hiddenReason?: string | null
  moderationStatus: AdminMemoModerationStatus
}

const MODERATION_PRESET: Record<
  AdminMemoModerationStatus,
  { label: string; className: string }
> = {
  pending: { label: '검토 대기', className: 'bg-amber-100 text-amber-700' },
  allowed: { label: '허용', className: 'bg-emerald-100 text-emerald-700' },
  blocked: { label: '차단', className: 'bg-rose-100 text-rose-700' },
}

const HIDDEN_REASON_LABEL: Record<string, string> = {
  admin_hidden: '관리자 숨김',
  report_threshold: '신고 누적',
  ocr_blocked: 'OCR 차단',
}

export function MemoStatusBadge({
  isHidden,
  hiddenReason,
  moderationStatus,
}: MemoStatusBadgeProps) {
  const moderation = MODERATION_PRESET[moderationStatus]
  const hiddenLabel = hiddenReason
    ? (HIDDEN_REASON_LABEL[hiddenReason] ?? hiddenReason)
    : '숨김'

  return (
    <div className="flex flex-col items-start gap-1">
      <span
        className={cn(
          'caption-b inline-flex items-center gap-1 rounded-full px-2 py-0.5',
          isHidden
            ? 'bg-slate-200 text-slate-600'
            : 'bg-emerald-100 text-emerald-700',
        )}
      >
        <span className="h-1.5 w-1.5 rounded-full bg-current opacity-70" />
        {isHidden ? hiddenLabel : '노출'}
      </span>
      <span
        className={cn(
          'caption-r inline-flex items-center rounded-full px-2 py-0.5',
          moderation.className,
        )}
      >
        {moderation.label}
      </span>
    </div>
  )
}
