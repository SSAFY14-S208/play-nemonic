import type { RefObject } from 'react'
import { KeyRound, Sparkles, X } from 'lucide-react'
import { motion } from 'motion/react'

interface RoomCodeModalProps {
  open: boolean
  inputRef: RefObject<HTMLInputElement | null>
  roomCodeDraft: string
  isBusy: boolean
  errorMessage: string | null
  onRoomCodeDraftChange: (roomCode: string) => void
  onClose: () => void
  onSubmit: () => void
}

export function RoomCodeModal({
  open,
  inputRef,
  roomCodeDraft,
  isBusy,
  errorMessage,
  onRoomCodeDraftChange,
  onClose,
  onSubmit,
}: RoomCodeModalProps) {
  if (!open) return null

  return (
    <motion.div
      className="fixed inset-0 z-50 grid place-items-start bg-[#2a1f3a]/28 px-5 py-[calc(1rem+env(safe-area-inset-top))] backdrop-blur-[3px] sm:place-items-center"
      role="presentation"
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) {
          onClose()
        }
      }}
    >
      <motion.form
        className="relative max-h-[calc(100dvh-2rem)] w-full max-w-[460px] overflow-y-auto rounded-[28px] border border-[#f5bdca] bg-[#fffaf5] px-6 pb-7 pt-7 text-flipbook-ink shadow-[0_24px_70px_rgb(92_31_38_/_24%)] sm:px-8 sm:pb-8"
        initial={{ y: 20, scale: 0.96, opacity: 0 }}
        animate={{ y: 0, scale: 1, opacity: 1 }}
        transition={{ duration: 0.22, ease: [0.22, 0.8, 0.2, 1] }}
        onSubmit={(event) => {
          event.preventDefault()
          onSubmit()
        }}
        onKeyDown={(event) => {
          if (event.key === 'Escape') {
            onClose()
          }
        }}
      >
        <div
          aria-hidden
          className="absolute -right-10 -top-10 size-32 rounded-full bg-[#ffe7ef]"
        />
        <div
          aria-hidden
          className="absolute -bottom-16 -left-14 size-40 rounded-full bg-[#fff3a8]/70"
        />

        <button
          type="button"
          onClick={onClose}
          disabled={isBusy}
          className="absolute right-5 top-5 grid size-10 place-items-center rounded-full bg-white text-flipbook-deep shadow-[0_5px_14px_rgb(92_31_38_/_12%)] transition hover:-translate-y-0.5 disabled:cursor-not-allowed disabled:opacity-60"
          aria-label="입장 코드 모달 닫기"
        >
          <X className="size-5" aria-hidden />
        </button>

        <div className="relative">
          <div className="mx-auto grid size-16 place-items-center rounded-[20px] bg-[#fdebf0] text-[#f45d8d] shadow-[inset_0_0_0_1px_rgb(244_93_141_/_16%)]">
            <KeyRound className="size-8" aria-hidden />
          </div>
          <h2 className="h2-b mt-5 text-center text-flipbook-ink">입장 코드 입력</h2>
          <p className="body-r mt-2 text-center text-flipbook-deep/75">
            친구가 알려준 코드를 입력하면 바로 같은 플립북 방으로 들어가요.
          </p>

          <label className="caption-b mt-7 block text-flipbook-deep" htmlFor="flipbook-room-code-modal">
            입장 코드
          </label>
          <input
            ref={inputRef}
            id="flipbook-room-code-modal"
            value={roomCodeDraft}
            onChange={(event) => onRoomCodeDraftChange(event.target.value.toUpperCase())}
            maxLength={12}
            placeholder="예: AB3K9Q"
            className="h3-b mt-2 h-14 w-full rounded-[16px] border border-[#f5bdca] bg-white px-5 text-center uppercase tracking-[0.08em] text-flipbook-ink outline-none shadow-[inset_0_2px_8px_rgb(92_31_38_/_6%)] transition focus:border-[#f45d8d] focus:ring-4 focus:ring-[#f45d8d]/15"
          />

          {errorMessage && (
            <p className="caption-b mt-3 rounded-[12px] bg-[#fdebf0] px-4 py-3 text-center text-flipbook-deep">
              {errorMessage}
            </p>
          )}

          <button
            type="submit"
            disabled={isBusy}
            className="body-l-b mt-6 inline-flex h-14 w-full items-center justify-center gap-2 rounded-[18px] bg-[#f45d8d] text-white shadow-[0_12px_24px_rgb(244_93_141_/_30%)] transition hover:-translate-y-0.5 disabled:cursor-not-allowed disabled:opacity-70"
          >
            <Sparkles className="size-5" aria-hidden />
            {isBusy ? '입장 중' : '입장하기'}
          </button>
        </div>
      </motion.form>
    </motion.div>
  )
}
