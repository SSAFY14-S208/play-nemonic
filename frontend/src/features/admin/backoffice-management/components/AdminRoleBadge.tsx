import { cn } from '@/shared/libs'
import type { AdminRole } from '@/shared/types'

interface AdminRoleBadgeProps {
  role: AdminRole
}

const ROLE_STYLES: Record<AdminRole, string> = {
  super_admin: 'bg-purple-100 text-purple-700',
  admin: 'bg-blue-100 text-blue-700',
  viewer: 'bg-surface-subtle text-fg-secondary',
}

const ROLE_LABELS: Record<AdminRole, string> = {
  super_admin: '슈퍼 관리자',
  admin: '관리자',
  viewer: '뷰어',
}

export function AdminRoleBadge({ role }: AdminRoleBadgeProps) {
  return (
    <span
      className={cn(
        'caption-b inline-block rounded-[var(--radius-sm)] px-2 py-0.5',
        ROLE_STYLES[role],
      )}
    >
      {ROLE_LABELS[role]}
    </span>
  )
}
