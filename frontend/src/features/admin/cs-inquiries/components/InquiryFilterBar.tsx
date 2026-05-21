import { cn } from '@/shared/libs'

import type { InquiryStatusFilter, InquiryTypeFilter } from '../hooks'

interface InquiryFilterBarProps {
  statusValue: InquiryStatusFilter
  typeValue: InquiryTypeFilter
  onStatusChange: (next: InquiryStatusFilter) => void
  onTypeChange: (next: InquiryTypeFilter) => void
  disabled?: boolean
}

const STATUS_OPTIONS: Array<{ value: InquiryStatusFilter; label: string }> = [
  { value: 'ALL', label: '전체' },
  { value: 'new', label: '신규' },
  { value: 'in_progress', label: '처리중' },
  { value: 'resolved', label: '완료' },
  { value: 'closed', label: '종료' },
]

const TYPE_OPTIONS: Array<{ value: InquiryTypeFilter; label: string }> = [
  { value: 'ALL', label: '전체' },
  { value: 'error', label: '오류/버그' },
  { value: 'feature_request', label: '기능 제안' },
  { value: 'content_report', label: '콘텐츠 신고' },
  { value: 'other', label: '기타' },
]

export function InquiryFilterBar({
  statusValue,
  typeValue,
  onStatusChange,
  onTypeChange,
  disabled,
}: InquiryFilterBarProps) {
  return (
    <div className="flex flex-col gap-2">
      <div className="flex flex-wrap items-center gap-1">
        <span className="caption-b mr-1 text-fg-secondary">상태</span>
        {STATUS_OPTIONS.map((option) => (
          <button
            key={option.value}
            type="button"
            onClick={() => onStatusChange(option.value)}
            disabled={disabled}
            className={cn(
              'caption-b flex items-center gap-1.5 rounded-full px-3 py-1.5 transition-colors disabled:opacity-50',
              statusValue === option.value
                ? 'bg-primary-1 text-fg-inverse'
                : 'bg-surface-subtle text-fg-secondary hover:bg-primary-5',
            )}
          >
            {option.value !== 'ALL' && (
              <span className="h-1.5 w-1.5 rounded-full bg-current" />
            )}
            {option.label}
          </button>
        ))}
      </div>
      <div className="flex flex-wrap items-center gap-1">
        <span className="caption-b mr-1 text-fg-secondary">유형</span>
        {TYPE_OPTIONS.map((option) => (
          <button
            key={option.value}
            type="button"
            onClick={() => onTypeChange(option.value)}
            disabled={disabled}
            className={cn(
              'caption-b flex items-center gap-1.5 rounded-full px-3 py-1.5 transition-colors disabled:opacity-50',
              typeValue === option.value
                ? 'bg-primary-1 text-fg-inverse'
                : 'bg-surface-subtle text-fg-secondary hover:bg-primary-5',
            )}
          >
            {option.label}
          </button>
        ))}
      </div>
    </div>
  )
}
