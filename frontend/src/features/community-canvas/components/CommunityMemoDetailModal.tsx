'use client'

import Image from 'next/image'
import { Flag, Share2, Trash2, X } from 'lucide-react'
import type { CommunityMemoDetailResponse } from '@/shared/types'
import { formatKoreanDateTime } from '@/shared/utils'
import { useCommunityMemoExternalShare } from '../hooks'
import { getStaticCommunityImageUrl } from '../utils'
import {
  useCommunityCompactViewport,
  useCommunityModalFitScale,
} from './useCommunityModalFitScale'

const DETAIL_MODAL_WIDTH = 1500
const DETAIL_MODAL_HEIGHT = 1040
const DETAIL_MODAL_MAX_WIDTH = 1152

interface CommunityMemoDetailModalProps {
  isOpen: boolean
  detail: CommunityMemoDetailResponse | null
  playbackImageUrl: string | null
  detailStatus: 'idle' | 'loading' | 'success' | 'error'
  detailError: string | null
  mutationStatus: 'idle' | 'loading' | 'success' | 'error'
  isPlaybackPaused: boolean
  onClose: () => void
  onDelete: () => void
  onReportOpen: () => void
}

function formatAttachedAt(value: string) {
  return formatKoreanDateTime(value, {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

export function CommunityMemoDetailModal({
  isOpen,
  detail,
  playbackImageUrl,
  detailStatus,
  detailError,
  mutationStatus,
  isPlaybackPaused,
  onClose,
  onDelete,
  onReportOpen,
}: CommunityMemoDetailModalProps) {
  const modalScale = useCommunityModalFitScale({
    designWidth: DETAIL_MODAL_WIDTH,
    designHeight: DETAIL_MODAL_HEIGHT,
    maxWidth: DETAIL_MODAL_MAX_WIDTH,
    viewportPadding: 32,
  })
  const isCompactViewport = useCommunityCompactViewport()
  const { isSharing, shareCommunityMemo } = useCommunityMemoExternalShare()

  if (!isOpen) return null

  const displayImageUrl = detail
    ? isPlaybackPaused
      ? getStaticCommunityImageUrl(
          detail.memoOriginalImageUrl,
          detail.memoImageUrl,
          detail.memoThumbnailImageUrl,
        )
      : playbackImageUrl ||
        getStaticCommunityImageUrl(
          detail.memoOriginalImageUrl,
          detail.memoImageUrl,
          detail.memoThumbnailImageUrl,
        )
    : null
  const shareButtonLabel = '공유하기'
  const isShareDisabled = !detail || isSharing

  if (isCompactViewport) {
    return (
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="community-memo-detail-title"
        className="fixed inset-0 z-[var(--z-overlay)] overflow-y-auto overflow-x-hidden bg-[#19172a]/50 p-3 backdrop-blur-[2px]"
      >
        <section className="mx-auto flex min-h-full w-full max-w-[46rem] flex-col gap-4 rounded-[1.5rem] border-[0.35rem] border-[#b9b1ce] bg-[#eeeaf7] p-4 shadow-[0_18px_38px_rgb(25_20_40_/_24%)]">
          <header className="flex items-start justify-between gap-3 rounded-[1rem] border border-[#d5cee3] bg-[#fbfaff] p-4">
            <div>
              <p className="caption-b text-primary-2">{detail?.sourceType ?? 'COMMUNITY'}</p>
              <h2 id="community-memo-detail-title" className="h3-b mt-1 text-fg-primary">
                {detail?.authorNickname ?? '커뮤니티 메모'}
              </h2>
            </div>
            <button
              type="button"
              aria-label="메모 상세 닫기"
              onClick={onClose}
              className="grid size-12 shrink-0 place-items-center rounded-full border border-[#b9b1ce] bg-[#fbfaff] text-fg-secondary shadow-[0_7px_16px_rgb(71_68_112_/_16%)]"
            >
              <X className="size-6" />
            </button>
          </header>

          <div className="relative min-h-[18rem] overflow-hidden rounded-[1rem] border border-[#d5cee3]/80 bg-white shadow-[inset_0_1px_0_rgb(255_255_255_/_88%),0_8px_22px_rgb(71_68_112_/_10%)]">
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

            {detail && displayImageUrl && (
              <Image
                key={displayImageUrl}
                src={displayImageUrl}
                alt={`${detail.authorNickname}의 커뮤니티 메모 원본`}
                fill
                sizes="100vw"
                unoptimized
                className="object-contain p-4"
              />
            )}
          </div>

          <footer className="grid gap-4 rounded-[1rem] border border-[#d5cee3] bg-[#fbfaff] p-5">
            {detail && (
              <p className="body-l-m text-fg-secondary">
                {formatAttachedAt(detail.attachedAt)}
              </p>
            )}

            {detail && (
              <div className="grid gap-3 sm:grid-cols-2">
                {detail.ownedByMe ? (
                  <button
                    type="button"
                    onClick={onDelete}
                    disabled={mutationStatus === 'loading'}
                    className="body-l-b inline-flex h-12 items-center justify-center gap-3 rounded-[0.45rem] border border-border-default bg-surface-default px-6 text-error transition disabled:cursor-not-allowed disabled:opacity-60"
                  >
                    <Trash2 className="size-5" />
                    삭제
                  </button>
                ) : (
                  <button
                    type="button"
                    onClick={onReportOpen}
                    disabled={mutationStatus === 'loading'}
                    className="body-l-b inline-flex h-12 items-center justify-center gap-3 rounded-[0.45rem] bg-[#d9d2ea] px-6 text-fg-primary transition disabled:cursor-not-allowed disabled:opacity-60"
                  >
                    <Flag className="size-5" />
                    신고하기
                  </button>
                )}
                <button
                  type="button"
                  onClick={() => void shareCommunityMemo(detail)}
                  disabled={isShareDisabled}
                  className="body-l-b inline-flex h-12 items-center justify-center gap-3 rounded-[0.45rem] bg-primary-1 px-6 text-fg-inverse transition hover:-translate-y-0.5 disabled:cursor-not-allowed disabled:opacity-60"
                >
                  <Share2 className="size-5" />
                  {isSharing ? '공유 준비 중' : shareButtonLabel}
                </button>
              </div>
            )}
          </footer>
        </section>
      </div>
    )
  }

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="community-memo-detail-title"
      className="fixed inset-0 z-[var(--z-overlay)] grid place-items-center overflow-y-auto overflow-x-hidden bg-[#19172a]/50 p-4 backdrop-blur-[2px]"
    >
      <div
        className="relative shrink-0"
        style={{
          width: DETAIL_MODAL_WIDTH * modalScale,
          height: DETAIL_MODAL_HEIGHT * modalScale,
        }}
      >
        <section
          className="absolute left-0 top-0 bg-contain bg-center bg-no-repeat"
          style={{
            width: DETAIL_MODAL_WIDTH,
            height: DETAIL_MODAL_HEIGHT,
            transform: `scale(${modalScale})`,
            transformOrigin: 'top left',
            backgroundImage: 'url("/images/community-canvas/ui/modal-detail-frame.svg")',
            backgroundSize: '100% 100%',
          }}
        >
        <button
          type="button"
          aria-label="메모 상세 닫기"
          onClick={onClose}
          className="absolute right-[5.3%] top-[5.3%] z-10 grid size-16 place-items-center rounded-full border border-[#b9b1ce] bg-[#fbfaff] text-fg-secondary shadow-[0_7px_16px_rgb(71_68_112_/_16%)] transition hover:-translate-y-0.5 hover:bg-white"
        >
          <X className="size-7" />
        </button>

        <div className="absolute left-[6.4%] right-[6.4%] top-[9.2%] bottom-[25.4%] overflow-hidden rounded-[1rem] border border-[#d5cee3]/80 bg-white shadow-[inset_0_1px_0_rgb(255_255_255_/_88%),0_8px_22px_rgb(71_68_112_/_10%)]">
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

        <footer className="absolute bottom-[7.1%] left-[6.4%] right-[6.4%] top-[79.4%] grid grid-cols-[minmax(0,1fr)_auto] items-center gap-4 px-8">
          <div className="self-center">
            <p className="body-b text-primary-2">{detail?.sourceType ?? 'COMMUNITY'}</p>
            <h2 id="community-memo-detail-title" className="h2-b mt-1 text-fg-primary">
              {detail?.authorNickname ?? '커뮤니티 메모'}
            </h2>
            {detail && (
              <p className="body-l-m mt-1 text-fg-secondary">
                {formatAttachedAt(detail.attachedAt)}
              </p>
            )}
          </div>

          {detail && (
            <div className="flex min-w-[26rem] self-center justify-end gap-3">
              {detail.ownedByMe ? (
                <button
                  type="button"
                  onClick={onDelete}
                  disabled={mutationStatus === 'loading'}
                  className="body-l-b inline-flex h-14 items-center justify-center gap-3 rounded-[0.45rem] border border-border-default bg-surface-default px-7 text-error transition hover:bg-surface-subtle disabled:cursor-not-allowed disabled:opacity-60"
                >
                  <Trash2 className="size-5" />
                  삭제
                </button>
              ) : (
                <button
                  type="button"
                  onClick={onReportOpen}
                  disabled={mutationStatus === 'loading'}
                  className="body-l-b inline-flex h-14 items-center justify-center gap-3 rounded-[0.45rem] bg-[#d9d2ea] px-7 text-fg-primary transition hover:-translate-y-0.5 disabled:cursor-not-allowed disabled:opacity-60"
                >
                  <Flag className="size-5" />
                  신고하기
                </button>
              )}
              <button
                type="button"
                onClick={() => void shareCommunityMemo(detail)}
                disabled={isShareDisabled}
                className="body-l-b inline-flex h-14 items-center justify-center gap-3 rounded-[0.45rem] bg-primary-1 px-7 text-fg-inverse transition hover:-translate-y-0.5 disabled:cursor-not-allowed disabled:opacity-60"
              >
                <Share2 className="size-5" />
                {isSharing ? '공유 준비 중' : shareButtonLabel}
              </button>
            </div>
          )}
        </footer>
        </section>
      </div>
    </div>
  )
}
