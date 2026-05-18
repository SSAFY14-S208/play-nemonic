'use client'

import { useMemo, useState } from 'react'
import { useRouter } from 'next/navigation'
import { Plus, FileSearch, Trash2 } from 'lucide-react'

import { useAdminAuthStore } from '@/shared/stores'
import type { AdminResponse } from '@/shared/types'

import {
  AdminCreateModal,
  AdminDetailModal,
  AdminRoleBadge,
  AuditLogSection,
  type AuditActorLookup,
} from './components'
import { useAdminAccounts } from './hooks'

export default function AdminBackofficeManagementPage() {
  const router = useRouter()
  const adminRole = useAdminAuthStore((state) => state.admin?.role ?? null)

  const {
    items,
    isLoading,
    loadError,
    isMutating,
    createAdmin,
    changePassword,
    removeAdmin,
  } = useAdminAccounts()

  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false)
  const [detailAdmin, setDetailAdmin] = useState<AdminResponse | null>(null)
  const [deleteConfirmId, setDeleteConfirmId] = useState<number | null>(null)

  // 감사 로그 metadata.actor_id(숫자 PK 문자열)를 닉네임으로 치환하기 위한 lookup.
  // 백엔드 AdminAuditLogger가 닉네임을 emit하지 않으므로 admin 목록 조회 결과를 재활용한다.
  const actorLookup = useMemo<AuditActorLookup>(() => {
    const map: Record<string, { nickname: string; loginId: string }> = {}
    for (const admin of items) {
      map[String(admin.id)] = {
        nickname: admin.nickname,
        loginId: admin.loginId,
      }
    }
    return map
  }, [items])

  // super_admin이 아니면 대시보드로 리다이렉트
  if (adminRole !== 'super_admin') {
    router.replace('/admin/dashboard')
    return null
  }

  const handleCreateSuccess = () => {
    setIsCreateModalOpen(false)
  }

  const handleDelete = (adminId: number) => {
    removeAdmin(adminId)
    setDeleteConfirmId(null)
  }

  return (
    <div className="flex flex-col gap-10">
      <section className="flex flex-col gap-4">
        <header className="flex items-center justify-between gap-3">
          <div className="flex flex-col gap-1">
            <h3 className="h4-b text-fg-primary">관리자 계정</h3>
            <p className="caption-r text-fg-secondary">
              슈퍼 관리자만 조회·변경할 수 있습니다.
            </p>
          </div>
          <button
            type="button"
            onClick={() => setIsCreateModalOpen(true)}
            disabled={isMutating}
            className="body-b inline-flex items-center gap-1.5 rounded-[var(--radius-md)] bg-primary-1 px-4 py-2 text-fg-inverse transition-opacity hover:opacity-90 disabled:opacity-50"
          >
            <Plus className="h-4 w-4" />
            계정 추가
          </button>
        </header>

        <div className="overflow-hidden rounded-[var(--radius-lg)] border border-border-default bg-surface-default">
          <table className="w-full">
            <thead>
              <tr className="border-b border-border-default bg-surface-subtle">
                <th className="caption-b w-16 px-4 py-3 text-left text-fg-secondary">
                  ID
                </th>
                <th className="caption-b px-4 py-3 text-left text-fg-secondary">
                  로그인 ID
                </th>
                <th className="caption-b px-4 py-3 text-left text-fg-secondary">
                  닉네임
                </th>
                <th className="caption-b px-4 py-3 text-left text-fg-secondary">
                  이메일
                </th>
                <th className="caption-b px-4 py-3 text-left text-fg-secondary">
                  역할
                </th>
                <th className="caption-b px-4 py-3 text-right text-fg-secondary">
                  액션
                </th>
              </tr>
            </thead>
            <tbody>
              {isLoading ? (
                <tr>
                  <td
                    colSpan={6}
                    className="body-r px-4 py-8 text-center text-fg-secondary"
                  >
                    불러오는 중…
                  </td>
                </tr>
              ) : loadError ? (
                <tr>
                  <td
                    colSpan={6}
                    className="body-r px-4 py-8 text-center text-red-500"
                    role="alert"
                  >
                    {loadError}
                  </td>
                </tr>
              ) : items.length === 0 ? (
                <tr>
                  <td
                    colSpan={6}
                    className="body-r px-4 py-8 text-center text-fg-secondary"
                  >
                    등록된 관리자 계정이 없습니다.
                  </td>
                </tr>
              ) : (
                items.map((admin) => (
                  <tr
                    key={admin.id}
                    className="border-b border-border-default last:border-b-0"
                  >
                    <td className="body-m px-4 py-3 text-fg-primary">
                      #{admin.id}
                    </td>
                    <td className="body-r px-4 py-3 text-fg-primary">
                      {admin.loginId}
                    </td>
                    <td className="body-r px-4 py-3 text-fg-primary">
                      {admin.nickname}
                    </td>
                    <td className="body-r px-4 py-3 text-fg-secondary">
                      {admin.email}
                    </td>
                    <td className="px-4 py-3">
                      <AdminRoleBadge role={admin.role} />
                    </td>
                    <td className="px-4 py-3 text-right">
                      <div className="inline-flex items-center gap-2">
                        <button
                          type="button"
                          onClick={() => setDetailAdmin(admin)}
                          disabled={isMutating}
                          className="caption-b inline-flex items-center gap-1 rounded-[var(--radius-md)] border border-border-default bg-surface-default px-3 py-1.5 text-fg-primary transition-colors hover:bg-surface-subtle disabled:opacity-50"
                        >
                          <FileSearch className="h-3.5 w-3.5" />
                          상세
                        </button>
                        {deleteConfirmId === admin.id ? (
                          <div className="inline-flex items-center gap-1">
                            <button
                              type="button"
                              onClick={() => handleDelete(admin.id)}
                              disabled={isMutating}
                              className="caption-b rounded-[var(--radius-md)] bg-red-500 px-3 py-1.5 text-fg-inverse transition-opacity hover:opacity-90 disabled:opacity-50"
                            >
                              확인
                            </button>
                            <button
                              type="button"
                              onClick={() => setDeleteConfirmId(null)}
                              disabled={isMutating}
                              className="caption-b rounded-[var(--radius-md)] border border-border-default px-3 py-1.5 text-fg-primary transition-colors hover:bg-surface-subtle disabled:opacity-50"
                            >
                              취소
                            </button>
                          </div>
                        ) : (
                          <button
                            type="button"
                            onClick={() => setDeleteConfirmId(admin.id)}
                            disabled={isMutating}
                            className="caption-b inline-flex items-center gap-1 rounded-[var(--radius-md)] border border-red-300 bg-surface-default px-3 py-1.5 text-red-500 transition-colors hover:bg-red-50 disabled:opacity-50"
                          >
                            <Trash2 className="h-3.5 w-3.5" />
                            삭제
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>

        <div className="flex items-center">
          <span className="caption-r text-fg-secondary">
            총 {items.length}개
          </span>
        </div>
      </section>

      <AuditLogSection actorLookup={actorLookup} />

      <AdminCreateModal
        open={isCreateModalOpen}
        isSubmitting={isMutating}
        onSubmit={(payload) => createAdmin(payload, handleCreateSuccess)}
        onClose={() => setIsCreateModalOpen(false)}
      />

      <AdminDetailModal
        open={detailAdmin !== null}
        admin={detailAdmin}
        isSubmitting={isMutating}
        onChangePassword={(adminId, payload, onSuccess) =>
          changePassword(adminId, payload, onSuccess)
        }
        onClose={() => setDetailAdmin(null)}
      />
    </div>
  )
}
