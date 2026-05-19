'use client'

import { Dialog } from '@base-ui/react/dialog'
import { X } from 'lucide-react'
import { useState, type ChangeEvent, type FormEvent } from 'react'

import { cn } from '@/shared/libs'

import { useInfinityNickname } from '../hooks'

interface InfinityNicknameModalProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  onSuccess?: () => void
}

const NICKNAME_MAX_LENGTH = 10

export function InfinityNicknameModal({
  open,
  onOpenChange,
  onSuccess,
}: InfinityNicknameModalProps) {
  const [nickname, setNickname] = useState('')
  const { isPending, fieldError, generalError, submit, clearError } = useInfinityNickname({
    onSuccess: () => {
      setNickname('')
      onSuccess?.()
      onOpenChange(false)
    },
  })

  const handleOpenChange = (nextOpen: boolean) => {
    if (!nextOpen) {
      setNickname('')
      clearError()
    }
    onOpenChange(nextOpen)
  }

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    submit(nickname)
  }

  const handleChange = (event: ChangeEvent<HTMLInputElement>) => {
    setNickname(event.target.value.slice(0, NICKNAME_MAX_LENGTH))
    if (fieldError || generalError) clearError()
  }

  return (
    <Dialog.Root open={open} onOpenChange={handleOpenChange}>
      <Dialog.Portal>
        <Dialog.Backdrop className="fixed inset-0 z-[var(--z-overlay)] bg-[#101239]/45 backdrop-blur-[6px]" />
        <Dialog.Popup className="fixed left-1/2 top-1/2 z-[var(--z-modal)] max-h-[calc(100dvh-2rem)] w-[420px] max-w-[calc(100vw-2rem)] -translate-x-1/2 -translate-y-1/2 overflow-y-auto rounded-[20px] border border-white/70 bg-white/92 text-canvas-ink shadow-[0_22px_56px_rgb(24_34_78_/_32%)] backdrop-blur-xl">
          <header className="flex items-center justify-between border-b border-canvas-border/45 px-5 py-4">
            <Dialog.Title className="h4-b text-canvas-ink">닉네임 설정</Dialog.Title>
            <Dialog.Close
              aria-label="닫기"
              className="grid size-8 place-items-center rounded-[8px] text-canvas-muted hover:bg-canvas-active/70 hover:text-canvas-ink"
            >
              <X className="size-4" aria-hidden />
            </Dialog.Close>
          </header>

          <form onSubmit={handleSubmit} className="flex flex-col gap-4 px-5 py-5">
            <p className="caption-r text-canvas-muted">
              무한 캔버스 방을 만들거나 입장하려면 닉네임이 필요해요.
            </p>

            <label className="flex flex-col gap-2">
              <span className="caption-b text-canvas-accent">
                새 닉네임 (최대 {NICKNAME_MAX_LENGTH}자)
              </span>
              <input
                type="text"
                inputMode="text"
                autoComplete="off"
                spellCheck={false}
                value={nickname}
                onChange={handleChange}
                placeholder="예: 네모"
                aria-invalid={fieldError ? true : undefined}
                disabled={isPending}
                maxLength={NICKNAME_MAX_LENGTH}
                className={cn(
                  'body-l-r rounded-[12px] border border-canvas-border/70 bg-white px-4 py-3 text-canvas-ink shadow-[inset_0_1px_2px_rgb(24_34_78_/_8%)] outline-none placeholder:text-canvas-muted focus:border-canvas-accent focus:ring-2 focus:ring-canvas-accent/20',
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
              <Dialog.Close
                disabled={isPending}
                className="body-b min-h-11 rounded-[12px] border border-canvas-border/70 bg-white/90 px-4 text-canvas-accent shadow-sm hover:bg-canvas-active/70 disabled:opacity-45"
              >
                취소
              </Dialog.Close>
              <button
                type="submit"
                disabled={isPending || nickname.trim().length === 0}
                className="body-b min-h-11 rounded-[12px] bg-canvas-accent px-5 text-white shadow-[0_10px_22px_rgb(61_113_210_/_22%)] disabled:opacity-45"
              >
                {isPending ? '저장 중...' : '저장'}
              </button>
            </div>
          </form>
        </Dialog.Popup>
      </Dialog.Portal>
    </Dialog.Root>
  )
}
