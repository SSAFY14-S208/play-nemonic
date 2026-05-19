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
        <Dialog.Backdrop className="fixed inset-0 z-[var(--z-overlay)] bg-[#061248]/62 backdrop-blur-[8px]" />
        <Dialog.Popup className="fixed left-1/2 top-1/2 z-[var(--z-modal)] max-h-[calc(100dvh-2rem)] w-[430px] max-w-[calc(100vw-2rem)] -translate-x-1/2 -translate-y-1/2 overflow-y-auto rounded-[28px] border border-[#b7e7ff]/80 bg-[linear-gradient(145deg,rgb(21_93_170_/_0.94),rgb(48_142_218_/_0.88)_48%,rgb(102_193_244_/_0.82))] text-white shadow-[0_28px_70px_rgb(2_22_68_/_44%),inset_0_1px_0_rgb(255_255_255_/_45%)] backdrop-blur-xl">
          <header className="flex items-center justify-between border-b border-white/28 px-6 py-5">
            <Dialog.Title className="h4-b text-white drop-shadow-[0_2px_8px_rgb(3_37_90_/_35%)]">
              닉네임 설정
            </Dialog.Title>
            <Dialog.Close
              aria-label="닫기"
              className="grid size-8 place-items-center rounded-[10px] text-white/85 transition-colors hover:bg-white/18 hover:text-white"
            >
              <X className="size-4" aria-hidden />
            </Dialog.Close>
          </header>

          <form onSubmit={handleSubmit} className="flex flex-col gap-5 px-6 py-6">
            <p className="caption-r text-[#e5f7ff]">
              무한 캔버스 방을 만들거나 입장하려면 닉네임이 필요해요.
            </p>

            <label className="flex flex-col gap-2">
              <span className="caption-b text-[#b9ecff]">
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
                  'body-l-r rounded-[18px] border border-[#aee8ff]/85 bg-white/95 px-5 py-4 text-[#12305f] shadow-[inset_0_2px_4px_rgb(34_93_151_/_10%),0_10px_24px_rgb(3_42_101_/_18%)] outline-none placeholder:text-[#7f98b3] focus:border-[#f7faff] focus:ring-4 focus:ring-[#7ee7ff]/35 disabled:bg-white/70',
                  fieldError && 'border-error focus:border-error focus:ring-error/20',
                )}
              />
              {fieldError && (
                <span role="alert" className="caption-r text-[#ffe2e2]">
                  {fieldError}
                </span>
              )}
            </label>

            {generalError && (
              <p role="alert" className="caption-r text-[#ffe2e2]">
                {generalError}
              </p>
            )}

            <div className="flex justify-end gap-2 pt-1">
              <Dialog.Close
                disabled={isPending}
                className="body-b min-h-11 rounded-[14px] border border-white/50 bg-white/16 px-5 text-white shadow-[inset_0_1px_0_rgb(255_255_255_/_28%)] transition-colors hover:bg-white/24 disabled:opacity-45"
              >
                취소
              </Dialog.Close>
              <button
                type="submit"
                disabled={isPending || nickname.trim().length === 0}
                className="body-b min-h-11 rounded-[14px] bg-[linear-gradient(135deg,#67e8ff_0%,#4f8cff_52%,#6c63ff_100%)] px-6 text-white shadow-[0_12px_26px_rgb(25_88_205_/_34%)] transition-[filter,opacity] hover:brightness-105 disabled:bg-none disabled:bg-[#8dadcf] disabled:text-white/72 disabled:opacity-65"
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
