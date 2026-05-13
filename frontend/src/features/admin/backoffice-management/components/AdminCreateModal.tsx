'use client'

import { useEffect, useState } from 'react'
import { X } from 'lucide-react'

import type { AdminCreateRequest } from '@/shared/types'

interface AdminCreateModalProps {
  open: boolean
  isSubmitting: boolean
  onSubmit: (payload: AdminCreateRequest) => void
  onClose: () => void
}

export function AdminCreateModal({
  open,
  isSubmitting,
  onSubmit,
  onClose,
}: AdminCreateModalProps) {
  const [loginId, setLoginId] = useState('')
  const [password, setPassword] = useState('')
  const [nickname, setNickname] = useState('')
  const [email, setEmail] = useState('')

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      if (!cancelled && !open) {
        setLoginId('')
        setPassword('')
        setNickname('')
        setEmail('')
      }
    })()
    return () => {
      cancelled = true
    }
  }, [open])

  if (!open) return null

  const isFormValid =
    loginId.trim() !== '' &&
    password.trim() !== '' &&
    nickname.trim() !== '' &&
    email.trim() !== ''

  const handleSubmit = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!isFormValid || isSubmitting) return
    onSubmit({
      loginId: loginId.trim(),
      password: password.trim(),
      nickname: nickname.trim(),
      email: email.trim(),
    })
  }

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="create-admin-title"
      className="fixed inset-0 z-[var(--z-modal)] flex items-center justify-center bg-black/40 backdrop-blur-sm"
    >
      <form
        onSubmit={handleSubmit}
        className="flex w-full max-w-md flex-col overflow-hidden rounded-[var(--radius-xl)] bg-surface-default shadow-lg"
      >
        <header className="flex items-center justify-between gap-3 border-b border-border-default px-6 py-4">
          <h2 id="create-admin-title" className="h4-b text-fg-primary">
            관리자 계정 추가
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
          <label className="flex flex-col gap-1.5">
            <span className="caption-b text-fg-secondary">로그인 ID</span>
            <input
              type="text"
              value={loginId}
              onChange={(event) => setLoginId(event.target.value)}
              placeholder="로그인 ID를 입력하세요"
              autoComplete="off"
              className="body-r rounded-[var(--radius-md)] border border-border-default bg-surface-default px-3 py-2 text-fg-primary placeholder:text-fg-disabled focus:outline-none focus:ring-1 focus:ring-primary-1"
            />
          </label>

          <label className="flex flex-col gap-1.5">
            <span className="caption-b text-fg-secondary">비밀번호</span>
            <input
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              placeholder="비밀번호를 입력하세요"
              autoComplete="new-password"
              className="body-r rounded-[var(--radius-md)] border border-border-default bg-surface-default px-3 py-2 text-fg-primary placeholder:text-fg-disabled focus:outline-none focus:ring-1 focus:ring-primary-1"
            />
          </label>

          <label className="flex flex-col gap-1.5">
            <span className="caption-b text-fg-secondary">닉네임</span>
            <input
              type="text"
              value={nickname}
              onChange={(event) => setNickname(event.target.value)}
              placeholder="닉네임을 입력하세요"
              autoComplete="off"
              className="body-r rounded-[var(--radius-md)] border border-border-default bg-surface-default px-3 py-2 text-fg-primary placeholder:text-fg-disabled focus:outline-none focus:ring-1 focus:ring-primary-1"
            />
          </label>

          <label className="flex flex-col gap-1.5">
            <span className="caption-b text-fg-secondary">이메일</span>
            <input
              type="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              placeholder="이메일을 입력하세요"
              autoComplete="off"
              className="body-r rounded-[var(--radius-md)] border border-border-default bg-surface-default px-3 py-2 text-fg-primary placeholder:text-fg-disabled focus:outline-none focus:ring-1 focus:ring-primary-1"
            />
          </label>
        </div>

        <footer className="flex items-center justify-end gap-2 border-t border-border-default px-6 py-4">
          <button
            type="button"
            onClick={onClose}
            disabled={isSubmitting}
            className="body-b rounded-[var(--radius-md)] border border-border-default bg-surface-default px-4 py-2 text-fg-primary transition-colors hover:bg-surface-subtle disabled:opacity-50"
          >
            취소
          </button>
          <button
            type="submit"
            disabled={!isFormValid || isSubmitting}
            className="body-b rounded-[var(--radius-md)] bg-primary-1 px-4 py-2 text-fg-inverse transition-opacity hover:opacity-90 disabled:opacity-50"
          >
            {isSubmitting ? '생성 중…' : '생성'}
          </button>
        </footer>
      </form>
    </div>
  )
}
