'use client'

import { Dialog } from '@base-ui/react/dialog'
import { X } from 'lucide-react'
import { useEffect, useState } from 'react'

import { cn } from '@/shared/libs'

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
  // open 변화를 추적하는 effect는 React 19 권장: dialog의 부수효과(외부 시스템 동기)
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
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Portal>
        <Dialog.Backdrop className="fixed inset-0 z-[var(--z-overlay)] bg-black/30" />
        <Dialog.Popup className="font-paperlogy fixed left-1/2 top-1/2 z-[var(--z-modal)] w-[min(420px,calc(100vw-2rem))] -translate-x-1/2 -translate-y-1/2 overflow-hidden rounded-[var(--radius-xl)] bg-relay-paper shadow-lg">
          <header className="flex items-center justify-between border-b border-relay-line px-5 py-4">
            <Dialog.Title className="h4-b text-relay-ink">
              방 코드 입력
            </Dialog.Title>
            <Dialog.Close
              aria-label="닫기"
              className="grid size-8 cursor-pointer place-items-center rounded-[var(--radius-md)] text-relay-muted transition-colors hover:bg-relay-active hover:text-relay-ink"
            >
              <X className="size-4" />
            </Dialog.Close>
          </header>

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
              <Dialog.Close
                disabled={isPending}
                className="body-b min-h-11 cursor-pointer rounded-[var(--radius-md)] border border-relay-line bg-relay-paper px-4 text-relay-accent-strong transition-all hover:brightness-95 disabled:opacity-45 disabled:hover:brightness-100"
              >
                취소
              </Dialog.Close>
              <button
                type="submit"
                disabled={isPending || roomCode.length === 0}
                className="body-b min-h-11 cursor-pointer rounded-[var(--radius-md)] bg-relay-accent px-5 text-relay-ink transition-all hover:brightness-105 disabled:opacity-45 disabled:hover:brightness-100"
              >
                {isPending ? '입장 중…' : '입장'}
              </button>
            </div>
          </form>
        </Dialog.Popup>
      </Dialog.Portal>
    </Dialog.Root>
  )
}
