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
      className="fixed inset-0 z-[var(--z-overlay)] grid place-items-center bg-[#19172a]/50 p-4 backdrop-blur-[2px]"
    >
      <section
        className="relative aspect-[1500/1040] w-full max-w-6xl bg-contain bg-center bg-no-repeat"
        style={{
          backgroundImage: 'url("/images/community-canvas/ui/modal-detail-frame.svg")',
          backgroundSize: '100% 100%',
        }}
      >
        <button
          type="button"
          aria-label="메모 상세 닫기"
          onClick={onClose}
          className="absolute right-[5.3%] top-[5.3%] z-10 grid size-11 place-items-center rounded-full border border-[#ffd66b] bg-[#fff8e1] text-fg-secondary shadow-[0_7px_16px_rgb(71_68_112_/_16%)] transition hover:-translate-y-0.5 hover:bg-white"
        >
          <X className="size-5" />
        </button>

        <div className="absolute left-[6.4%] right-[6.4%] top-[9.2%] bottom-[25.4%] overflow-hidden rounded-[1rem] border border-[#ffdf82]/80 bg-white shadow-[inset_0_1px_0_rgb(255_255_255_/_88%),0_8px_22px_rgb(71_68_112_/_10%)]">
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

        <footer className="absolute bottom-[7.1%] left-[6.4%] right-[6.4%] flex min-h-[6.5rem] flex-wrap items-center justify-between gap-3 px-6">
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
