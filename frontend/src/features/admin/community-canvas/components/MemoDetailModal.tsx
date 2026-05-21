'use client'

import Image from 'next/image'
import { EyeOff, RotateCcw, X } from 'lucide-react'

import { cn } from '@/shared/libs'
import type { AdminCommunityMemoDetailResponse } from '@/shared/types'
import { formatKoreanDateTime } from '@/shared/utils'

import { MemoStatusBadge } from './MemoStatusBadge'

// 메모 상세 모달 — dumb 컴포넌트.
// 페이지가 useMemoDetail로 데이터를 fetch해서 props로 전달하고, 액션은 페이지의
// MemoReasonModal 흐름으로 위임한다.

interface MemoDetailModalProps {
  /** null이면 모달이 닫힌 상태. */
  detail: AdminCommunityMemoDetailResponse | null
  isLoading: boolean
  error: string | null
  /** 모달 표시 조건 — memoId가 set돼 있으면 detail이 아직 로딩 중이라도 모달은 띄움. */
  open: boolean
  isMutating: boolean
  canModerate: boolean
  onClose: () => void
  onHide: (memoId: string) => void
  onRestore: (memoId: string) => void
}

const SOURCE_LABEL: Record<string, string> = {
  DIRECT: '직접 작성',
  GALLERY: '갤러리',
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

export function MemoDetailModal({
  detail,
  isLoading,
  error,
  open,
  isMutating,
  canModerate,
  onClose,
  onHide,
  onRestore,
}: MemoDetailModalProps) {
  if (!open) return null

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="memo-detail-title"
      className="fixed inset-0 z-[var(--z-modal)] flex items-center justify-center bg-black/40 backdrop-blur-sm"
    >
      <div className="flex max-h-[90vh] w-full max-w-3xl flex-col overflow-hidden rounded-[var(--radius-xl)] bg-surface-default shadow-lg">
        <header className="flex items-center justify-between gap-3 border-b border-border-default px-6 py-4">
          <h2 id="memo-detail-title" className="h3-b text-fg-primary">
            메모 상세
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
          <footer className="flex items-center justify-end gap-2 border-t border-border-default px-6 py-4">
            {detail.isHidden ? (
              <button
                type="button"
                onClick={() => onRestore(detail.memoId)}
                disabled={isMutating || !canModerate}
                title={
                  canModerate ? undefined : '뷰어 권한은 조회만 가능합니다.'
                }
                className="inline-flex items-center gap-1.5 rounded-[var(--radius-md)] border border-border-default bg-surface-default px-4 py-2 body-b text-fg-primary transition-colors hover:bg-surface-subtle disabled:opacity-50"
              >
                <RotateCcw className="h-4 w-4" />
                복원
              </button>
            ) : (
              <button
                type="button"
                onClick={() => onHide(detail.memoId)}
                disabled={isMutating || !canModerate}
                title={
                  canModerate ? undefined : '뷰어 권한은 조회만 가능합니다.'
                }
                className="inline-flex items-center gap-1.5 rounded-[var(--radius-md)] bg-red-500 px-4 py-2 body-b text-fg-inverse transition-opacity hover:bg-red-600 disabled:opacity-50"
              >
                <EyeOff className="h-4 w-4" />
                숨김
              </button>
            )}
            <button
              type="button"
              onClick={onClose}
              disabled={isMutating}
              className="rounded-[var(--radius-md)] border border-border-default bg-surface-default px-4 py-2 body-b text-fg-primary transition-colors hover:bg-surface-subtle disabled:opacity-50"
            >
              닫기
            </button>
          </footer>
        )}
      </div>
    </div>
  )
}

function DetailBody({ detail }: { detail: AdminCommunityMemoDetailResponse }) {
  return (
    <div className="flex flex-col gap-6">
      <section className="flex flex-wrap gap-6">
        <div className="flex shrink-0 flex-col gap-2">
          <span className="caption-b text-fg-secondary">원본 이미지</span>
          <Image
            src={detail.memoOriginalImageUrl}
            alt={`${detail.authorNickname}의 메모 원본`}
            width={240}
            height={240}
            unoptimized
            className="h-60 w-60 rounded-[var(--radius-md)] border border-border-default object-contain"
          />
        </div>
        <div className="flex min-w-0 flex-1 flex-col gap-3">
          <DetailRow label="메모 ID" value={detail.memoId} monospace />
          <DetailRow
            label="작성자"
            value={
              <span className="flex flex-col gap-0.5">
                <span>{detail.authorNickname}</span>
                <span className="caption-r font-mono text-fg-disabled">
                  {detail.authorUserUuid}
                </span>
              </span>
            }
          />
          <DetailRow
            label="출처"
            value={
              <span className="flex flex-col gap-0.5">
                <span>
                  {SOURCE_LABEL[detail.sourceType] ?? detail.sourceType}
                </span>
                <span className="caption-r text-fg-secondary">
                  {detail.artifactKind} ·{' '}
                  <span className="font-mono text-fg-disabled">
                    {detail.artifactId}
                  </span>
                </span>
              </span>
            }
          />
          <DetailRow
            label="좌표 / 레이어"
            value={`x: ${detail.positionX}, y: ${detail.positionY}, z-index: ${detail.zIndex}, rotation: ${detail.rotationDeg}°`}
          />
          <DetailRow label="부착 시각" value={formatDate(detail.attachedAt)} />
          <DetailRow
            label="생성 / 수정"
            value={`${formatDate(detail.createdAt)} / ${formatDate(detail.updatedAt)}`}
          />
        </div>
      </section>

      <section className="flex flex-col gap-2 rounded-[var(--radius-md)] border border-border-default p-4">
        <span className="caption-b text-fg-secondary">OCR</span>
        <p className="body-r whitespace-pre-wrap text-fg-primary">
          {detail.ocrText ?? '—'}
        </p>
        {detail.ocrCategories.length > 0 && (
          <div className="flex flex-wrap gap-1">
            {detail.ocrCategories.map((category) => (
              <span
                key={category}
                className="caption-r rounded-full bg-surface-subtle px-2 py-0.5 text-fg-secondary"
              >
                {category}
              </span>
            ))}
          </div>
        )}
      </section>

      <section className="flex flex-col gap-3 rounded-[var(--radius-md)] border border-border-default p-4">
        <div className="flex items-center justify-between">
          <span className="caption-b text-fg-secondary">모더레이션</span>
          <MemoStatusBadge
            isHidden={detail.isHidden}
            hiddenReason={detail.hiddenReason}
            moderationStatus={detail.moderationStatus}
          />
        </div>
        <DetailRow label="신고 누적" value={`${detail.reportCount}건`} />
        {detail.isHidden && (
          <>
            <DetailRow label="숨김 사유" value={detail.hiddenReason ?? '—'} />
            <DetailRow label="숨김 시각" value={formatDate(detail.hiddenAt)} />
          </>
        )}
        <DetailRow
          label="최종 검토"
          value={
            detail.reviewedBy === null
              ? '미검토'
              : `관리자 #${detail.reviewedBy} · ${formatDate(detail.reviewedAt)}`
          }
        />
      </section>

      <section className="flex flex-col gap-3">
        <span className="caption-b text-fg-secondary">
          신고 내역 ({detail.reports.length}건)
        </span>
        {detail.reports.length === 0 ? (
          <p className="body-r text-fg-secondary">신고 내역이 없습니다.</p>
        ) : (
          <ul className="flex flex-col gap-2">
            {detail.reports.map((report) => (
              <li
                key={report.reportId}
                className="flex flex-col gap-1 rounded-[var(--radius-md)] border border-border-default p-3"
              >
                <div className="flex items-center justify-between gap-2">
                  <div className="flex flex-col gap-0.5">
                    <span className="body-m text-fg-primary">
                      {report.reporterNickname}
                    </span>
                    <span className="caption-r font-mono text-fg-disabled">
                      {report.reporterUserUuid.slice(0, 8)}…
                    </span>
                  </div>
                  <div className="flex flex-col items-end gap-0.5">
                    <span className="caption-b rounded-full bg-rose-100 px-2 py-0.5 text-rose-700">
                      {report.reason}
                    </span>
                    <span className="caption-r text-fg-secondary">
                      {formatDate(report.createdAt)}
                    </span>
                  </div>
                </div>
                {report.reasonDetail && (
                  <p className="body-r text-fg-primary">{report.reasonDetail}</p>
                )}
              </li>
            ))}
          </ul>
        )}
      </section>
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
      <span className={cn('body-r text-fg-primary', monospace && 'font-mono')}>
        {value}
      </span>
    </div>
  )
}
