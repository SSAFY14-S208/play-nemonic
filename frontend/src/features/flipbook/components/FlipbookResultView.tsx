'use client'

import { Download, Pause, Play, Share2 } from 'lucide-react'
import type { DrawingLine } from '@/shared/types'
import { cn } from '@/shared/libs'
import {
  FLIPBOOK_BACKGROUND_COLOR,
  FLIPBOOK_BOARD_SIZE,
  FLIPBOOK_PARTICIPANTS,
  FLIPBOOK_TOPIC,
} from '../constants'
import type { FlipbookFrame } from '../types'

interface FlipbookResultViewProps {
  frames: FlipbookFrame[]
  activeFrame: FlipbookFrame | null
  resultFrameIndex: number
  isGifPlaying: boolean
  canGoPreviousResultFrame: boolean
  canGoNextResultFrame: boolean
  onToggleGifPlaying: (isPlaying: boolean) => void
  onShowPreviousFrame: () => void
  onShowNextFrame: () => void
  onCreateAnother: () => void
}

export default function FlipbookResultView({
  frames,
  activeFrame,
  resultFrameIndex,
  isGifPlaying,
  canGoPreviousResultFrame,
  canGoNextResultFrame,
  onToggleGifPlaying,
  onShowPreviousFrame,
  onShowNextFrame,
  onCreateAnother,
}: FlipbookResultViewProps) {
  return (
    <section className="min-h-screen bg-flipbook-background px-6 py-10 text-flipbook-ink lg:px-12 lg:py-14">
      <div className="mx-auto w-full max-w-[1312px]">
        <div className="flex min-h-9 items-center justify-between gap-4">
          <div className="flex flex-wrap items-center gap-2">
            <span className="caption-b text-flipbook-deep">2026.04.28 ·</span>
            <span className="caption-b rounded-full bg-flipbook-light px-3 py-1 text-flipbook-ink">
              🐱 고양이 님의 앨범
            </span>
          </div>
          <div className="flex items-center gap-1.5">
            {frames.map((frame, frameIndex) => (
              <span
                key={frame.id}
                className={cn(
                  'size-[9px] rounded-full bg-flipbook-light',
                  frameIndex <= resultFrameIndex && 'bg-flipbook-deep',
                )}
              />
            ))}
            <span className="caption-b ml-1 text-flipbook-deep">
              {frames.length === 0 ? 0 : resultFrameIndex + 1} / {frames.length}
            </span>
          </div>
        </div>

        <div className="mt-5 grid gap-8 lg:grid-cols-[minmax(0,880px)_400px] lg:items-start">
          <section className="min-h-[716px] overflow-hidden rounded-[18px] bg-flipbook-paper px-5 py-6 shadow-[0_14px_28px_var(--color-flipbook-shadow)] md:px-7">
            <header className="mb-4 flex min-h-[60px] items-end justify-between gap-4">
              <div>
                <p className="caption-b text-flipbook-deep">STEP {resultFrameIndex + 1}</p>
                <h1 className="h2-b mt-1 flex flex-wrap items-center gap-2 text-flipbook-ink">
                  <span className="rounded-full bg-flipbook-light px-3 py-0.5">
                    {activeFrame?.drawnBy ?? '친구'} 님의 작품
                  </span>
                </h1>
              </div>
              <button
                type="button"
                aria-label={isGifPlaying ? 'GIF 재생 멈춤' : 'GIF 재생 시작'}
                onClick={() => onToggleGifPlaying(!isGifPlaying)}
                className="grid size-11 place-items-center rounded-full bg-flipbook-primary text-flipbook-ink"
              >
                {isGifPlaying ? (
                  <Pause className="size-5" aria-hidden />
                ) : (
                  <Play className="size-5" aria-hidden />
                )}
              </button>
            </header>

            <div className="relative h-[460px] overflow-hidden rounded-[14px] border-[1.5px] border-flipbook-light bg-flipbook-paper">
              <FrameDrawing lines={activeFrame?.lines ?? []} />
              <div className="caption-b absolute right-4 top-4 flex items-center gap-2 rounded-full border border-flipbook-light bg-flipbook-paper py-1.5 pl-2 pr-4 text-flipbook-deep shadow-[0_6px_7px_var(--color-flipbook-shadow)]">
                <span className="grid size-8 place-items-center rounded-full bg-flipbook-light">
                  {activeFrame?.participantAvatar ?? '📖'}
                </span>
                {activeFrame?.drawnBy ?? '아직 프레임 없음'}
              </div>
            </div>

            <div className="mt-4 flex min-h-11 items-center gap-3">
              <button
                type="button"
                onClick={onShowPreviousFrame}
                disabled={!canGoPreviousResultFrame}
                className="body-b min-h-11 rounded-[12px] border-[1.5px] border-flipbook-light bg-flipbook-paper px-4 text-flipbook-deep disabled:opacity-45"
              >
                ◀ 이전
              </button>
              <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-flipbook-result-soft">
                <div
                  className="h-full rounded-full bg-flipbook-deep"
                  style={{
                    width:
                      frames.length > 0
                        ? `${((resultFrameIndex + 1) / frames.length) * 100}%`
                        : '0%',
                  }}
                />
              </div>
              <button
                type="button"
                onClick={onShowNextFrame}
                disabled={!canGoNextResultFrame}
                className="body-b min-h-11 rounded-[12px] bg-flipbook-primary px-4 text-flipbook-ink disabled:opacity-45"
              >
                다음 ▶
              </button>
            </div>
          </section>

          <aside className="flex min-h-[716px] flex-col gap-4">
            <section className="rounded-[18px] border border-flipbook-light bg-flipbook-paper px-5 py-4">
              <p className="caption-b text-flipbook-deep">이번엔 {FLIPBOOK_PARTICIPANTS.length}명이 모였어요</p>
              <h2 className="h4-b mt-2 text-flipbook-ink">{FLIPBOOK_TOPIC}</h2>
              <div className="mt-4 grid gap-2">
                {frames.map((frame, frameIndex) => (
                  <button
                    key={frame.id}
                    type="button"
                    className={cn(
                      'flex min-h-11 items-center gap-3 rounded-[14px] bg-flipbook-result-soft px-3.5',
                      frameIndex === resultFrameIndex &&
                        'border-[1.5px] border-flipbook-deep bg-flipbook-paper shadow-[0_4px_5px_var(--color-flipbook-shadow)]',
                    )}
                  >
                    <span className="h4-b">{frame.participantAvatar}</span>
                    <span className="body-b text-flipbook-ink">{frame.drawnBy}</span>
                    <span className="caption-b ml-auto text-flipbook-deep">
                      {frame.index + 1}장
                    </span>
                  </button>
                ))}
              </div>
            </section>

            <section className="rounded-[18px] border border-flipbook-light bg-flipbook-paper p-5">
              <p className="caption-b text-flipbook-deep">GIF 다운로드 URL</p>
              <p className="caption-r mt-2 rounded-[12px] bg-flipbook-result-soft p-3 text-flipbook-muted">
                /api/mock/flipbook/flipbook_uuid.gif
              </p>
            </section>

            <div className="mt-auto grid min-h-[60px] gap-3 sm:grid-cols-2">
              <button
                type="button"
                className="body-b inline-flex items-center justify-center gap-2 rounded-[14px] border-[1.5px] border-flipbook-light bg-flipbook-paper px-5 text-flipbook-ink"
              >
                <Download className="size-4" aria-hidden />
                GIF 저장
              </button>
              <button
                type="button"
                className="body-b inline-flex items-center justify-center gap-2 rounded-[14px] border-[1.5px] border-flipbook-primary bg-flipbook-primary px-5 text-flipbook-ink shadow-[0_4px_10px_var(--color-flipbook-shadow)]"
              >
                <Share2 className="size-4" aria-hidden />
                공유하기
              </button>
            </div>

            <button
              type="button"
              onClick={onCreateAnother}
              className="caption-b self-center text-flipbook-muted"
            >
              새 플립북 만들기
            </button>
          </aside>
        </div>
      </div>
    </section>
  )
}

function FrameDrawing({ lines }: { lines: DrawingLine[] }) {
  const hasLines = lines.length > 0

  return (
    <svg
      className="h-full w-full"
      viewBox={`0 0 ${FLIPBOOK_BOARD_SIZE.width} ${FLIPBOOK_BOARD_SIZE.height}`}
      role="img"
      aria-label="플립북 프레임"
      preserveAspectRatio="xMidYMid meet"
    >
      <rect width={FLIPBOOK_BOARD_SIZE.width} height={FLIPBOOK_BOARD_SIZE.height} fill={FLIPBOOK_BACKGROUND_COLOR} />
      {Array.from({ length: 36 }).map((unusedRow, rowIndex) =>
        Array.from({ length: 43 }).map((unusedColumn, columnIndex) => (
          <circle
            key={`${unusedRow}-${unusedColumn}-${rowIndex}-${columnIndex}`}
            cx={12 + columnIndex * 20}
            cy={12 + rowIndex * 20}
            r={1}
            fill="#ffa8b8"
            opacity={0.54}
          />
        )),
      )}
      {lines.map((line) => {
        if (line.kind === 'fill' && line.imageDataUrl) {
          return (
            <image
              key={line.id}
              href={line.imageDataUrl}
              x={0}
              y={0}
              width={FLIPBOOK_BOARD_SIZE.width}
              height={FLIPBOOK_BOARD_SIZE.height}
            />
          )
        }

        if (line.kind === 'fill') {
          return (
            <polygon
              key={line.id}
              points={line.points.map((point) => `${point.x},${point.y}`).join(' ')}
              fill={line.color}
            />
          )
        }

        return (
          <polyline
            key={line.id}
            points={line.points.map((point) => `${point.x},${point.y}`).join(' ')}
            fill="none"
            stroke={
              line.compositeOperation === 'destination-out'
                ? FLIPBOOK_BACKGROUND_COLOR
                : line.color
            }
            strokeWidth={line.strokeWidth}
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        )
      })}
      {!hasLines && (
        <text
          x={FLIPBOOK_BOARD_SIZE.width / 2}
          y={FLIPBOOK_BOARD_SIZE.height / 2}
          textAnchor="middle"
          dominantBaseline="middle"
          fill="#ac626a"
          fontFamily="Pretendard Variable"
          fontSize={18}
          fontWeight={700}
        >
          빈 프레임은 compact 처리되어 결과에서 빠져요
        </text>
      )}
    </svg>
  )
}
