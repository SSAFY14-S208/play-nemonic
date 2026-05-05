'use client'

import Image from 'next/image'
import { ArrowRight, Download, Share2, X } from 'lucide-react'
import { PHONE_GALLERY_ITEM_STYLES } from '../constants'
import type { PhoneGalleryItem } from '../types'

interface PhoneGalleryItemSheetProps {
  item: PhoneGalleryItem
  onClose: () => void
}

function PhoneGalleryPreview({ item }: { item: PhoneGalleryItem }) {
  const itemStyle = PHONE_GALLERY_ITEM_STYLES[item.kind]

  if (item.imageDataUrl) {
    return (
      <Image
        src={item.imageDataUrl}
        alt={`${item.title} 미리보기`}
        fill
        unoptimized
        className="object-contain"
      />
    )
  }

  return (
    <div className="relative flex h-full w-full items-center justify-center">
      <div
        className="h-28 w-36 rounded-[1.25rem] shadow-[0_0.75rem_1.5rem_rgba(0,0,0,0.12)]"
        style={{ background: itemStyle.color }}
      />
      <div className="absolute bottom-5 flex gap-1.5">
        {Array.from({ length: 8 }).map((_, dotIndex) => (
          <span
            key={`preview-dot-${dotIndex}`}
            className="h-1.5 w-1.5 rounded-full bg-white/80"
          />
        ))}
      </div>
    </div>
  )
}

export function PhoneGalleryItemSheet({
  item,
  onClose,
}: PhoneGalleryItemSheetProps) {
  const itemStyle = PHONE_GALLERY_ITEM_STYLES[item.kind]

  return (
    <div className="absolute inset-0 z-30 flex items-end">
      <button
        type="button"
        aria-label="아이템 상세 닫기"
        className="absolute inset-0 bg-black/35 backdrop-blur-[2px]"
        onClick={onClose}
      />
      <section className="relative w-full rounded-t-[1.75rem] bg-surface-default px-6 pb-8 pt-3 shadow-[0_-1rem_2rem_rgba(0,0,0,0.16)]">
        <div className="mx-auto mb-5 h-1 w-10 rounded-full bg-border-default" />
        <header className="mb-3 flex items-center justify-between">
          <span
            className="caption-b inline-flex items-center gap-1 rounded-full px-3 py-2"
            style={{
              background: itemStyle.background,
              color: itemStyle.color,
            }}
          >
            <span
              className="h-2 w-2 rounded-full"
              style={{ background: itemStyle.color }}
            />
            {itemStyle.label}
          </span>
          <button
            type="button"
            aria-label="닫기"
            onClick={onClose}
            className="flex h-10 w-10 items-center justify-center rounded-full bg-surface-subtle text-fg-secondary transition hover:text-fg-primary"
          >
            <X className="h-5 w-5" />
          </button>
        </header>

        <div
          className="relative mb-4 h-56 overflow-hidden rounded-[var(--radius-xl)]"
          style={{ background: itemStyle.background }}
        >
          <PhoneGalleryPreview item={item} />
          {item.badgeLabel && (
            <span className="caption-b absolute left-4 top-4 rounded-full bg-white/85 px-3 py-1 text-fg-primary">
              {item.badgeLabel}
            </span>
          )}
        </div>

        <h3 className="h3-b text-fg-primary">{item.title}</h3>
        <p className="caption-r mt-1 text-fg-secondary">
          {item.createdAtLabel}
          {item.contributorLabel ? ` · ${item.contributorLabel}` : ''}
        </p>

        <div className="mt-5 grid grid-cols-2 gap-3">
          <button
            type="button"
            className="body-b flex h-11 items-center justify-center gap-2 rounded-[var(--radius-md)] bg-surface-subtle text-fg-primary transition hover:bg-primary-5"
          >
            <Download className="h-4 w-4" />
            저장
          </button>
          <button
            type="button"
            className="body-b flex h-11 items-center justify-center gap-2 rounded-[var(--radius-md)] bg-surface-subtle text-fg-primary transition hover:bg-primary-5"
          >
            <Share2 className="h-4 w-4" />
            공유
          </button>
        </div>

        <button
          type="button"
          className="body-l-b mt-4 flex h-14 w-full items-center justify-center gap-3 rounded-[var(--radius-md)] bg-primary-1 text-fg-inverse shadow-[0_0.5rem_1rem_rgba(67,99,225,0.22)] transition hover:-translate-y-0.5"
        >
          커뮤니티 캔버스에 붙이기
          <ArrowRight className="h-5 w-5" />
        </button>
        <p className="caption-r mt-3 text-center text-fg-secondary">
          벽에 메모지로 부착됩니다 · 누구나 볼 수 있어요
        </p>
      </section>
    </div>
  )
}
