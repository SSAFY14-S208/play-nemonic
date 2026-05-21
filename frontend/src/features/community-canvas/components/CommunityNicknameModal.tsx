'use client'

import { X } from 'lucide-react'
import { useEffect, useState, type ChangeEvent, type FormEvent } from 'react'
import { cn } from '@/shared/libs'
import { useCommunityNickname } from '../hooks'

interface CommunityNicknameModalProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  onSuccess?: () => void
}

const NICKNAME_MAX_LENGTH = 10

export function CommunityNicknameModal({
  open,
  onOpenChange,
  onSuccess,
}: CommunityNicknameModalProps) {
  const [nickname, setNickname] = useState('')

  const { isPending, fieldError, generalError, submit, clearError } =
    useCommunityNickname({
      onSuccess: () => {
        setNickname('')
        clearError()
        onSuccess?.()
        onOpenChange(false)
      },
    })

  useEffect(() => {
    let isCancelled = false

    void (async () => {
      if (!open && !isCancelled) {
        setNickname('')
        clearError()
      }
    })()

    return () => {
      isCancelled = true
    }
  }, [clearError, open])

  if (!open) return null

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    submit(nickname)
  }

  const handleChange = (event: ChangeEvent<HTMLInputElement>) => {
    setNickname(event.target.value.slice(0, NICKNAME_MAX_LENGTH))
    if (fieldError || generalError) clearError()
  }

  const handleClose = () => {
    if (isPending) return
    onOpenChange(false)
  }

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="community-nickname-title"
      className="fixed inset-0 z-[var(--z-modal)] grid place-items-center bg-[#19172a]/50 px-4 py-6 backdrop-blur-[2px]"
    >
      <form
        onSubmit={handleSubmit}
        className="flex w-full max-w-[420px] flex-col overflow-hidden rounded-[1rem] border border-[#d5cee3] bg-[#fbfaff] text-fg-primary shadow-[0_24px_54px_rgb(25_20_40_/_24%)]"
      >
        <header className="flex items-center justify-between border-b border-[#d5cee3] px-5 py-4">
          <div>
            <p className="caption-b text-primary-2">커뮤니티 캔버스</p>
            <h2 id="community-nickname-title" className="h4-b text-fg-primary">
              닉네임 설정
            </h2>
          </div>
          <button
            type="button"
            aria-label="닫기"
            disabled={isPending}
            onClick={handleClose}
            className="grid size-9 place-items-center rounded-[0.45rem] text-fg-secondary transition hover:bg-[#f0ecfa] hover:text-fg-primary disabled:cursor-not-allowed disabled:opacity-45"
          >
            <X className="size-4" aria-hidden />
          </button>
        </header>

        <div className="flex flex-col gap-4 px-5 py-5">
          <p className="body-r text-fg-secondary">
            커뮤니티에 메모를 붙이려면 먼저 사용할 닉네임을 정해주세요.
          </p>

          <label className="flex flex-col gap-2">
            <span className="caption-b text-primary-2">
              새 닉네임 (최대 {NICKNAME_MAX_LENGTH}자)
            </span>
            <input
              type="text"
              inputMode="text"
              autoComplete="off"
              spellCheck={false}
              autoFocus
              value={nickname}
              onChange={handleChange}
              placeholder="예: 망고"
              aria-invalid={fieldError ? true : undefined}
              disabled={isPending}
              maxLength={NICKNAME_MAX_LENGTH}
              className={cn(
                'body-l-r h-12 rounded-[0.45rem] border border-[#d5cee3] bg-white px-4 text-fg-primary outline-none placeholder:text-fg-disabled focus:border-primary-1',
                fieldError && 'border-error',
              )}
            />
            {fieldError && (
              <span role="alert" className="caption-r text-error">
                {fieldError}
              </span>
            )}
          </label>

          {generalError && (
            <p role="alert" className="caption-r text-error">
              {generalError}
            </p>
          )}

          <div className="flex justify-end gap-2 pt-1">
            <button
              type="button"
              disabled={isPending}
              onClick={handleClose}
              className="body-b h-11 rounded-[0.45rem] border border-[#d5cee3] bg-[#fbfaff] px-4 text-fg-secondary transition hover:bg-[#f0ecfa] hover:text-fg-primary disabled:cursor-not-allowed disabled:opacity-45"
            >
              취소
            </button>
            <button
              type="submit"
              disabled={isPending || nickname.trim().length === 0}
              className="body-b h-11 rounded-[0.45rem] bg-[#d9d2ea] px-5 text-fg-primary transition hover:bg-[#cec4e5] disabled:cursor-not-allowed disabled:opacity-45"
            >
              {isPending ? '저장 중...' : '저장'}
            </button>
          </div>
        </div>
      </form>
    </div>
  )
}
