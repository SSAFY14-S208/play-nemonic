'use client'

import { useState } from 'react'
import { Button } from '@/shared/components'
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
      onOpenChange(false)
      onSuccess?.()
    },
  })

  const closeNicknameModal = () => {
    setNickname('')
    clearError()
    onOpenChange(false)
  }

  if (!open) return null

  const handleSubmit = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    submit(nickname)
  }

  return (
    <div className="fixed inset-0 z-50 grid place-items-center bg-slate-950/28 px-4 backdrop-blur-sm">
      <form
        onSubmit={handleSubmit}
        className="flex w-full max-w-[420px] flex-col gap-4 rounded-[24px] border border-canvas-border bg-canvas-panel p-6 text-canvas-ink shadow-[0_22px_54px_rgb(67_102_148_/_24%)]"
      >
        <div className="flex flex-col gap-2">
          <h2 className="h3-b">닉네임 설정</h2>
          <p className="body-r text-canvas-muted">
            무한 캔버스에 입장하려면 닉네임이 필요해요.
          </p>
        </div>

        <label className="flex flex-col gap-2">
          <span className="caption-b text-canvas-muted">새 닉네임</span>
          <input
            type="text"
            inputMode="text"
            autoComplete="off"
            spellCheck={false}
            value={nickname}
            onChange={(event) => {
              setNickname(event.target.value.slice(0, NICKNAME_MAX_LENGTH))
              if (fieldError) clearError()
            }}
            placeholder="예: 네모"
            aria-invalid={fieldError ? true : undefined}
            disabled={isPending}
            maxLength={NICKNAME_MAX_LENGTH}
            className={cn(
              'body-l-r min-h-12 rounded-[16px] border border-canvas-border bg-white px-4 text-canvas-ink outline-none placeholder:text-canvas-muted focus:border-canvas-accent',
              fieldError && 'border-red-400',
            )}
          />
          {fieldError && (
            <span role="alert" className="caption-r text-red-500">
              {fieldError}
            </span>
          )}
        </label>

        {generalError && (
          <p role="alert" className="caption-r text-red-500">
            {generalError}
          </p>
        )}

        <div className="flex justify-end gap-2">
          <Button
            type="button"
            size="md"
            color="neutral"
            disabled={isPending}
            onClick={closeNicknameModal}
          >
            취소
          </Button>
          <Button
            type="submit"
            size="md"
            color="blue"
            disabled={isPending || nickname.trim().length === 0}
          >
            {isPending ? '저장 중' : '저장'}
          </Button>
        </div>
      </form>
    </div>
  )
}
