'use client'

import { Mail, X } from 'lucide-react'

import { cn } from '@/shared/libs'
import { formatKoreanDateTime } from '@/shared/utils'
import type {
  AdminInquiryDetailResponse,
  AdminInquiryStatus,
} from '@/shared/types'

import { InquiryStatusBadge } from './InquiryStatusBadge'

// 문의 상세 모달 — dumb 컴포넌트. detail/loading/error는 페이지에서 주입.

interface InquiryDetailModalProps {
  detail: AdminInquiryDetailResponse | null
  isLoading: boolean
  error: string | null
  open: boolean
  isMutating: boolean
  canHandle: boolean
  onClose: () => void
  onChangeStatus: (inquiryId: number, next: AdminInquiryStatus) => void
  onRequestReply: (inquiryId: number, title: string) => void
}

const STATUS_OPTIONS: AdminInquiryStatus[] = ['new', 'in_progress', 'resolved', 'closed']
const STATUS_LABEL: Record<AdminInquiryStatus, string> = {
  new: '신규',
  in_progress: '처리중',
  resolved: '완료',
  closed: '종료',
}

function formatDate(value: string | null): string {
  return formatKoreanDateTime(value, {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

export function InquiryDetailModal({
  detail,
  isLoading,
  error,
  open,
  isMutating,
  canHandle,
  onClose,
  onChangeStatus,
  onRequestReply,
}: InquiryDetailModalProps) {
  if (!open) return null

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="inquiry-detail-title"
      className="fixed inset-0 z-[var(--z-modal)] flex items-center justify-center bg-black/40 backdrop-blur-sm"
    >
      <div className="flex max-h-[90vh] w-full max-w-3xl flex-col overflow-hidden rounded-[var(--radius-xl)] bg-surface-default shadow-lg">
        <header className="flex items-center justify-between gap-3 border-b border-border-default px-6 py-4">
          <h2 id="inquiry-detail-title" className="h3-b text-fg-primary">
            문의 상세
          </h2>
          <button
            type="button"
            onClick={onClose}
            className="rounded-[var(--radius-md)] p-1 text-fg-secondary transition-colors hover:bg-surface-subtle"
            aria-label="닫기"
          >
            <X className="h-5 w-5" />
          </button>
        </header>

        <div className="min-h-0 flex-1 overflow-y-auto px-6 py-5">
          {isLoading && <p className="body-r text-fg-secondary">불러오는 중…</p>}
          {error && (
            <p role="alert" className="body-r text-red-500">
              {error}
            </p>
          )}
          {!isLoading && !error && detail && <DetailBody detail={detail} />}
        </div>

        {detail && (
          <footer className="flex flex-wrap items-center justify-between gap-3 border-t border-border-default px-6 py-4">
            <label className="flex items-center gap-2">
              <span className="caption-b text-fg-secondary">상태</span>
              <select
                value={detail.status}
                onChange={(event) =>
                  onChangeStatus(
                    detail.id,
                    event.target.value as AdminInquiryStatus,
                  )
                }
                disabled={isMutating || !canHandle}
                className={cn(
                  'body-r rounded-[var(--radius-md)] border border-border-default bg-surface-default px-3 py-1.5 text-fg-primary focus:border-primary-2 focus:outline-none disabled:opacity-50',
                )}
              >
                {STATUS_OPTIONS.map((option) => (
                  <option key={option} value={option}>
                    {STATUS_LABEL[option]}
                  </option>
                ))}
              </select>
            </label>
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={() => onRequestReply(detail.id, detail.title)}
                disabled={isMutating || !canHandle}
                title={
                  canHandle ? undefined : '뷰어 권한은 조회만 가능합니다.'
                }
                className="inline-flex items-center gap-1.5 rounded-[var(--radius-md)] bg-primary-1 px-4 py-2 body-b text-fg-inverse transition-opacity hover:opacity-90 disabled:opacity-50"
              >
                <Mail className="h-4 w-4" />
                이메일 회신
              </button>
              <button
                type="button"
                onClick={onClose}
                disabled={isMutating}
                className="rounded-[var(--radius-md)] border border-border-default bg-surface-default px-4 py-2 body-b text-fg-primary transition-colors hover:bg-surface-subtle disabled:opacity-50"
              >
                닫기
              </button>
            </div>
          </footer>
        )}
      </div>
    </div>
  )
}

function DetailBody({ detail }: { detail: AdminInquiryDetailResponse }) {
  return (
    <div className="flex flex-col gap-5">
      <section className="flex items-start justify-between gap-4">
        <div className="flex min-w-0 flex-1 flex-col gap-1">
          <span className="caption-b text-fg-secondary">제목</span>
          <h3 className="h4-b text-fg-primary">{detail.title}</h3>
        </div>
        <InquiryStatusBadge status={detail.status} />
      </section>

      <section className="grid grid-cols-2 gap-3">
        <DetailRow label="문의 ID" value={`#${detail.id}`} />
        <DetailRow label="유형" value={detail.type} />
        <DetailRow label="작성자 UUID" value={detail.userId} monospace />
        <DetailRow label="답변 이메일" value={detail.email} />
        <DetailRow label="생성 시각" value={formatDate(detail.createdAt)} />
        <DetailRow label="수정 시각" value={formatDate(detail.updatedAt)} />
        <DetailRow
          label="담당 관리자"
          value={detail.assignedTo === null ? '미할당' : `#${detail.assignedTo}`}
        />
        <DetailRow
          label="답변 시각"
          value={detail.respondedAt ? formatDate(detail.respondedAt) : '미응답'}
        />
      </section>

      <section className="flex flex-col gap-2 rounded-[var(--radius-md)] border border-border-default p-4">
        <span className="caption-b text-fg-secondary">문의 내용</span>
        <p className="body-r whitespace-pre-wrap text-fg-primary">
          {detail.content}
        </p>
      </section>

      {detail.attachments.length > 0 && (
        <section className="flex flex-col gap-2">
          <span className="caption-b text-fg-secondary">
            첨부 파일 ({detail.attachments.length})
          </span>
          <ul className="flex flex-col gap-1">
            {detail.attachments.map((url) => (
              <li key={url}>
                <a
                  href={url}
                  target="_blank"
                  rel="noreferrer"
                  className="body-r font-mono text-primary-2 underline hover:opacity-80"
                >
                  {url}
                </a>
              </li>
            ))}
          </ul>
        </section>
      )}

      {detail.responseNote && (
        <section className="flex flex-col gap-2 rounded-[var(--radius-md)] border border-border-default bg-surface-subtle p-4">
          <span className="caption-b text-fg-secondary">관리자 메모 / 답변</span>
          <p className="body-r whitespace-pre-wrap text-fg-primary">
            {detail.responseNote}
          </p>
        </section>
      )}

      {Object.keys(detail.meta).length > 0 && (
        <section className="flex flex-col gap-2 rounded-[var(--radius-md)] border border-border-default p-4">
          <span className="caption-b text-fg-secondary">작성 환경 메타데이터</span>
          <pre className="body-r overflow-x-auto whitespace-pre-wrap font-mono text-fg-primary">
            {JSON.stringify(detail.meta, null, 2)}
          </pre>
        </section>
      )}
    </div>
  )
}

interface DetailRowProps {
  label: string
  value: React.ReactNode
  monospace?: boolean
}

function DetailRow({ label, value, monospace }: DetailRowProps) {
  return (
    <div className="flex flex-col gap-0.5">
      <span className="caption-b text-fg-secondary">{label}</span>
      <span className={cn('body-r text-fg-primary', monospace && 'font-mono text-xs')}>
        {value}
      </span>
    </div>
  )
}
