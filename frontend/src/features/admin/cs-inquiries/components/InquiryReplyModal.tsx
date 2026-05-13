'use client'

import { useEffect, useState } from 'react'

import { cn } from '@/shared/libs'
import type { AdminInquiryReplyRequest } from '@/shared/types'

// 이메일 회신 입력 모달 — body는 { subject, message } (Swagger 명세).

interface InquiryReplyModalProps {
  open: boolean
  /** 회신 대상 문의의 제목 — 기본 subject 프리필 용도. */
  defaultSubject?: string
  isSubmitting: boolean
  onSubmit: (payload: AdminInquiryReplyRequest) => void
  onCancel: () => void
}

export function InquiryReplyModal({
  open,
  defaultSubject,
  isSubmitting,
  onSubmit,
  onCancel,
}: InquiryReplyModalProps) {
  const [subject, setSubject] = useState('')
  const [message, setMessage] = useState('')

  // 모달 open 시 subject를 문의 제목으로 프리필, 닫힐 때 입력값 리셋.
  // React Compiler 규칙 — setState는 async 콜백 안에서만.
  useEffect(() => {
    void (async () => {
      if (open) {
        setSubject(defaultSubject ? `Re: ${defaultSubject}` : '')
        setMessage('')
      } else {
        setSubject('')
        setMessage('')
      }
    })()
  }, [open, defaultSubject])

  if (!open) return null

  const canSubmit = subject.trim().length > 0 && message.trim().length > 0

  const handleSubmit = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!canSubmit) return
    onSubmit({ subject: subject.trim(), message: message.trim() })
  }

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="inquiry-reply-title"
      className="fixed inset-0 z-[var(--z-modal)] flex items-center justify-center bg-black/40 backdrop-blur-sm"
    >
      <form
        onSubmit={handleSubmit}
        className="flex w-full max-w-2xl flex-col gap-4 rounded-[var(--radius-xl)] bg-surface-default p-6 shadow-lg"
      >
        <header className="flex flex-col gap-1">
          <h2 id="inquiry-reply-title" className="h3-b text-fg-primary">
            이메일 회신
          </h2>
          <p className="body-r text-fg-secondary">
            발송 후 해당 문의는 자동으로 처리 완료 상태가 됩니다.
          </p>
        </header>

        <label className="flex flex-col gap-1">
          <span className="caption-b text-fg-secondary">제목</span>
          <input
            type="text"
            value={subject}
            onChange={(event) => setSubject(event.target.value)}
            disabled={isSubmitting}
            className={cn(
              'body-r rounded-[var(--radius-md)] border border-border-default bg-surface-default px-3 py-2 text-fg-primary placeholder:text-fg-disabled focus:border-primary-2 focus:outline-none disabled:opacity-50',
            )}
          />
        </label>

        <label className="flex flex-col gap-1">
          <span className="caption-b text-fg-secondary">본문</span>
          <textarea
            value={message}
            onChange={(event) => setMessage(event.target.value)}
            disabled={isSubmitting}
            rows={8}
            placeholder="이메일 본문을 입력하세요."
            className={cn(
              'body-r resize-none rounded-[var(--radius-md)] border border-border-default bg-surface-default px-3 py-2 text-fg-primary placeholder:text-fg-disabled focus:border-primary-2 focus:outline-none disabled:opacity-50',
            )}
          />
        </label>

        <div className="flex justify-end gap-2">
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
            disabled={isSubmitting || !canSubmit}
            className="rounded-[var(--radius-md)] bg-primary-1 px-4 py-2 body-b text-fg-inverse transition-opacity disabled:opacity-50"
          >
            {isSubmitting ? '발송 중…' : '발송'}
          </button>
        </div>
      </form>
    </div>
  )
}
