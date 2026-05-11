import { ChevronLeft, ChevronRight } from 'lucide-react'

import { cn } from '@/shared/libs'

interface RoomPaginationProps {
  /** 0-based 현재 페이지 */
  page: number
  totalPages: number
  onChange: (next: number) => void
  disabled?: boolean
}

// 표시 윈도우: 현재 페이지 기준 좌우 2개씩, 총 5개까지 노출. 양 끝은 ellipsis 처리.
const WINDOW_RADIUS = 2

function buildPageWindow(current: number, total: number): number[] {
  const start = Math.max(0, current - WINDOW_RADIUS)
  const end = Math.min(total - 1, current + WINDOW_RADIUS)
  const pages: number[] = []
  for (let pageIndex = start; pageIndex <= end; pageIndex++) {
    pages.push(pageIndex)
  }
  return pages
}

export function RoomPagination({
  page,
  totalPages,
  onChange,
  disabled,
}: RoomPaginationProps) {
  if (totalPages <= 1) return null
  const pages = buildPageWindow(page, totalPages)
  const canPrev = page > 0
  const canNext = page < totalPages - 1

  return (
    <nav className="flex items-center justify-center gap-1" aria-label="페이지 이동">
      <button
        type="button"
        onClick={() => onChange(page - 1)}
        disabled={disabled || !canPrev}
        className="flex h-8 w-8 items-center justify-center rounded-[var(--radius-md)] text-fg-secondary transition-colors hover:bg-surface-subtle disabled:opacity-30"
        aria-label="이전 페이지"
      >
        <ChevronLeft className="h-4 w-4" />
      </button>
      {pages.map((pageIndex) => (
        <button
          key={pageIndex}
          type="button"
          onClick={() => onChange(pageIndex)}
          disabled={disabled}
          className={cn(
            'caption-b flex h-8 min-w-8 items-center justify-center rounded-[var(--radius-md)] px-2 transition-colors disabled:opacity-50',
            pageIndex === page
              ? 'bg-primary-1 text-fg-inverse'
              : 'text-fg-secondary hover:bg-surface-subtle',
          )}
          aria-current={pageIndex === page ? 'page' : undefined}
        >
          {pageIndex + 1}
        </button>
      ))}
      <button
        type="button"
        onClick={() => onChange(page + 1)}
        disabled={disabled || !canNext}
        className="flex h-8 w-8 items-center justify-center rounded-[var(--radius-md)] text-fg-secondary transition-colors hover:bg-surface-subtle disabled:opacity-30"
        aria-label="다음 페이지"
      >
        <ChevronRight className="h-4 w-4" />
      </button>
    </nav>
  )
}
