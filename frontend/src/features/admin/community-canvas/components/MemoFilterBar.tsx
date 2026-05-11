import { cn } from '@/shared/libs'

import type { MemoListFilter } from '../hooks'

interface MemoFilterBarProps {
  value: MemoListFilter
  onChange: (next: MemoListFilter) => void
  disabled?: boolean
}

const OPTIONS: Array<{ value: MemoListFilter; label: string }> = [
  { value: 'ALL', label: '전체' },
  { value: 'VISIBLE', label: '노출' },
  { value: 'HIDDEN', label: '숨김' },
  { value: 'REPORTED', label: '신고됨' },
]

export function MemoFilterBar({ value, onChange, disabled }: MemoFilterBarProps) {
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
