'use client'

import { useEffect, useState } from 'react'
import { X } from 'lucide-react'

import type { AdminPasswordChangeRequest, AdminResponse } from '@/shared/types'

import { AdminRoleBadge } from './AdminRoleBadge'

interface AdminDetailModalProps {
  open: boolean
  admin: AdminResponse | null
  isSubmitting: boolean
  onChangePassword: (
    adminId: number,
    payload: AdminPasswordChangeRequest,
    onSuccess: () => void,
  ) => void
  onClose: () => void
}

export function AdminDetailModal({
  open,
  admin,
  isSubmitting,
  onChangePassword,
  onClose,
}: AdminDetailModalProps) {
  const [isPasswordMode, setIsPasswordMode] = useState(false)
  const [newPassword, setNewPassword] = useState('')

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      if (!cancelled && !open) {
        setIsPasswordMode(false)
        setNewPassword('')
      }
    })()
    return () => {
      cancelled = true
    }
  }, [open])

  if (!open || !admin) return null

  const handlePasswordSubmit = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!newPassword.trim() || isSubmitting) return
    onChangePassword(admin.id, { password: newPassword.trim() }, () => {
      setIsPasswordMode(false)
      setNewPassword('')
    })
  }

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="detail-admin-title"
      className="fixed inset-0 z-[var(--z-modal)] flex items-center justify-center bg-black/40 backdrop-blur-sm"
    >
      <div className="flex w-full max-w-md flex-col overflow-hidden rounded-[var(--radius-xl)] bg-surface-default shadow-lg">
        <header className="flex items-center justify-between gap-3 border-b border-border-default px-6 py-4">
          <h2 id="detail-admin-title" className="h4-b text-fg-primary">
            관리자 상세
          </h2>
          <button
            type="button"
            onClick={onClose}
            disabled={isSubmitting}
            className="rounded-[var(--radius-md)] p-1 text-fg-secondary transition-colors hover:bg-surface-subtle"
            aria-label="닫기"
          >
            <X className="h-5 w-5" />
          </button>
        </header>

        <div className="flex flex-col gap-4 px-6 py-5">
          <div className="flex flex-col gap-3">
            <div className="flex items-center justify-between">
              <span className="caption-b text-fg-secondary">ID</span>
              <span className="body-r text-fg-primary">#{admin.id}</span>
            </div>
            <div className="flex items-center justify-between">
              <span className="caption-b text-fg-secondary">로그인 ID</span>
              <span className="body-r text-fg-primary">{admin.loginId}</span>
            </div>
            <div className="flex items-center justify-between">
              <span className="caption-b text-fg-secondary">닉네임</span>
              <span className="body-r text-fg-primary">{admin.nickname}</span>
            </div>
            <div className="flex items-center justify-between">
              <span className="caption-b text-fg-secondary">이메일</span>
              <span className="body-r text-fg-primary">{admin.email}</span>
            </div>
            <div className="flex items-center justify-between">
              <span className="caption-b text-fg-secondary">역할</span>
              <AdminRoleBadge role={admin.role} />
            </div>
          </div>

          <div className="border-t border-border-default pt-4">
            {isPasswordMode ? (
              <form onSubmit={handlePasswordSubmit} className="flex flex-col gap-3">
                <label className="flex flex-col gap-1.5">
                  <span className="caption-b text-fg-secondary">새 비밀번호</span>
                  <input
                    type="password"
                    value={newPassword}
                    onChange={(event) => setNewPassword(event.target.value)}
                    placeholder="새 비밀번호를 입력하세요"
                    autoComplete="new-password"
                    className="body-r rounded-[var(--radius-md)] border border-border-default bg-surface-default px-3 py-2 text-fg-primary placeholder:text-fg-disabled focus:outline-none focus:ring-1 focus:ring-primary-1"
                  />
                </label>
                <div className="flex items-center justify-end gap-2">
                  <button
                    type="button"
                    onClick={() => {
                      setIsPasswordMode(false)
                      setNewPassword('')
                    }}
                    disabled={isSubmitting}
                    className="caption-b rounded-[var(--radius-md)] border border-border-default px-3 py-1.5 text-fg-primary transition-colors hover:bg-surface-subtle disabled:opacity-50"
                  >
                    취소
                  </button>
                  <button
                    type="submit"
                    disabled={!newPassword.trim() || isSubmitting}
                    className="caption-b rounded-[var(--radius-md)] bg-primary-1 px-3 py-1.5 text-fg-inverse transition-opacity hover:opacity-90 disabled:opacity-50"
                  >
                    {isSubmitting ? '변경 중…' : '변경'}
                  </button>
                </div>
              </form>
            ) : (
              <button
                type="button"
                onClick={() => setIsPasswordMode(true)}
                className="body-b w-full rounded-[var(--radius-md)] border border-border-default bg-surface-default px-4 py-2 text-fg-primary transition-colors hover:bg-surface-subtle"
              >
                비밀번호 변경
              </button>
            )}
          </div>
        </div>

        <footer className="flex items-center justify-end border-t border-border-default px-6 py-4">
          <button
            type="button"
            onClick={onClose}
            disabled={isSubmitting}
            className="body-b rounded-[var(--radius-md)] border border-border-default bg-surface-default px-4 py-2 text-fg-primary transition-colors hover:bg-surface-subtle disabled:opacity-50"
          >
            닫기
          </button>
        </footer>
      </div>
    </div>
  )
}
