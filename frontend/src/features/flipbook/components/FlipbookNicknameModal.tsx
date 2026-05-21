'use client'

import { Dialog } from '@base-ui/react/dialog'
import { X } from 'lucide-react'
import { useEffect, useState, type ChangeEvent, type FormEvent } from 'react'

import { cn } from '@/shared/libs'

import { useFlipbookNickname } from '../hooks'

interface FlipbookNicknameModalProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  onSuccess?: () => void
}

const NICKNAME_MAX_LENGTH = 10

export default function FlipbookNicknameModal({
  open,
  onOpenChange,
  onSuccess,
}: FlipbookNicknameModalProps) {
  const [nickname, setNickname] = useState('')

  const handleSuccess = () => {
    onOpenChange(false)
    onSuccess?.()
  }

  const { isPending, fieldError, generalError, submit, clearError } = useFlipbookNickname({
    onSuccess: handleSuccess,
  })

  useEffect(() => {
    let cancelled = false

    void (async () => {
      if (!open && !cancelled) {
        setNickname('')
        clearError()
      }
    })()

    return () => {
      cancelled = true
    }
  }, [clearError, open])

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    submit(nickname)
  }

  const handleChange = (event: ChangeEvent<HTMLInputElement>) => {
    setNickname(event.target.value.slice(0, NICKNAME_MAX_LENGTH))
    if (fieldError || generalError) clearError()
  }

  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Portal>
        <Dialog.Backdrop className="fixed inset-0 z-[var(--z-overlay)] bg-black/30" />
        <Dialog.Popup className="fixed left-1/2 top-[calc(env(safe-area-inset-top)+1rem)] z-[var(--z-modal)] max-h-[calc(100dvh-2rem)] w-[min(420px,calc(100vw-2rem))] -translate-x-1/2 overflow-y-auto rounded-[16px] border border-flipbook-light bg-flipbook-paper shadow-[0_16px_32px_var(--color-flipbook-shadow)] sm:top-1/2 sm:-translate-y-1/2">
          <header className="flex items-center justify-between border-b border-flipbook-light px-5 py-4">
            <Dialog.Title className="h4-b text-flipbook-ink">닉네임 설정</Dialog.Title>
            <Dialog.Close
              aria-label="닫기"
              className="grid size-8 place-items-center rounded-[8px] text-flipbook-deep hover:bg-flipbook-light hover:text-flipbook-ink"
            >
              <X className="size-4" aria-hidden />
            </Dialog.Close>
          </header>

          <form onSubmit={handleSubmit} className="flex flex-col gap-4 px-5 py-5">
            <p className="caption-r text-flipbook-deep">
              플립북 방을 만들거나 입장하려면 닉네임이 필요해요.
            </p>

            <label className="flex flex-col gap-2">
              <span className="caption-b text-flipbook-primary">
                새 닉네임 (최대 {NICKNAME_MAX_LENGTH}자)
              </span>
              <input
                type="text"
                inputMode="text"
                autoComplete="off"
                spellCheck={false}
                value={nickname}
                onChange={handleChange}
                placeholder="예: 망고"
                aria-invalid={fieldError ? true : undefined}
                disabled={isPending}
                maxLength={NICKNAME_MAX_LENGTH}
                className={cn(
                  'body-l-r rounded-[8px] border border-flipbook-light bg-white px-4 py-3 text-flipbook-ink outline-none placeholder:text-flipbook-muted focus:border-flipbook-primary',
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
                className="body-b min-h-11 rounded-[8px] border border-flipbook-light bg-flipbook-paper px-4 text-flipbook-primary disabled:opacity-45"
              >
                취소
              </Dialog.Close>
              <button
                type="submit"
                disabled={isPending || nickname.trim().length === 0}
                className="body-b min-h-11 rounded-[8px] bg-flipbook-primary px-5 text-flipbook-ink disabled:opacity-45"
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
