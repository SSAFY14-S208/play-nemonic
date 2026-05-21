import type { AdminRole } from '@/shared/types'

export function isSuperAdminRole(
  role: AdminRole | string | null | undefined,
): boolean {
  return role === 'super_admin'
}

export function isViewerAdminRole(
  role: AdminRole | string | null | undefined,
): boolean {
  return role === 'viewer'
}

export function canMutateBackoffice(
  role: AdminRole | string | null | undefined,
): boolean {
  return role === 'super_admin' || role === 'admin'
}
