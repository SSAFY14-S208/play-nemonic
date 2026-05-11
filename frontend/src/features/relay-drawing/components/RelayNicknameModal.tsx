'use client'

import { useEffect, useState } from 'react'

import { useRelayNickname } from '../hooks'
import { cn } from '@/shared/libs'

import RelayButton from './RelayButton'
import RelayModal from './RelayModal'

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
    <RelayModal
      open={open}
      onOpenChange={onOpenChange}
      title="닉네임 설정"
      width="md"
    >
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
          <RelayButton
            variant="secondary"
            onClick={() => onOpenChange(false)}
            disabled={isPending}
          >
            취소
          </RelayButton>
          <RelayButton
            type="submit"
            variant="primary"
            disabled={isPending || nickname.trim().length === 0}
          >
            {isPending ? '저장 중…' : '저장'}
          </RelayButton>
        </div>
      </form>
    </RelayModal>
  )
}
