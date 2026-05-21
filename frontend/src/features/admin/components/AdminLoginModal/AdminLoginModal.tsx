'use client'

import { useState } from 'react'

import { cn } from '@/shared/libs'

import { useAdminLogin } from '../../hooks'

export function AdminLoginModal() {
  const [loginId, setLoginId] = useState('')
  const [password, setPassword] = useState('')

  const { isPending, fieldErrors, generalError, submit, clearError } = useAdminLogin()

  const handleSubmit = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    submit(loginId, password)
  }

  const handleLoginIdChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    setLoginId(event.target.value)
    if (fieldErrors.loginId || generalError) clearError()
  }

  const handlePasswordChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    setPassword(event.target.value)
    if (fieldErrors.password || generalError) clearError()
  }

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="admin-login-title"
      className="fixed inset-0 z-[var(--z-modal)] flex items-center justify-center bg-black/40 backdrop-blur-sm"
    >
      <form
        onSubmit={handleSubmit}
        className="w-full max-w-md rounded-[var(--radius-xl)] bg-surface-default p-8 shadow-lg"
      >
        <h2 id="admin-login-title" className="h2-b text-fg-primary">
          관리자 로그인
        </h2>
        <p className="body-r mt-2 text-fg-secondary">
          백오피스에 접근하려면 관리자 계정으로 로그인하세요.
        </p>

        <label htmlFor="admin-login-id" className="mt-6 flex flex-col gap-2">
          <span className="body-m text-fg-primary">아이디</span>
          <input
            id="admin-login-id"
            type="text"
            inputMode="text"
            autoComplete="username"
            spellCheck={false}
            value={loginId}
            onChange={handleLoginIdChange}
            disabled={isPending}
            aria-invalid={fieldErrors.loginId ? true : undefined}
            className={cn(
              'rounded-[var(--radius-md)] border border-border-default bg-surface-default px-3 py-2 body-r text-fg-primary placeholder:text-fg-disabled focus:border-primary-2 focus:outline-none',
              fieldErrors.loginId && 'border-red-500',
            )}
          />
          {fieldErrors.loginId && (
            <span role="alert" className="caption-r text-red-500">
              {fieldErrors.loginId}
            </span>
          )}
        </label>

        <label htmlFor="admin-login-password" className="mt-4 flex flex-col gap-2">
          <span className="body-m text-fg-primary">비밀번호</span>
          <input
            id="admin-login-password"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={handlePasswordChange}
            disabled={isPending}
            aria-invalid={fieldErrors.password ? true : undefined}
            className={cn(
              'rounded-[var(--radius-md)] border border-border-default bg-surface-default px-3 py-2 body-r text-fg-primary placeholder:text-fg-disabled focus:border-primary-2 focus:outline-none',
              fieldErrors.password && 'border-red-500',
            )}
          />
          {fieldErrors.password && (
            <span role="alert" className="caption-r text-red-500">
              {fieldErrors.password}
            </span>
          )}
        </label>

        {generalError && (
          <p role="alert" className="caption-r mt-4 text-red-500">
            {generalError}
          </p>
        )}

        <button
          type="submit"
          disabled={isPending}
          className="mt-6 w-full rounded-[var(--radius-md)] bg-primary-1 px-4 py-2.5 body-b text-fg-inverse transition-opacity disabled:opacity-50"
        >
          {isPending ? '로그인 중…' : '로그인'}
        </button>
      </form>
    </div>
  )
}
