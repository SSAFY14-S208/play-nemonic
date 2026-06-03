'use client'

import Image from 'next/image'
import { PostItNote } from '@/shared/components/PostItNote'
import { cn } from '@/shared/libs'
import type { CommunityMemoItemResponse } from '@/shared/types'
import { getCommunityMemoColor, getStaticCommunityMemoImageUrl } from '../utils'

const MEMO_LAYER_BASE_Z_INDEX = 9_100
const ACTIVE_MEMO_LAYER_BASE_Z_INDEX = 9_500
const MAX_MEMO_STACK_ORDER = 399

interface CommunityMemoCardProps {
  memo: CommunityMemoItemResponse
  isActive: boolean
  playbackImageUrl?: string | null
  isPlaybackPaused: boolean
  placementMotion?: 'attach' | 'detach' | 'lift' | 'release'
  isInteractionDisabled?: boolean
  onSelect: (memo: CommunityMemoItemResponse) => void
  onOpenDetail: (memoUuid: string) => void
}

export function CommunityMemoCard({
  memo,
  isActive,
  playbackImageUrl,
  isPlaybackPaused,
  placementMotion,
  isInteractionDisabled = false,
  onSelect,
  onOpenDetail,
}: CommunityMemoCardProps) {
  const normalizedMemoStackOrder = Math.min(Math.max(memo.zIndex, 0), MAX_MEMO_STACK_ORDER)
  const staticImageUrl = getStaticCommunityMemoImageUrl(memo)
  const displayImageUrl = isPlaybackPaused
    ? staticImageUrl
    : playbackImageUrl || memo.memoThumbnailImageUrl || memo.memoImageUrl

  return (
    <button
      type="button"
      data-community-memo-interactive="true"
      aria-label={`${memo.authorNickname}의 메모 보기`}
      onClick={(event) => {
        event.stopPropagation()
        onSelect(memo)
      }}
      onDoubleClick={(event) => {
        event.stopPropagation()
        onOpenDetail(memo.memoUuid)
      }}
      className={cn(
        'group absolute h-[160px] w-[160px] origin-center transition duration-200',
        isActive && 'drop-shadow-[0_0_0.875rem_rgb(48_121_86_/_34%)]',
        isInteractionDisabled && 'pointer-events-none',
      )}
      style={{
        left: `calc(50% + ${memo.positionX}px)`,
        top: `calc(50% + ${memo.positionY}px)`,
        zIndex:
          (isActive ? ACTIVE_MEMO_LAYER_BASE_Z_INDEX : MEMO_LAYER_BASE_Z_INDEX) +
          normalizedMemoStackOrder,
        transform: `translate(-50%, -50%) rotate(${memo.rotationDeg}deg) scale(${isActive ? 1.04 : 1})`,
      }}
    >
      <span
        data-community-memo-placement={placementMotion}
        className="community-memo-placement absolute inset-0"
      >
        <PostItNote
          shape="square"
          motion={placementMotion ? 'none' : 'hover'}
          selected={isActive}
          className="absolute inset-0 h-full w-full drop-shadow-[0_12px_18px_rgb(66_45_25_/_18%)]"
          style={{ color: getCommunityMemoColor(memo) }}
        />
        <span
          data-post-it-art-motion={placementMotion ? undefined : 'hover'}
          className="post-it-note-art absolute inset-x-4 bottom-5 top-7 overflow-hidden rounded-[0.35rem]"
        >
          {displayImageUrl ? (
            <Image
              src={displayImageUrl}
              alt={`${memo.authorNickname}의 커뮤니티 메모`}
              fill
              sizes="160px"
              unoptimized
              className="object-contain transition duration-200 group-hover:scale-[1.03]"
            />
          ) : (
            <span className="body-r flex h-full items-center justify-center text-fg-secondary">
              메모
            </span>
          )}
        </span>
      </span>
    </button>
  )
}
