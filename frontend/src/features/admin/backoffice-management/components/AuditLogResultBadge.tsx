import { cn } from '@/shared/libs'

interface AuditLogResultBadgeProps {
  result: string | null
}

const RESULT_STYLES: Record<string, string> = {
  success: 'bg-green-100 text-green-700',
  failure: 'bg-red-100 text-red-700',
}

const RESULT_LABELS: Record<string, string> = {
  success: '성공',
  failure: '실패',
}

export function AuditLogResultBadge({ result }: AuditLogResultBadgeProps) {
  const key = result ?? ''
  return (
    <span
      className={cn(
        'caption-b inline-block rounded-[var(--radius-sm)] px-2 py-0.5',
        RESULT_STYLES[key] ?? 'bg-surface-subtle text-fg-secondary',
      )}
    >
      {RESULT_LABELS[key] ?? result ?? '—'}
    </span>
  )
}
