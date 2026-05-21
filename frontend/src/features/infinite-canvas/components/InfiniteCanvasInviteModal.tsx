import type { FormEvent } from 'react'

interface InfiniteCanvasInviteModalProps {
  open: boolean
  inviteCode: string
  isPending: boolean
  errorMessage: string | null
  onInviteCodeChange: (inviteCode: string) => void
  onSubmit: () => void
  onClose: () => void
}

export default function InfiniteCanvasInviteModal({
  open,
  inviteCode,
  isPending,
  errorMessage,
  onInviteCodeChange,
  onSubmit,
  onClose,
}: InfiniteCanvasInviteModalProps) {
  if (!open) return null

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    onSubmit()
  }

  return (
    <div className="infinite-canvas-modal-backdrop">
      <div
        className="infinite-canvas-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="infinite-canvas-invite-title"
      >
        <form className="infinite-canvas-modal__form" onSubmit={handleSubmit}>
          <div className="infinite-canvas-modal__header">
            <h2 id="infinite-canvas-invite-title">초대코드 입장</h2>
            <button
              type="button"
              className="infinite-canvas-modal__close"
              aria-label="초대코드 입장 닫기"
              onClick={onClose}
            >
              ×
            </button>
          </div>

          <label className="infinite-canvas-modal__field">
            <span>초대코드</span>
            <input
              value={inviteCode}
              autoFocus
              inputMode="text"
              autoComplete="off"
              placeholder="코드를 입력해주세요"
              disabled={isPending}
              onChange={(event) => onInviteCodeChange(event.target.value)}
            />
          </label>

          {errorMessage && (
            <p className="infinite-canvas-modal__error" role="alert">
              {errorMessage}
            </p>
          )}

          <button type="submit" className="infinite-canvas-modal__submit" disabled={isPending}>
            {isPending ? '입장 중' : '입장하기'}
          </button>
        </form>
      </div>
    </div>
  )
}
