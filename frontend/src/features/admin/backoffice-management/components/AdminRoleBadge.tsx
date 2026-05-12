import { cn } from '@/shared/libs'

interface AdminRoleBadgeProps {
  role: string
}

const ROLE_STYLES: Record<string, string> = {
  super_admin: 'bg-purple-100 text-purple-700',
  admin: 'bg-blue-100 text-blue-700',
}

const ROLE_LABELS: Record<string, string> = {
  super_admin: '슈퍼 관리자',
  admin: '관리자',
}

export function AdminRoleBadge({ role }: AdminRoleBadgeProps) {
  return (
    <span
      className={cn(
        'caption-b inline-block rounded-[var(--radius-sm)] px-2 py-0.5',
        ROLE_STYLES[role] ?? 'bg-surface-subtle text-fg-secondary',
      )}
    >
      {ROLE_LABELS[role] ?? role}
    </span>
  )
}
