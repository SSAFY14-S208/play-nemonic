'use client'

import Image from 'next/image'
import { PostItNote } from '@/shared/components/PostItNote'
import { cn } from '@/shared/libs'
import type { CommunityMemoItemResponse } from '@/shared/types'
import { getCommunityMemoColor } from '../utils'

interface CommunityMemoCardProps {
  memo: CommunityMemoItemResponse
  isActive: boolean
  isInteractionDisabled?: boolean
  onSelect: (memo: CommunityMemoItemResponse) => void
  onOpenDetail: (memoUuid: string) => void
}

export function CommunityMemoCard({
  memo,
  isActive,
  isInteractionDisabled = false,
  onSelect,
  onOpenDetail,
}: CommunityMemoCardProps) {
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
        'group absolute h-[156px] w-[184px] origin-center transition duration-200',
        isActive && 'drop-shadow-[0_0_0.875rem_rgb(48_121_86_/_34%)]',
        isInteractionDisabled && 'pointer-events-none',
      )}
      style={{
        left: `calc(50% + ${memo.positionX}px)`,
        top: `calc(50% + ${memo.positionY}px)`,
        zIndex: isActive ? Math.max(memo.zIndex, 9999) : memo.zIndex,
        transform: `translate(-50%, -50%) rotate(${memo.rotationDeg}deg) scale(${isActive ? 1.04 : 1})`,
      }}
    >
      <PostItNote
        motion="hover"
        selected={isActive}
        className="absolute inset-0 h-full w-full drop-shadow-[0_12px_18px_rgb(66_45_25_/_18%)]"
        style={{ color: getCommunityMemoColor(memo) }}
      />
      <span
        data-post-it-art-motion="hover"
        className="post-it-note-art absolute inset-x-5 bottom-6 top-8 overflow-hidden rounded-[0.35rem]"
      >
        {memo.memoThumbnailImageUrl || memo.memoImageUrl ? (
          <Image
            src={memo.memoThumbnailImageUrl || memo.memoImageUrl}
            alt={`${memo.authorNickname}의 커뮤니티 메모`}
            fill
            sizes="184px"
            unoptimized
            className="object-contain transition duration-200 group-hover:scale-[1.03]"
          />
        ) : (
          <span className="body-r flex h-full items-center justify-center text-fg-secondary">
            메모
          </span>
        )}
      </span>
    </button>
  )
}
