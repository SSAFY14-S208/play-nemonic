'use client'

import { Dialog } from '@base-ui/react/dialog'
import { ChevronLeft, ChevronRight, Sparkles, UsersRound, X } from 'lucide-react'
import { AnimatePresence, motion } from 'motion/react'
import Image from 'next/image'
import { useEffect, useState, type CSSProperties } from 'react'

import { cn } from '@/shared/libs'

import type { HowToPlayPanel, HowToPlayVisual, HowToPlayVisualImage } from './HowToPlayModal.types'

interface HowToPlayModalProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  panels: HowToPlayPanel[]
  title?: string
  subtitle?: string
  accentColor?: string
}

const DEFAULT_ACCENT_COLOR = 'var(--color-primary-1)'

function getAccentStyle(accentColor?: string): CSSProperties {
  return {
    '--how-to-play-accent': accentColor ?? DEFAULT_ACCENT_COLOR,
  } as CSSProperties
}

function ExampleImageFrame({
  image,
  label,
  className,
}: {
  image: HowToPlayVisualImage
  label?: string
  className?: string
}) {
  return (
    <figure
      className={cn(
        'relative min-h-0 overflow-hidden rounded-[var(--radius-lg)] border border-border-default bg-surface-default shadow-[0_10px_24px_rgb(71_68_112_/_12%)]',
        className,
      )}
    >
      {label && (
        <figcaption className="caption-b absolute left-3 top-3 z-10 rounded-full bg-surface-default/90 px-3 py-1 text-[var(--how-to-play-accent)] shadow-sm backdrop-blur-sm">
          {label}
        </figcaption>
      )}
      <Image
        src={image.src}
        alt={image.alt}
        fill
        sizes="(max-width: 768px) 80vw, 320px"
        className="object-contain"
      />
    </figure>
  )
}

function RoomVisual({ visual }: { visual: Extract<HowToPlayVisual, { type: 'room' }> }) {
  return (
    <div className="flex h-full min-h-[20rem] flex-col gap-4">
      <div className="rounded-[var(--radius-lg)] border border-border-default bg-surface-default p-4 shadow-[0_10px_24px_rgb(71_68_112_/_12%)]">
        <div className="flex items-center justify-between gap-3">
          <div>
            <p className="caption-b text-[var(--how-to-play-accent)]">ROOM CODE</p>
            <p className="h2-b mt-1 text-fg-primary">{visual.roomCode}</p>
          </div>
          <div className="grid size-12 place-items-center rounded-full bg-surface-subtle text-[var(--how-to-play-accent)]">
            <UsersRound className="size-6" aria-hidden />
          </div>
        </div>
        <div className="mt-4 grid grid-cols-3 gap-2">
          {visual.participants.map((participant) => (
            <span
              key={participant}
              className="caption-b rounded-full border border-border-default bg-surface-subtle px-3 py-2 text-center text-fg-primary"
            >
              {participant}
            </span>
          ))}
        </div>
      </div>

      <div className="grid min-h-0 flex-1 grid-cols-3 gap-2">
        {visual.images.map((image) => (
          <ExampleImageFrame key={image.alt} image={image} label={image.label} />
        ))}
      </div>

      <p className="caption-b rounded-[var(--radius-md)] bg-[var(--how-to-play-accent)] px-4 py-3 text-center text-fg-primary">
        {visual.caption}
      </p>
    </div>
  )
}

function SingleVisual({ visual }: { visual: Extract<HowToPlayVisual, { type: 'single' }> }) {
  return (
    <div className="flex h-full min-h-[20rem] flex-col gap-3">
      <div className="flex items-center justify-between gap-3">
        <span className="caption-b rounded-full bg-[var(--how-to-play-accent)] px-3 py-1 text-fg-primary">
          {visual.badge}
        </span>
        <span className="caption-b text-fg-secondary">{visual.frameLabel}</span>
      </div>
      <ExampleImageFrame image={visual.image} className="flex-1" />
      <p className="caption-b rounded-[var(--radius-md)] bg-surface-subtle px-4 py-3 text-center text-fg-primary">
        {visual.caption}
      </p>
    </div>
  )
}

function HandoffVisual({ visual }: { visual: Extract<HowToPlayVisual, { type: 'handoff' }> }) {
  return (
    <div className="flex h-full min-h-[20rem] flex-col gap-3">
      <div className="grid min-h-0 flex-1 grid-cols-[1fr_auto_1fr] items-center gap-3">
        <ExampleImageFrame image={visual.previousImage} label={visual.previousImage.label} />
        <div className="grid size-10 place-items-center rounded-full bg-[var(--how-to-play-accent)] text-fg-primary shadow-[0_8px_18px_rgb(71_68_112_/_18%)]">
          <ChevronRight className="size-5" aria-hidden />
        </div>
        <ExampleImageFrame image={visual.currentImage} label={visual.currentImage.label} />
      </div>
      <div className="rounded-[var(--radius-lg)] border border-dashed bg-surface-subtle px-4 py-3 text-center [border-color:var(--how-to-play-accent)]">
        <p className="caption-b text-[var(--how-to-play-accent)]">{visual.hintLabel}</p>
        <p className="caption-r mt-1 text-fg-secondary">{visual.caption}</p>
      </div>
    </div>
  )
}

function DrawingHintVisual({
  visual,
}: {
  visual: Extract<HowToPlayVisual, { type: 'drawing-hint' }>
}) {
  return (
    <div className="flex h-full min-h-[20rem] flex-col gap-3">
      <div className="relative min-h-0 flex-1 overflow-hidden rounded-[var(--radius-lg)] border border-border-default bg-surface-default shadow-[0_12px_28px_rgb(71_68_112_/_14%)]">
        <div className="relative h-[34%] min-h-24 overflow-hidden bg-surface-default">
          <span className="caption-b absolute left-4 top-4 z-10 rounded-full bg-[var(--how-to-play-accent)] px-4 py-1 text-fg-primary shadow-sm">
            {visual.hintLabel}
          </span>
          <Image
            src={visual.hintImage.src}
            alt={visual.hintImage.alt}
            fill
            sizes="(max-width: 768px) 80vw, 360px"
            className="object-cover object-bottom opacity-80"
          />
        </div>

        <div className="relative flex h-[66%] min-h-0 flex-col justify-between border-t border-dashed bg-surface-default px-5 pb-4 pt-5 [border-color:var(--how-to-play-accent)]">
          <p className="caption-b text-[var(--how-to-play-accent)]">{visual.instruction}</p>
          <figure className="relative mx-auto h-full min-h-36 w-full max-w-72">
            <Image
              src={visual.resultImage.src}
              alt={visual.resultImage.alt}
              fill
              sizes="(max-width: 768px) 70vw, 280px"
              className="object-contain"
            />
          </figure>
        </div>
      </div>

      <p className="caption-b rounded-[var(--radius-md)] bg-surface-subtle px-4 py-3 text-center text-fg-primary">
        {visual.caption}
      </p>
    </div>
  )
}

function HintVisual({ visual }: { visual: Extract<HowToPlayVisual, { type: 'hint' }> }) {
  return (
    <div className="relative flex h-full min-h-[20rem] flex-col gap-3">
      <ExampleImageFrame image={visual.image} className="flex-1" />
      <div className="rounded-[var(--radius-lg)] border bg-[var(--how-to-play-accent)] px-4 py-3 text-center text-fg-primary shadow-[0_8px_18px_rgb(71_68_112_/_14%)] [border-color:var(--how-to-play-accent)]">
        <p className="caption-b">{visual.hintLabel}</p>
        <p className="caption-r mt-1">{visual.caption}</p>
      </div>
    </div>
  )
}

function StackVisual({ visual }: { visual: Extract<HowToPlayVisual, { type: 'stack' }> }) {
  return (
    <div className="flex h-full min-h-[20rem] flex-col gap-3">
      <div className="grid min-h-0 flex-1 grid-rows-3 overflow-hidden rounded-[var(--radius-lg)] border border-border-default bg-surface-default shadow-[0_12px_28px_rgb(71_68_112_/_14%)]">
        {visual.images.map((image, imageIndex) => (
          <div
            key={image.alt}
            className={cn(
              'relative min-h-0 bg-surface-default',
              imageIndex > 0 && 'border-t border-border-default',
            )}
          >
            <span className="caption-b absolute left-3 top-3 z-10 rounded-full bg-surface-default/90 px-3 py-1 text-[var(--how-to-play-accent)] shadow-sm backdrop-blur-sm">
              {image.label}
            </span>
            <Image
              src={image.src}
              alt={image.alt}
              fill
              sizes="(max-width: 768px) 80vw, 320px"
              className="object-contain"
            />
          </div>
        ))}
      </div>
      <p className="caption-b rounded-[var(--radius-md)] bg-surface-subtle px-4 py-3 text-center text-fg-primary">
        {visual.caption}
      </p>
    </div>
  )
}

function FilmstripVisual({ visual }: { visual: Extract<HowToPlayVisual, { type: 'filmstrip' }> }) {
  return (
    <div className="flex h-full min-h-[20rem] flex-col gap-3">
      <div className="grid min-h-0 flex-1 grid-cols-2 grid-rows-3 gap-2 sm:grid-cols-3 sm:grid-rows-2">
        {visual.images.map((image) => (
          <ExampleImageFrame key={image.alt} image={image} label={image.label} />
        ))}
      </div>
      <p className="caption-b rounded-[var(--radius-md)] bg-surface-subtle px-4 py-3 text-center text-fg-primary">
        {visual.caption}
      </p>
    </div>
  )
}

function OnionSkinVisual({
  visual,
}: {
  visual: Extract<HowToPlayVisual, { type: 'onion-skin' }>
}) {
  return (
    <div className="flex h-full min-h-[20rem] flex-col gap-3">
      <div className="relative min-h-0 flex-1 overflow-hidden rounded-[var(--radius-lg)] border border-border-default bg-white shadow-[0_12px_28px_rgb(71_68_112_/_14%)]">
        <Image
          src={visual.hintImage.src}
          alt={visual.hintImage.alt}
          fill
          sizes="(max-width: 768px) 80vw, 360px"
          className="object-contain opacity-[0.36] blur-[1.4px] saturate-[0.78]"
        />
        <div className="absolute inset-0 bg-white/24" aria-hidden />
        <span className="caption-b absolute left-4 top-4 z-10 rounded-full bg-[var(--how-to-play-accent)] px-4 py-1 text-fg-primary shadow-sm">
          {visual.hintLabel}
        </span>
      </div>

      <div className="rounded-[var(--radius-lg)] border border-dashed bg-surface-subtle px-4 py-3 text-center [border-color:var(--how-to-play-accent)]">
        <p className="caption-b text-[var(--how-to-play-accent)]">{visual.instruction}</p>
        <p className="caption-r mt-1 text-fg-secondary">{visual.caption}</p>
      </div>
    </div>
  )
}

function AnimationVisual({ visual }: { visual: Extract<HowToPlayVisual, { type: 'animation' }> }) {
  const [frameIndex, setFrameIndex] = useState(0)
  const frameCount = visual.images.length

  useEffect(() => {
    if (frameCount <= 1) return

    const intervalId = window.setInterval(() => {
      setFrameIndex((currentFrameIndex) => (currentFrameIndex + 1) % frameCount)
    }, visual.frameIntervalMs ?? 220)

    return () => {
      window.clearInterval(intervalId)
    }
  }, [frameCount, visual.frameIntervalMs])

  const activeImage = visual.images[frameIndex] ?? visual.images[0]

  return (
    <div className="flex h-full min-h-[20rem] flex-col gap-3">
      <figure className="relative min-h-0 flex-1 overflow-hidden rounded-[var(--radius-lg)] border border-border-default bg-white shadow-[0_12px_28px_rgb(71_68_112_/_14%)]">
        {activeImage && (
          <Image
            key={activeImage.alt}
            src={activeImage.src}
            alt={activeImage.alt}
            fill
            sizes="(max-width: 768px) 80vw, 360px"
            className="object-contain"
          />
        )}
        <figcaption className="caption-b absolute left-4 top-4 z-10 rounded-full bg-[var(--how-to-play-accent)] px-4 py-1 text-fg-primary shadow-sm">
          {`${frameIndex + 1} / ${Math.max(frameCount, 1)}`}
        </figcaption>
      </figure>

      <div
        className="grid gap-1.5"
        style={{ gridTemplateColumns: `repeat(${Math.max(frameCount, 1)}, minmax(0, 1fr))` }}
      >
        {visual.images.map((image, imageIndex) => (
          <div
            key={image.alt}
            className={cn(
              'h-1.5 rounded-full transition-colors',
              imageIndex === frameIndex ? 'bg-[var(--how-to-play-accent)]' : 'bg-border-default',
            )}
            aria-hidden
          />
        ))}
      </div>

      <p className="caption-b rounded-[var(--radius-md)] bg-surface-subtle px-4 py-3 text-center text-fg-primary">
        {visual.caption}
      </p>
    </div>
  )
}

function PanelVisual({ visual }: { visual?: HowToPlayVisual }) {
  if (!visual) {
    return (
      <div className="grid h-full min-h-[20rem] place-items-center rounded-[var(--radius-lg)] border border-border-default bg-surface-subtle text-[var(--how-to-play-accent)]">
        <Sparkles className="size-12" aria-hidden />
      </div>
    )
  }

  if (visual.type === 'room') return <RoomVisual visual={visual} />
  if (visual.type === 'single') return <SingleVisual visual={visual} />
  if (visual.type === 'handoff') return <HandoffVisual visual={visual} />
  if (visual.type === 'drawing-hint') return <DrawingHintVisual visual={visual} />
  if (visual.type === 'hint') return <HintVisual visual={visual} />
  if (visual.type === 'stack') return <StackVisual visual={visual} />
  if (visual.type === 'filmstrip') return <FilmstripVisual visual={visual} />
  if (visual.type === 'onion-skin') return <OnionSkinVisual visual={visual} />
  return <AnimationVisual visual={visual} />
}

export function HowToPlayModal({
  open,
  onOpenChange,
  panels,
  title = '게임 설명',
  subtitle = '한 장씩 넘기며 릴레이 드로잉 흐름을 확인해요.',
  accentColor,
}: HowToPlayModalProps) {
  const [index, setIndex] = useState(0)
  const totalCount = panels.length
  const currentPanel = panels[index] ?? panels[0]
  const isFirst = index === 0
  const isLast = index === totalCount - 1
  const accentStyle = getAccentStyle(accentColor)

  useEffect(() => {
    ;(async () => {
      if (!open) setIndex(0)
    })()
  }, [open])

  useEffect(() => {
    if (!open) return
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'ArrowLeft') setIndex((currentIndex) => Math.max(0, currentIndex - 1))
      if (event.key === 'ArrowRight') {
        setIndex((currentIndex) => Math.min(totalCount - 1, currentIndex + 1))
      }
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [open, totalCount])

  if (!currentPanel) return null

  const goPrev = () => setIndex((currentIndex) => Math.max(0, currentIndex - 1))
  const goNext = () => setIndex((currentIndex) => Math.min(totalCount - 1, currentIndex + 1))

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
          className="fixed inset-0 z-[var(--z-overlay)] bg-black/35 backdrop-blur-[2px]"
        />
        <Dialog.Popup
          render={
            <motion.div
              initial={{ opacity: 0, y: 24, scale: 0.98 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              transition={{ duration: 0.3, ease: [0.16, 1, 0.3, 1] }}
            />
          }
          className="fixed left-1/2 top-[calc(env(safe-area-inset-top)+0.75rem)] z-[var(--z-modal)] max-h-[calc(100dvh-1.5rem)] w-[min(820px,calc(100vw-1.5rem))] -translate-x-1/2 overflow-hidden rounded-[var(--radius-xl)] border border-border-default bg-surface-default shadow-[0_28px_70px_rgb(71_68_112_/_24%)] sm:top-1/2 sm:-translate-y-1/2"
          style={accentStyle}
        >
          <header className="flex items-center justify-between border-b border-border-default bg-surface-default px-5 py-4 sm:px-7">
            <div>
              <Dialog.Title className="h4-b text-fg-primary">{title}</Dialog.Title>
              <p className="caption-r mt-1 text-fg-secondary">
                {subtitle}
              </p>
            </div>
            <Dialog.Close
              aria-label="닫기"
              className="grid size-9 cursor-pointer place-items-center rounded-full text-fg-secondary transition-colors hover:bg-surface-subtle hover:text-fg-primary"
            >
              <X className="size-5" />
            </Dialog.Close>
          </header>

          <div className="max-h-[calc(100dvh-7rem)] overflow-y-auto px-4 py-4 sm:px-7 sm:py-6">
            <AnimatePresence initial={false} mode="wait">
              <motion.section
                key={currentPanel.id}
                className="grid gap-5 md:grid-cols-[minmax(0,0.95fr)_minmax(0,1.05fr)]"
                initial={{ opacity: 0, x: 28 }}
                animate={{ opacity: 1, x: 0 }}
                exit={{ opacity: 0, x: -28 }}
                transition={{ duration: 0.25, ease: 'easeOut' }}
              >
                <div className="rounded-[var(--radius-xl)] border border-border-default bg-surface-subtle p-3">
                  <PanelVisual visual={currentPanel.visual} />
                </div>

                <article className="flex min-h-[20rem] flex-col justify-between rounded-[var(--radius-xl)] border border-border-default bg-surface-default p-5">
                  <div>
                    <span className="caption-b inline-flex rounded-full bg-[var(--how-to-play-accent)] px-3 py-1 text-fg-primary">
                      {currentPanel.eyebrow ?? `#${index + 1}`}
                    </span>
                    <h2 className="h2-b mt-4 text-fg-primary">{currentPanel.title}</h2>
                    <p className="body-r mt-4 text-fg-secondary">{currentPanel.description}</p>
                  </div>

                  {currentPanel.bullets && (
                    <ul className="mt-5 grid gap-2">
                      {currentPanel.bullets.map((bullet) => (
                        <li
                          key={bullet}
                          className="body-b flex items-start gap-2 rounded-[var(--radius-md)] bg-surface-subtle px-3 py-2 text-fg-primary"
                        >
                          <span className="mt-1 size-2 shrink-0 rounded-full bg-[var(--how-to-play-accent)]" />
                          <span>{bullet}</span>
                        </li>
                      ))}
                    </ul>
                  )}
                </article>
              </motion.section>
            </AnimatePresence>

            <div className="mt-5 flex items-center justify-between gap-4">
              <span className="caption-b text-fg-secondary">{`${index + 1} / ${totalCount}`}</span>
              <div className="flex items-center gap-2" role="tablist" aria-label="설명 페이지">
                {panels.map((panel, panelIndex) => {
                  const isActive = panelIndex === index
                  return (
                    <button
                      key={panel.id}
                      type="button"
                      role="tab"
                      aria-selected={isActive}
                      aria-label={`${panelIndex + 1}번째 설명으로 이동`}
                      onClick={() => setIndex(panelIndex)}
                      className={cn(
                        'h-2 cursor-pointer rounded-full transition-all',
                        isActive
                          ? 'w-8 bg-[var(--how-to-play-accent)]'
                          : 'w-2 bg-border-default hover:bg-[var(--how-to-play-accent)]',
                      )}
                    />
                  )
                })}
              </div>
            </div>

            <div className="mt-5 flex justify-between gap-3">
              <button
                type="button"
                onClick={goPrev}
                disabled={isFirst}
                aria-label="이전 설명"
                className="body-b inline-flex min-h-11 cursor-pointer items-center gap-1.5 rounded-[var(--radius-md)] border border-border-default bg-surface-default px-4 text-fg-primary transition-all hover:brightness-95 disabled:cursor-not-allowed disabled:opacity-45 disabled:hover:brightness-100"
              >
                <ChevronLeft className="size-4" aria-hidden />
                이전
              </button>
              {isLast ? (
                <Dialog.Close className="body-b inline-flex min-h-11 cursor-pointer items-center gap-1.5 rounded-[var(--radius-md)] bg-[var(--how-to-play-accent)] px-5 text-fg-primary transition-all hover:brightness-105">
                  확인했어요
                </Dialog.Close>
              ) : (
                <button
                  type="button"
                  onClick={goNext}
                  aria-label="다음 설명"
                  className="body-b inline-flex min-h-11 cursor-pointer items-center gap-1.5 rounded-[var(--radius-md)] bg-[var(--how-to-play-accent)] px-5 text-fg-primary transition-all hover:brightness-105"
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
