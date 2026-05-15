'use client'

import Image from 'next/image'
import { Flag, Trash2 } from 'lucide-react'
import type { CommunityMemoDetailResponse } from '@/shared/types'
import { parseServerInstant } from '@/shared/utils'
import { CommunityModalFrame } from './CommunityModalFrame'

interface CommunityMemoDetailModalProps {
  isOpen: boolean
  detail: CommunityMemoDetailResponse | null
  playbackImageUrl: string | null
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
  playbackImageUrl,
  detailStatus,
  detailError,
  mutationStatus,
  onClose,
  onDelete,
  onReportOpen,
}: CommunityMemoDetailModalProps) {
  if (!isOpen) return null

  const displayImageUrl = detail
    ? playbackImageUrl || detail.memoOriginalImageUrl || detail.memoImageUrl
    : null

  return (
    <CommunityModalFrame
      isOpen={isOpen}
      titleId="community-memo-detail-title"
      frameImageUrl="/images/community-canvas/ui/modal-detail-frame.svg"
      aspectRatio={1500 / 1040}
      maxWidth="70rem"
      closeButtonLabel="메모 상세 닫기"
      frameClassName="max-md:aspect-auto max-md:h-[calc(100dvh_-_2rem)]"
      contentClassName="bottom-[6.4%] left-[6.4%] right-[6.4%] top-[8.8%] max-md:inset-4 max-md:pt-12"
      closeButtonClassName="right-[4.9%] top-[4.7%] max-md:right-4 max-md:top-4"
      onClose={onClose}
    >
      <div className="grid h-full min-h-0 grid-rows-[minmax(0,1fr)_auto] gap-3">
        <div className="relative min-h-0 overflow-hidden rounded-[1rem] border border-[#ffdf82]/80 bg-white shadow-[inset_0_1px_0_rgb(255_255_255_/_88%),0_8px_22px_rgb(71_68_112_/_10%)]">
          {detailStatus === 'loading' && (
            <div className="absolute inset-0 grid place-items-center">
              <p className="body-b text-fg-secondary">메모를 불러오는 중이에요.</p>
            </div>
          )}

          {detailStatus === 'error' && (
            <div className="absolute inset-0 grid place-items-center p-8 text-center">
              <div>
                <p className="h3-b text-fg-primary">메모를 불러오지 못했어요.</p>
                <p className="body-r mt-2 text-fg-secondary">{detailError}</p>
              </div>
            </div>
          )}

          {detail && displayImageUrl && (
            <Image
              key={displayImageUrl}
              src={displayImageUrl}
              alt={`${detail.authorNickname}의 커뮤니티 메모 원본`}
              fill
              sizes="100vw"
              unoptimized
              className="object-contain p-6 md:p-8"
            />
          )}
        </div>

        <footer className="flex shrink-0 flex-wrap items-center justify-between gap-3 rounded-[0.85rem] border border-[#ffdf82]/70 bg-[#fffdf1]/80 px-5 py-4 shadow-[0_6px_18px_rgb(71_68_112_/_8%)] max-md:px-4">
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
                  className="body-b inline-flex h-11 items-center justify-center gap-2 rounded-[0.45rem] bg-[#FFD95D] px-5 text-fg-primary transition hover:-translate-y-0.5 disabled:cursor-not-allowed disabled:opacity-60"
                >
                  <Flag className="size-4" />
                  신고하기
                </button>
              )}
            </div>
          )}
        </footer>
      </div>
    </CommunityModalFrame>
  )
}
