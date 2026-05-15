'use client'

import { Dialog } from '@base-ui/react/dialog'
import { ChevronLeft, ChevronRight, X } from 'lucide-react'
import { AnimatePresence, motion } from 'motion/react'
import { useEffect, useState } from 'react'

import { cn } from '@/shared/libs'

import type { HowToPlayPanel } from './HowToPlayModal.types'

interface HowToPlayModalProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  panels: HowToPlayPanel[]
  title?: string
  /** 버튼·인디케이터에 적용할 테마 색상. 미지정 시 디자인 시스템 primary 사용. */
  accentColor?: string
}

// 한 번에 1컷씩 만화 컷을 보여주는 캐러셀 모달.
// 이전/다음 버튼, 점 인디케이터, ←/→ 키보드로 컷 이동이 가능하다.
// 실제 만화 일러스트는 호출 측에서 panels에 채워주거나, 채워지기 전까지는
// 자리 표시로 번호 + 제목 + 설명을 렌더한다.
export function HowToPlayModal({
  open,
  onOpenChange,
  panels,
  title = '게임 설명',
  accentColor,
}: HowToPlayModalProps) {
  const [index, setIndex] = useState(0)
  const totalCount = panels.length
  const currentPanel = panels[index]
  const isFirst = index === 0
  const isLast = index === totalCount - 1

  // 모달이 닫히면 다음에 다시 열렸을 때 1컷부터 시작하도록 인덱스 초기화.
  useEffect(() => {
    (async () => {
      if (!open) setIndex(0)
    })()
  }, [open])

  // 키보드 ←/→ 로도 컷 이동 가능하게.
  useEffect(() => {
    if (!open) return
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'ArrowLeft') setIndex((cur) => Math.max(0, cur - 1))
      if (event.key === 'ArrowRight')
        setIndex((cur) => Math.min(totalCount - 1, cur + 1))
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [open, totalCount])

  const goPrev = () => setIndex((cur) => Math.max(0, cur - 1))
  const goNext = () => setIndex((cur) => Math.min(totalCount - 1, cur + 1))

  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Portal>
        <Dialog.Backdrop
          render={
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              transition={{ duration: 0.2 }}
            />
          }
          className="fixed inset-0 z-[var(--z-overlay)] bg-black/30"
        />
        <Dialog.Popup
          render={
            <motion.div
              initial={{ opacity: 0, y: 24 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.3, ease: [0.16, 1, 0.3, 1] }}
            />
          }
          className="fixed left-1/2 top-1/2 z-[var(--z-modal)] w-[min(560px,calc(100vw-2rem))] -translate-x-1/2 -translate-y-1/2 overflow-hidden rounded-[var(--radius-xl)] bg-surface-default shadow-lg"
        >
          <header className="flex items-center justify-between border-b border-border-default px-5 py-4">
            <Dialog.Title className="h4-b text-fg-primary">{title}</Dialog.Title>
            <Dialog.Close
              aria-label="닫기"
              className="grid size-8 cursor-pointer place-items-center rounded-[var(--radius-md)] text-fg-secondary transition-colors hover:bg-surface-subtle hover:text-fg-primary"
            >
              <X className="size-4" />
            </Dialog.Close>
          </header>

          <div className="flex flex-col gap-5 px-5 py-5 sm:px-7 sm:py-6">
            {/* 컷 영역 — 실제 만화 이미지는 추후 추가 예정.
                자리 표시로 번호 + 제목 + 설명을 큼직하게 렌더. */}
            <div
              className="relative aspect-[4/3] w-full overflow-hidden rounded-[var(--radius-lg)] border border-border-default bg-surface-subtle"
              aria-roledescription="만화 컷"
              aria-label={`${index + 1}번째 컷 — ${currentPanel.title}`}
            >
              <AnimatePresence initial={false} mode="wait">
                <motion.div
                  key={currentPanel.id}
                  className="absolute inset-0 flex flex-col items-center justify-center gap-3 px-6 text-center"
                  initial={{ opacity: 0, x: 24 }}
                  animate={{ opacity: 1, x: 0 }}
                  exit={{ opacity: 0, x: -24 }}
                  transition={{ duration: 0.25, ease: 'easeOut' }}
                >
                  <span
                    className="caption-b text-primary-2"
                    style={accentColor ? { color: accentColor } : undefined}
                  >
                    {`#${index + 1}`}
                  </span>
                  <p
                    className="text-fg-primary"
                    style={{
                      fontSize: 'clamp(1.25rem, 3vw, 1.75rem)',
                      fontWeight: 700,
                      lineHeight: 1.3,
                    }}
                  >
                    {currentPanel.title}
                  </p>
                  <p className="body-r max-w-[36ch] text-fg-secondary">
                    {currentPanel.description}
                  </p>
                </motion.div>
              </AnimatePresence>
            </div>

            {/* 인디케이터 + 카운터 */}
            <div className="flex items-center justify-between">
              <span className="caption-r text-fg-secondary">
                {`${index + 1} / ${totalCount}`}
              </span>
              <div
                className="flex items-center gap-2"
                role="tablist"
                aria-label="컷 선택"
              >
                {panels.map((panel, panelIndex) => {
                  const isActive = panelIndex === index
                  return (
                    <button
                      key={panel.id}
                      type="button"
                      role="tab"
                      aria-selected={isActive}
                      aria-label={`${panelIndex + 1}번째 컷으로 이동`}
                      onClick={() => setIndex(panelIndex)}
                      className={cn(
                        'h-2 cursor-pointer rounded-full transition-all',
                        isActive
                          ? 'w-6 bg-primary-1'
                          : 'w-2 bg-border-default hover:bg-fg-secondary',
                      )}
                      style={
                        isActive && accentColor
                          ? { backgroundColor: accentColor }
                          : undefined
                      }
                    />
                  )
                })}
              </div>
            </div>

            {/* 이전/다음 버튼 */}
            <div className="flex justify-between gap-3">
              <button
                type="button"
                onClick={goPrev}
                disabled={isFirst}
                aria-label="이전 컷"
                className="body-b inline-flex min-h-11 cursor-pointer items-center gap-1.5 rounded-[var(--radius-md)] border border-border-default bg-surface-default px-4 text-fg-primary transition-all hover:brightness-95 disabled:cursor-not-allowed disabled:opacity-45 disabled:hover:brightness-100"
              >
                <ChevronLeft className="size-4" aria-hidden />
                이전
              </button>
              {isLast ? (
                <Dialog.Close
                  className="body-b inline-flex min-h-11 cursor-pointer items-center gap-1.5 rounded-[var(--radius-md)] bg-primary-1 px-5 text-fg-inverse transition-all hover:brightness-105"
                  style={accentColor ? { backgroundColor: accentColor } : undefined}
                >
                  닫기
                </Dialog.Close>
              ) : (
                <button
                  type="button"
                  onClick={goNext}
                  aria-label="다음 컷"
                  className="body-b inline-flex min-h-11 cursor-pointer items-center gap-1.5 rounded-[var(--radius-md)] bg-primary-1 px-5 text-fg-inverse transition-all hover:brightness-105"
                  style={accentColor ? { backgroundColor: accentColor } : undefined}
                >
                  다음
                  <ChevronRight className="size-4" aria-hidden />
                </button>
              )}
            </div>
          </div>
        </Dialog.Popup>
      </Dialog.Portal>
    </Dialog.Root>
  )
}
