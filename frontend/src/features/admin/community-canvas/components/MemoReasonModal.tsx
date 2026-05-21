'use client'

import { useEffect, useState } from 'react'

import { cn } from '@/shared/libs'

// 메모 숨김/복원 시 운영자가 입력하는 사유를 받는 블로킹 모달.
// 백엔드 PATCH /admin/community/memos/{memoId}/(hide|restore) body의 reason 필드용.

export type MemoReasonAction = 'hide' | 'restore'

interface MemoReasonModalProps {
  open: boolean
  action: MemoReasonAction
  isSubmitting: boolean
  onSubmit: (reason: string) => void
  onCancel: () => void
}

const ACTION_LABEL: Record<MemoReasonAction, { title: string; submit: string; placeholder: string }> = {
  hide: {
    title: '메모 숨김 처리',
    submit: '숨김 처리',
    placeholder: '예: 신고 내용 확인 결과 부적절한 이미지로 판단했습니다.',
  },
  restore: {
    title: '메모 복원',
    submit: '복원',
    placeholder: '예: 오신고로 확인되어 복구합니다.',
  },
}

export function MemoReasonModal({
  open,
  action,
  isSubmitting,
  onSubmit,
  onCancel,
}: MemoReasonModalProps) {
  const [reason, setReason] = useState('')
  const label = ACTION_LABEL[action]

  // 모달이 닫힐 때 입력값 초기화 — 다음 열림에서 stale 상태 방지.
  // React Compiler 규칙: setState는 async 콜백 안에서만 호출.
  useEffect(() => {
    void (async () => {
      if (!open) setReason('')
    })()
  }, [open])

  if (!open) return null

  const handleSubmit = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!reason.trim()) return
    onSubmit(reason.trim())
  }

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="memo-reason-title"
      className="fixed inset-0 z-[var(--z-modal)] flex items-center justify-center bg-black/40 backdrop-blur-sm"
    >
      <form
        onSubmit={handleSubmit}
        className="w-full max-w-md rounded-[var(--radius-xl)] bg-surface-default p-6 shadow-lg"
      >
        <h2 id="memo-reason-title" className="h3-b text-fg-primary">
          {label.title}
        </h2>
        <p className="body-r mt-2 text-fg-secondary">
          이 사유는 감사 로그에 영구 기록됩니다.
        </p>

        <label className="mt-4 flex flex-col gap-1">
          <span className="caption-b text-fg-secondary">사유 (필수)</span>
          <textarea
            value={reason}
            onChange={(event) => setReason(event.target.value)}
            placeholder={label.placeholder}
            disabled={isSubmitting}
            rows={4}
            className={cn(
              'body-r resize-none rounded-[var(--radius-md)] border border-border-default bg-surface-default px-3 py-2 text-fg-primary placeholder:text-fg-disabled focus:border-primary-2 focus:outline-none disabled:opacity-50',
            )}
            autoFocus
          />
        </label>

        <div className="mt-5 flex justify-end gap-2">
          <button
            type="button"
            onClick={onCancel}
            disabled={isSubmitting}
            className="rounded-[var(--radius-md)] border border-border-default bg-surface-default px-4 py-2 body-b text-fg-primary transition-colors hover:bg-surface-subtle disabled:opacity-50"
          >
            취소
          </button>
          <button
            type="submit"
            disabled={isSubmitting || !reason.trim()}
            className="rounded-[var(--radius-md)] bg-primary-1 px-4 py-2 body-b text-fg-inverse transition-opacity disabled:opacity-50"
          >
            {isSubmitting ? '처리 중…' : label.submit}
          </button>
        </div>
      </form>
    </div>
  )
}
