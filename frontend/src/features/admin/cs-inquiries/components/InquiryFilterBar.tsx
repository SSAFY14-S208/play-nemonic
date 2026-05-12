import { cn } from '@/shared/libs'

import type { InquiryStatusFilter } from '../hooks'

interface InquiryFilterBarProps {
  value: InquiryStatusFilter
  onChange: (next: InquiryStatusFilter) => void
  disabled?: boolean
}

const OPTIONS: Array<{ value: InquiryStatusFilter; label: string }> = [
  { value: 'ALL', label: '전체' },
  { value: 'new', label: '신규' },
  { value: 'in_progress', label: '처리중' },
  { value: 'resolved', label: '완료' },
  { value: 'closed', label: '종료' },
]

export function InquiryFilterBar({
  value,
  onChange,
  disabled,
}: InquiryFilterBarProps) {
  return (
    <div className="flex flex-wrap items-center gap-1">
      {OPTIONS.map((option) => (
        <button
          key={option.value}
          type="button"
          onClick={() => onChange(option.value)}
          disabled={disabled}
          className={cn(
            'caption-b flex items-center gap-1.5 rounded-full px-3 py-1.5 transition-colors disabled:opacity-50',
            value === option.value
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
  )
}
