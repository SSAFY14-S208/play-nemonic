import { cn } from '@/shared/libs'
import type { AdminInquiryStatus } from '@/shared/types'

interface InquiryStatusBadgeProps {
  status: AdminInquiryStatus
}

const STATUS_PRESET: Record<
  AdminInquiryStatus,
  { label: string; className: string }
> = {
  new: { label: '신규', className: 'bg-amber-100 text-amber-700' },
  in_progress: { label: '처리중', className: 'bg-sky-100 text-sky-700' },
  resolved: { label: '완료', className: 'bg-emerald-100 text-emerald-700' },
  closed: { label: '종료', className: 'bg-slate-200 text-slate-600' },
}

export function InquiryStatusBadge({ status }: InquiryStatusBadgeProps) {
  const preset = STATUS_PRESET[status]
  return (
    <span
      className={cn(
        'caption-b inline-flex items-center gap-1 rounded-full px-2 py-0.5',
        preset.className,
      )}
    >
      <span className="h-1.5 w-1.5 rounded-full bg-current opacity-70" />
      {preset.label}
    </span>
  )
}
