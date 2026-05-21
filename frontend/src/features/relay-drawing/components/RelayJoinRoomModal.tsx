'use client'

import { useEffect, useState } from 'react'

import { cn } from '@/shared/libs'

import RelayButton from './RelayButton'
import RelayModal from './RelayModal'

interface RelayJoinRoomModalProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  onSubmit: (roomCode: string) => void
  isPending: boolean
  error: string | null
}

// 가이드 §11 — 방 코드는 백엔드가 6자 영숫자로 발급한다(예: AB3K9Q).
// 입력 자체는 백엔드가 더 엄격히 검증하므로 클라는 길이 힌트만 둔다.
const ROOM_CODE_LENGTH = 6

export default function RelayJoinRoomModal({
  open,
  onOpenChange,
  onSubmit,
  isPending,
  error,
}: RelayJoinRoomModalProps) {
  const [roomCode, setRoomCode] = useState('')

  // 모달이 닫힐 때 입력값을 비운다 — 다음 열림에서 이전 시도 코드가 남지 않도록.
  useEffect(() => {
    (async () => {
      if (!open) setRoomCode('')
    })()
  }, [open])

  const handleSubmit = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    onSubmit(roomCode)
  }

  const handleChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    // 영숫자만 허용하고 자동으로 대문자화 — 백엔드가 대문자 코드를 발급하므로 일치.
    const sanitized = event.target.value
      .replace(/[^a-zA-Z0-9]/g, '')
      .toUpperCase()
      .slice(0, ROOM_CODE_LENGTH)
    setRoomCode(sanitized)
  }

  return (
    <RelayModal
      open={open}
      onOpenChange={onOpenChange}
      title="방 코드 입력"
      width="md"
    >
      <form onSubmit={handleSubmit} className="flex flex-col gap-4 px-5 py-5">
        <label className="flex flex-col gap-2">
          <span className="caption-b text-relay-accent-strong">
            친구에게 받은 6자리 코드
          </span>
          <input
            type="text"
            inputMode="text"
            autoComplete="off"
            autoCapitalize="characters"
            spellCheck={false}
            value={roomCode}
            onChange={handleChange}
            placeholder="ABC123"
            aria-invalid={error ? true : undefined}
            className={cn(
              'rounded-[var(--radius-md)] border border-relay-line bg-relay-active px-4 py-3 tracking-[6px] text-center text-[28px] font-bold uppercase text-relay-ink placeholder:text-relay-muted focus:border-relay-accent focus:outline-none',
              error && 'border-error',
            )}
            disabled={isPending}
          />
        </label>

        {error && (
          <p role="alert" className="caption-r text-error">
            {error}
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
            disabled={isPending || roomCode.length === 0}
          >
            {isPending ? '입장 중…' : '입장'}
          </RelayButton>
        </div>
      </form>
    </RelayModal>
  )
}
