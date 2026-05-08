'use client'

import { Dialog } from '@base-ui/react/dialog'
import { X } from 'lucide-react'
import { useEffect, useState } from 'react'

import { useRelayNickname } from '../hooks'
import { cn } from '@/shared/libs'

interface RelayNicknameModalProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  // 부스가 "방 만들기 누르려다 익명 닉네임 검사에 걸린 사용자"를 위해 띄운 경우,
  // 닉네임 갱신 성공 후 보류했던 액션을 이어서 수행하도록 컴포넌트가 콜백을 넘긴다.
  onSuccess?: () => void
}

const NICKNAME_MAX_LENGTH = 10

export default function RelayNicknameModal({
  open,
  onOpenChange,
  onSuccess,
}: RelayNicknameModalProps) {
  const [nickname, setNickname] = useState('')

  const handleSuccess = () => {
    onOpenChange(false)
    onSuccess?.()
  }

  const { isPending, fieldError, generalError, submit, clearError } = useRelayNickname({
    onSuccess: handleSuccess,
  })

  // 모달이 닫힐 때 입력값/에러 초기화 — 다음 열림에서 stale 상태 방지.
  useEffect(() => {
    (async () => {
      if (!open) {
        setNickname('')
        clearError()
      }
    })()
  }, [open, clearError])

  const handleSubmit = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    submit(nickname)
  }

  const handleChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const sanitized = event.target.value.slice(0, NICKNAME_MAX_LENGTH)
    setNickname(sanitized)
    if (fieldError) clearError()
  }

  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Portal>
        <Dialog.Backdrop className="fixed inset-0 z-[var(--z-overlay)] bg-black/30" />
        <Dialog.Popup className="fixed left-1/2 top-1/2 z-[var(--z-modal)] w-[min(420px,calc(100vw-2rem))] -translate-x-1/2 -translate-y-1/2 overflow-hidden rounded-[var(--radius-xl)] bg-relay-paper shadow-lg">
          <header className="flex items-center justify-between border-b border-relay-line px-5 py-4">
            <Dialog.Title className="h4-b text-relay-ink">
              닉네임 설정
            </Dialog.Title>
            <Dialog.Close
              aria-label="닫기"
              className="grid size-8 place-items-center rounded-[var(--radius-md)] text-relay-muted hover:bg-relay-active hover:text-relay-ink"
            >
              <X className="size-4" />
            </Dialog.Close>
          </header>

          <form onSubmit={handleSubmit} className="flex flex-col gap-4 px-5 py-5">
            <p className="caption-r text-relay-muted">
              릴레이 방을 만들거나 입장하려면 닉네임이 필요해요.
            </p>

            <label className="flex flex-col gap-2">
              <span className="caption-b text-relay-accent-strong">
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
                  'rounded-[var(--radius-md)] border border-relay-line bg-relay-active px-4 py-3 body-l-r text-relay-ink placeholder:text-relay-muted focus:border-relay-accent focus:outline-none',
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
                className="body-b min-h-11 rounded-[var(--radius-md)] border border-relay-line bg-relay-paper px-4 text-relay-accent-strong disabled:opacity-45"
              >
                취소
              </Dialog.Close>
              <button
                type="submit"
                disabled={isPending || nickname.trim().length === 0}
                className="body-b min-h-11 rounded-[var(--radius-md)] bg-relay-accent px-5 text-relay-ink disabled:opacity-45"
              >
                {isPending ? '저장 중…' : '저장'}
              </button>
            </div>
          </form>
        </Dialog.Popup>
      </Dialog.Portal>
    </Dialog.Root>
  )
}
