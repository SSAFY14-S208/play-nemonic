'use client'

import Image from 'next/image'
import { Flag, Trash2, X } from 'lucide-react'
import type { CommunityMemoDetailResponse } from '@/shared/types'
import { parseServerInstant } from '@/shared/utils'

interface CommunityMemoDetailModalProps {
  isOpen: boolean
  detail: CommunityMemoDetailResponse | null
  detailStatus: 'idle' | 'loading' | 'success' | 'error'
  detailError: string | null
  mutationStatus: 'idle' | 'loading' | 'success' | 'error'
  onClose: () => void
  onDelete: () => void
  onReportOpen: () => void
}

function formatAttachedAt(value: string) {
  const date = parseServerInstant(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('ko-KR', {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}

export function CommunityMemoDetailModal({
  isOpen,
  detail,
  detailStatus,
  detailError,
  mutationStatus,
  onClose,
  onDelete,
  onReportOpen,
}: CommunityMemoDetailModalProps) {
  if (!isOpen) return null

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="community-memo-detail-title"
      className="fixed inset-0 z-[var(--z-overlay)] grid place-items-center bg-black/35 p-4"
    >
      <section className="relative flex max-h-[94vh] w-full max-w-6xl flex-col overflow-hidden rounded-[0.5rem] bg-surface-default shadow-[0_18px_44px_rgb(30_24_18_/_24%)]">
        <button
          type="button"
          aria-label="메모 상세 닫기"
          onClick={onClose}
          className="absolute right-4 top-4 z-10 grid size-10 place-items-center rounded-full bg-surface-default text-fg-secondary shadow-[0_8px_16px_rgb(30_24_18_/_12%)]"
        >
          <X className="size-5" />
        </button>

        <div className="relative min-h-[28rem] flex-1 bg-surface-subtle md:min-h-[40rem]">
          {detailStatus === 'loading' && (
            <div className="absolute inset-0 grid place-items-center">
              <p className="body-b text-fg-secondary">메모를 여는 중</p>
            </div>
          )}

          {detailStatus === 'error' && (
            <div className="absolute inset-0 grid place-items-center p-8 text-center">
              <div>
                <p className="h3-b text-fg-primary">메모를 열지 못했어요.</p>
                <p className="body-r mt-2 text-fg-secondary">{detailError}</p>
              </div>
            </div>
          )}

          {detail && (
            <Image
              src={detail.memoOriginalImageUrl || detail.memoImageUrl}
              alt={`${detail.authorNickname}의 커뮤니티 메모 원본`}
              fill
              sizes="100vw"
              unoptimized
              className="object-contain p-6 md:p-8"
            />
          )}
        </div>

        <footer className="flex flex-wrap items-center justify-between gap-3 border-t border-border-default bg-surface-default p-5">
          <div>
            <p className="caption-b text-primary-2">{detail?.sourceType ?? 'COMMUNITY'}</p>
            <h2 id="community-memo-detail-title" className="h3-b mt-1 text-fg-primary">
              {detail?.authorNickname ?? '커뮤니티 메모'}
            </h2>
            {detail && (
              <p className="body-r mt-1 text-fg-secondary">
                {formatAttachedAt(detail.attachedAt)}
              </p>
            )}
          </div>

          {detail && (
            <div className="flex min-w-[12rem] justify-end">
              {detail.ownedByMe ? (
                <button
                  type="button"
                  onClick={onDelete}
                  disabled={mutationStatus === 'loading'}
                  className="body-b inline-flex h-11 items-center justify-center gap-2 rounded-[0.45rem] border border-border-default bg-surface-default px-5 text-error transition hover:bg-surface-subtle disabled:cursor-not-allowed disabled:opacity-60"
                >
                  <Trash2 className="size-4" />
                  삭제
                </button>
              ) : (
                <button
                  type="button"
                  onClick={onReportOpen}
                  disabled={mutationStatus === 'loading'}
                  className="body-b inline-flex h-11 items-center justify-center gap-2 rounded-[0.45rem] bg-fg-primary px-5 text-fg-inverse transition hover:bg-fg-secondary disabled:cursor-not-allowed disabled:opacity-60"
                >
                  <Flag className="size-4" />
                  신고하기
                </button>
              )}
            </div>
          )}
        </footer>
      </section>
    </div>
  )
}
