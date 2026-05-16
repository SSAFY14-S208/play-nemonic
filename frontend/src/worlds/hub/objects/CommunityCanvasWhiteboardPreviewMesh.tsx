import type { CSSProperties } from 'react'
import { Html } from '@react-three/drei'
import { ArrowRight } from 'lucide-react'
import Image from 'next/image'
import { PostItNote } from '@/shared/components/PostItNote'
import { cn } from '@/shared/libs'
import {
  type CommunityCanvasWhiteboardPreviewMemo,
  useCommunityCanvasWhiteboardPreviewMemos,
} from './hooks'

const WHITEBOARD_PREVIEW_POSITION: [number, number, number] = [
  -0.638,
  0.555,
  -0.1087,
]
const WHITEBOARD_PREVIEW_QUATERNION: [number, number, number, number] = [
  -0.00585,
  -0.70849,
  0.00587,
  -0.70568,
]
const WHITEBOARD_PREVIEW_WORLD_SCALE = 0.04
const WHITEBOARD_PREVIEW_WIDTH_PX = 374
const WHITEBOARD_PREVIEW_HEIGHT_PX = 226
const WHITEBOARD_PREVIEW_MEMO_SIZE_PX = 48
const WHITEBOARD_PREVIEW_MAX_STACK_Z_INDEX = 999

function getPreviewMemoStackOrder(memo: CommunityCanvasWhiteboardPreviewMemo) {
  return Math.min(Math.max(memo.zIndex, 0), WHITEBOARD_PREVIEW_MAX_STACK_Z_INDEX)
}

function WhiteboardPreviewMemoCard({
  isExpanded,
  memo,
}: {
  isExpanded: boolean
  memo: CommunityCanvasWhiteboardPreviewMemo
}) {
  const cardStyle: CSSProperties = {
    color: memo.color,
    height: WHITEBOARD_PREVIEW_MEMO_SIZE_PX,
    left: `${memo.xPercent}%`,
    top: `${memo.yPercent}%`,
    transform: `translate(-50%, -50%) rotate(${memo.rotationDeg}deg) scale(${isExpanded ? 1.08 : 1})`,
    width: WHITEBOARD_PREVIEW_MEMO_SIZE_PX,
    zIndex: 20 + getPreviewMemoStackOrder(memo),
  }
  const imageAlt = `${memo.authorNickname}의 커뮤니티 메모 미리보기`

  return (
    <div
      className="absolute origin-center transition duration-300 ease-out"
      style={cardStyle}
    >
      <PostItNote
        shape="square"
        className={cn(
          'absolute inset-0 h-full w-full drop-shadow-[0_7px_9px_rgb(64_44_38_/_20%)] transition duration-300',
          isExpanded ? 'opacity-100' : 'opacity-78',
        )}
        style={{ color: memo.color }}
      />
      <div
        className={cn(
          'absolute inset-x-[7px] bottom-[8px] top-[10px] overflow-hidden rounded-[4px] bg-white/18 transition duration-300',
          isExpanded ? 'opacity-100' : 'opacity-42',
        )}
      >
        {memo.imageUrl ? (
          <Image
            src={memo.imageUrl}
            alt={imageAlt}
            fill
            sizes={`${WHITEBOARD_PREVIEW_MEMO_SIZE_PX}px`}
            unoptimized
            className={cn(
              'object-contain transition duration-300',
              isExpanded ? 'scale-105 opacity-100' : 'scale-95 opacity-40',
            )}
          />
        ) : (
          <div
            aria-hidden="true"
            className={cn(
              'flex h-full flex-col justify-center gap-[4px] px-[5px] transition duration-300',
              isExpanded ? 'opacity-78' : 'opacity-48',
            )}
          >
            <span className="h-[4px] rounded-full bg-white/70" />
            <span className="h-[4px] w-4/5 rounded-full bg-white/55" />
            <span className="h-[4px] w-3/5 rounded-full bg-white/45" />
          </div>
        )}
      </div>
    </div>
  )
}

export default function CommunityCanvasWhiteboardPreviewMesh({
  isExpanded,
}: {
  isExpanded: boolean
}) {
  const {
    hiddenMemoCount,
    isUsingFallbackPreview,
    previewMemos,
    status,
  } = useCommunityCanvasWhiteboardPreviewMemos()
  const isLoadingInitialPreview = status === 'loading' && isUsingFallbackPreview

  return (
    <Html
      center
      transform
      pointerEvents="none"
      position={WHITEBOARD_PREVIEW_POSITION}
      quaternion={WHITEBOARD_PREVIEW_QUATERNION}
      scale={WHITEBOARD_PREVIEW_WORLD_SCALE}
      zIndexRange={[60, 0]}
    >
      <div
        aria-hidden="true"
        className={cn(
          'relative overflow-hidden rounded-[23px] border border-white/45 bg-white/16 shadow-[0_13px_32px_rgb(106_96_142_/_18%)] backdrop-blur-[1px] transition duration-300 ease-out',
          isExpanded
            ? 'opacity-100 saturate-110'
            : 'opacity-72 saturate-75',
          isLoadingInitialPreview && 'animate-pulse',
        )}
        style={{
          height: WHITEBOARD_PREVIEW_HEIGHT_PX,
          pointerEvents: 'none',
          width: WHITEBOARD_PREVIEW_WIDTH_PX,
        }}
      >
        <div className="absolute inset-[10px] rounded-[18px] bg-[#f8fcff]/16 shadow-[inset_0_0_18px_rgb(255_255_255_/_28%)]" />
        <div className="absolute inset-[13px]">
          {previewMemos.map((memo) => (
            <WhiteboardPreviewMemoCard
              key={memo.id}
              isExpanded={isExpanded}
              memo={memo}
            />
          ))}
        </div>
        {hiddenMemoCount > 0 && (
          <div
            className={cn(
              'caption-b absolute right-[16px] top-[14px] z-[1100] rounded-full bg-white/72 px-[9px] py-[4px] text-[#66547f] shadow-[0_5px_11px_rgb(70_51_88_/_14%)] transition duration-300',
              isExpanded ? 'opacity-100' : 'opacity-0',
            )}
            style={{ fontSize: 22, lineHeight: '26px' }}
          >
            +{hiddenMemoCount}
          </div>
        )}
        <div
          className={cn(
            'absolute inset-x-[40px] bottom-[13px] z-[1100] flex items-center justify-center gap-[10px] rounded-full bg-white/82 px-[18px] py-[8px] text-center text-[#66547f] shadow-[0_8px_16px_rgb(76_54_92_/_16%)] transition duration-300',
            isExpanded
              ? 'translate-y-0 opacity-100'
              : 'translate-y-[8px] opacity-0',
          )}
          style={{
            fontFamily: 'var(--font-paperlogy)',
            fontSize: 28,
            fontWeight: 600,
            letterSpacing: '0.02em',
            lineHeight: '32px',
          }}
        >
          <span>커뮤니티 캔버스로</span>
          <ArrowRight aria-hidden size={24} strokeWidth={2.4} />
        </div>
      </div>
    </Html>
  )
}
