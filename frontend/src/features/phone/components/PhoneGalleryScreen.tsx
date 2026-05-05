'use client'

import Image from 'next/image'
import { ArrowLeft, ChevronDown, Search } from 'lucide-react'
import { cn } from '@/shared/libs'
import {
  PHONE_COLORS,
  PHONE_GALLERY_FILTERS,
  PHONE_GALLERY_ITEM_STYLES,
} from '../constants'
import { usePhoneGallery } from '../hooks'
import type { PhoneGalleryItem } from '../types'
import { PhoneGalleryItemSheet } from './PhoneGalleryItemSheet'

interface PhoneGalleryScreenProps {
  galleryItems: PhoneGalleryItem[]
  selectedItem: PhoneGalleryItem | null
  onBack: () => void
  onCloseItem: () => void
  onSelectItem: (itemId: string) => void
}

function PhoneGalleryCard({
  item,
  onSelectItem,
}: {
  item: PhoneGalleryItem
  onSelectItem: (itemId: string) => void
}) {
  const itemStyle = PHONE_GALLERY_ITEM_STYLES[item.kind]

  return (
    <button
      type="button"
      onClick={() => onSelectItem(item.id)}
      className="group min-h-[12.5rem] overflow-hidden rounded-[var(--radius-xl)] border border-border-default bg-white p-2 text-left shadow-[0_0.4rem_1.1rem_rgba(77,83,103,0.08)] transition hover:-translate-y-0.5 hover:shadow-[0_0.7rem_1.4rem_rgba(77,83,103,0.12)] focus-visible:outline focus-visible:outline-3 focus-visible:outline-primary-2"
    >
      <div
        className="relative mb-3 h-32 overflow-hidden rounded-[var(--radius-lg)]"
        style={{ background: itemStyle.background }}
      >
        {item.imageDataUrl ? (
          <Image
            src={item.imageDataUrl}
            alt={`${item.title} 미리보기`}
            fill
            unoptimized
            className="object-cover"
          />
        ) : (
          <div className="absolute inset-0 flex items-center justify-center">
            <div
              className="h-16 w-20 rounded-[var(--radius-md)] opacity-85 shadow-[0_0.6rem_1rem_rgba(0,0,0,0.12)]"
              style={{ background: itemStyle.color }}
            />
          </div>
        )}
        <span
          className="caption-b absolute left-3 top-3 inline-flex items-center gap-1 rounded-full bg-white/80 px-2 py-1"
          style={{ color: itemStyle.color }}
        >
          <span
            className="h-2 w-2 rounded-full"
            style={{ background: itemStyle.color }}
          />
          {itemStyle.label}
        </span>
        {item.badgeLabel && (
          <span className="caption-b absolute right-2 top-2 rounded-full bg-white/85 px-2 py-1 text-fg-primary">
            {item.badgeLabel}
          </span>
        )}
      </div>
      <h3 className="body-b line-clamp-1 px-1 text-fg-primary">
        {item.title}
      </h3>
      <p className="caption-r mt-1 px-1 text-fg-secondary">
        {item.createdAtLabel}
      </p>
    </button>
  )
}

export function PhoneGalleryScreen({
  galleryItems,
  selectedItem,
  onBack,
  onCloseItem,
  onSelectItem,
}: PhoneGalleryScreenProps) {
  const {
    activeFilterKey,
    filteredGalleryItems,
    setActiveFilterKey,
  } = usePhoneGallery(galleryItems)

  return (
    <div className="relative flex h-full flex-col bg-surface-default pt-12 text-fg-primary">
      <header
        className="flex h-14 items-center justify-between border-b border-border-default px-3"
        style={{ background: PHONE_COLORS.drawingPanel }}
      >
        <button
          type="button"
          aria-label="홈으로 돌아가기"
          onClick={onBack}
          className="flex h-11 w-11 items-center justify-center rounded-full text-fg-primary transition hover:bg-white focus-visible:outline focus-visible:outline-3 focus-visible:outline-primary-2"
        >
          <ArrowLeft className="h-6 w-6" />
        </button>
        <h2 className="h4-b">내 갤러리</h2>
        <button
          type="button"
          aria-label="갤러리 검색"
          className="flex h-11 w-11 items-center justify-center rounded-full text-fg-secondary transition hover:bg-white"
        >
          <Search className="h-5 w-5" />
        </button>
      </header>

      <div className="px-6 pb-3 pt-4">
        <div className="mb-3 flex items-center justify-between gap-3">
          <div className="flex items-baseline gap-3">
            <p className="body-b text-fg-primary">
              총 {galleryItems.length}개
            </p>
            <p className="caption-r text-fg-secondary">
              최근 활동: 방금 전
            </p>
          </div>
          <button
            type="button"
            className="caption-b flex h-8 items-center gap-1 rounded-full bg-surface-subtle px-3 text-fg-secondary"
          >
            최신순
            <ChevronDown className="h-3.5 w-3.5" />
          </button>
        </div>

        <div className="flex gap-2 overflow-x-auto pb-1">
          {PHONE_GALLERY_FILTERS.map(({ key, label }) => {
            const isActive = activeFilterKey === key
            const filterStyle =
              key === 'all' ? null : PHONE_GALLERY_ITEM_STYLES[key]

            return (
              <button
                key={key}
                type="button"
                onClick={() => setActiveFilterKey(key)}
                className={cn(
                  'caption-b flex h-9 shrink-0 items-center gap-1.5 rounded-full border px-3 transition',
                  isActive
                    ? 'border-fg-primary bg-fg-primary text-fg-inverse'
                    : 'border-border-default bg-white text-fg-secondary hover:bg-surface-subtle',
                )}
              >
                {filterStyle && (
                  <span
                    className="h-2 w-2 rounded-full"
                    style={{ background: filterStyle.color }}
                  />
                )}
                {label}
              </button>
            )
          })}
        </div>
      </div>

      <main className="flex-1 overflow-y-auto px-6 pb-8">
        {filteredGalleryItems.length === 0 ? (
          <div className="flex h-full flex-col items-center justify-center text-center">
            <p className="h3-b text-fg-primary">아직 저장된 카드가 없어요</p>
            <p className="body-r mt-2 text-fg-secondary">
              네모닉 그림판에서 하나 만들어 볼까요?
            </p>
          </div>
        ) : (
          <div className="grid grid-cols-2 gap-4">
            {filteredGalleryItems.map((item) => (
              <PhoneGalleryCard
                key={item.id}
                item={item}
                onSelectItem={onSelectItem}
              />
            ))}
          </div>
        )}
      </main>

      {selectedItem && (
        <PhoneGalleryItemSheet
          item={selectedItem}
          onClose={onCloseItem}
        />
      )}
    </div>
  )
}
