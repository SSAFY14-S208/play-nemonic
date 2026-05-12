import { cn } from '@/shared/libs'

// 백오피스 활성 방 status 필터. 도메인별 허용 status가 달라 호출 측이 옵션 배열을 넘긴다.

export interface RoomFilterOption<T extends string> {
  value: T | 'ALL'
  label: string
}

interface RoomFilterBarProps<T extends string> {
  value: T | 'ALL'
  onChange: (next: T | 'ALL') => void
  options: RoomFilterOption<T>[]
  disabled?: boolean
}

export function RoomFilterBar<T extends string>({
  value,
  onChange,
  options,
  disabled,
}: RoomFilterBarProps<T>) {
  return (
    <div className="flex flex-wrap items-center gap-1">
      {options.map((option) => (
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
