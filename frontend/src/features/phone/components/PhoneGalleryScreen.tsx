'use client'

import Image from 'next/image'
import { useEffect } from 'react'
import { ArrowLeft, ChevronDown, ImageIcon, Search } from 'lucide-react'
import { cn } from '@/shared/libs'
import {
  PHONE_COLORS,
  PHONE_GALLERY_FILTERS,
  PHONE_GALLERY_ITEM_STYLES,
} from '../constants'
import { usePhoneGallery } from '../hooks'
import { usePhoneStore } from '../phoneStore'
import type { PhoneGalleryItem } from '../types'
import { PhoneGalleryItemSheet } from './PhoneGalleryItemSheet'

function PhoneGalleryPaperPreview({ item }: { item: PhoneGalleryItem }) {
  const itemStyle = PHONE_GALLERY_ITEM_STYLES[item.kind]

  if (item.imageDataUrl) {
    return (
      <Image
        src={item.imageDataUrl}
        alt={`${item.title} 미리보기`}
        fill
        unoptimized
        className="bg-white object-contain"
        sizes="7rem"
      />
    )
  }

  return (
    <div
      className="absolute inset-0 grid place-items-center"
      style={{ background: itemStyle.background }}
    >
      <div
        className="relative grid size-[70%] place-items-center bg-white"
        style={{
          border: `1px solid ${itemStyle.color}22`,
          boxShadow: PHONE_COLORS.galleryPaperShadow,
        }}
      >
        <ImageIcon
          aria-hidden
          className="size-7"
          style={{ color: itemStyle.color }}
        />
        <div className="absolute bottom-2.5 left-2.5 right-2.5 space-y-1">
          {Array.from({ length: 3 }).map((_, lineIndex) => (
            <span
              key={`gallery-preview-line-${lineIndex}`}
              aria-hidden
              className="block h-1 rounded-full opacity-45"
              style={{ background: itemStyle.color }}
            />
          ))}
        </div>
      </div>
    </div>
  )
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
      className="group min-w-0 text-left transition focus-visible:outline focus-visible:outline-3 focus-visible:outline-primary-2"
    >
      <div
        className="relative aspect-square overflow-hidden rounded-[0.55rem] bg-white transition group-hover:-translate-y-0.5"
        style={{
          background: itemStyle.background,
          boxShadow: PHONE_COLORS.galleryCardShadow,
        }}
      >
        <PhoneGalleryPaperPreview item={item} />
        <span
          aria-hidden
          className="absolute left-2 top-2 size-2 rounded-full"
          style={{
            background: itemStyle.color,
            boxShadow: PHONE_COLORS.galleryBadgeShadow,
          }}
        />
        {item.badgeLabel && (
          <span
            className="caption-b absolute right-1.5 top-1.5 rounded-[0.28rem] bg-white/90 px-1.5 py-0.5"
            style={{ color: itemStyle.color }}
          >
            {item.badgeLabel}
          </span>
        )}
      </div>

      <div className="mt-2 min-w-0 px-0.5">
        <h3 className="caption-b line-clamp-1 text-fg-primary">
          {item.title}
        </h3>
        <p className="caption-r mt-0.5 text-fg-secondary">
          {item.createdAtLabel}
        </p>
      </div>
    </button>
  )
}

function PhoneGalleryCardSkeleton() {
  return (
    <div className="min-w-0">
      <div className="aspect-square animate-pulse rounded-[0.55rem] bg-white/70" />
      <div className="mt-2 h-3 w-3/4 animate-pulse rounded bg-white/70" />
      <div className="mt-1 h-2.5 w-1/2 animate-pulse rounded bg-white/70" />
    </div>
  )
}

export function PhoneGalleryScreen() {
  const closeGalleryItem = usePhoneStore((state) => state.closeGalleryItem)
  const galleryItems = usePhoneStore((state) => state.galleryItems)
  const goHome = usePhoneStore((state) => state.goHome)
  const selectGalleryItem = usePhoneStore((state) => state.selectGalleryItem)
  const selectedGalleryItemId = usePhoneStore(
    (state) => state.selectedGalleryItemId,
  )
  const galleryStatus = usePhoneStore((state) => state.galleryStatus)
  const galleryError = usePhoneStore((state) => state.galleryError)
  const galleryHasNext = usePhoneStore((state) => state.galleryHasNext)
  const galleryTotal = usePhoneStore((state) => state.galleryTotal)
  const galleryLoadingMore = usePhoneStore((state) => state.galleryLoadingMore)
  const loadGallery = usePhoneStore((state) => state.loadGallery)
  const loadMoreGallery = usePhoneStore((state) => state.loadMoreGallery)

  useEffect(() => {
    if (galleryStatus === 'idle') {
      void loadGallery()
    }
  }, [galleryStatus, loadGallery])

  const {
    activeFilterKey,
    filteredGalleryItems,
    selectedItem,
    setActiveFilterKey,
  } = usePhoneGallery(galleryItems, selectedGalleryItemId)

  const isInitialLoading = galleryStatus === 'loading' && galleryItems.length === 0
  const isErrored = galleryStatus === 'error'
  const isEmpty =
    galleryStatus === 'success' && filteredGalleryItems.length === 0

  return (
    <div
      className="relative flex h-full flex-col pt-[3.35rem] text-fg-primary"
      style={{ background: PHONE_COLORS.galleryBackground }}
    >
      <header className="shrink-0 px-5 pb-3">
        <div className="flex h-12 items-center justify-between">
          <button
            type="button"
            aria-label="뒤로 돌아가기"
            onClick={goHome}
            className="grid size-11 place-items-center rounded-full text-fg-primary transition hover:bg-white focus-visible:outline focus-visible:outline-3 focus-visible:outline-primary-2"
          >
            <ArrowLeft className="size-5" />
          </button>
          <h2 className="h3-b">내 갤러리</h2>
          <button
            type="button"
            aria-label="갤러리 검색"
            className="grid size-11 place-items-center rounded-full text-fg-secondary transition hover:bg-white"
          >
            <Search className="size-5" />
          </button>
        </div>
      </header>

      <section className="shrink-0 px-5 pb-3">
        <div className="mb-3 flex items-end justify-between gap-3">
          <div>
            <p className="caption-m text-fg-secondary">저장된 네모닉</p>
            <p className="h4-b mt-0.5 text-fg-primary">총 {galleryTotal}개</p>
          </div>
          <button
            type="button"
            className="caption-b flex h-8 items-center gap-1 rounded-[0.45rem] border border-border-default bg-white px-3 text-fg-secondary"
          >
            최신순
            <ChevronDown className="size-3.5" />
          </button>
        </div>

        <div className="no-scrollbar flex gap-2 overflow-x-auto pb-1">
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
                  'caption-b flex h-8 shrink-0 items-center gap-1.5 rounded-[0.45rem] border px-3 transition',
                  isActive
                    ? 'border-fg-primary bg-fg-primary text-fg-inverse'
                    : 'border-border-default bg-white text-fg-secondary hover:bg-surface-subtle',
                )}
              >
                {filterStyle && (
                  <span
                    aria-hidden
                    className="size-1.5 rounded-full"
                    style={{
                      background: isActive
                        ? PHONE_COLORS.white
                        : filterStyle.color,
                    }}
                  />
                )}
                {label}
              </button>
            )
          })}
        </div>
      </section>

      <main className="no-scrollbar min-h-0 flex-1 overflow-y-auto px-5 pb-8">
        {isInitialLoading ? (
          <div className="grid grid-cols-3 gap-x-2.5 gap-y-4">
            {Array.from({ length: 6 }).map((_, index) => (
              <PhoneGalleryCardSkeleton key={`skeleton-${index}`} />
            ))}
          </div>
        ) : isErrored ? (
          <div className="flex h-full flex-col items-center justify-center gap-3 text-center">
            <p className="h4-b text-fg-primary">
              갤러리를 불러올 수 없어요
            </p>
            {galleryError && (
              <p className="caption-r text-fg-secondary">{galleryError}</p>
            )}
            <button
              type="button"
              onClick={() => void loadGallery({ force: true })}
              className="body-b mt-1 inline-flex h-9 items-center justify-center rounded-[0.45rem] bg-fg-primary px-4 text-fg-inverse"
            >
              다시 시도
            </button>
          </div>
        ) : isEmpty ? (
          <div className="flex h-full flex-col items-center justify-center text-center">
            <p className="h4-b text-fg-primary">아직 저장된 카드가 없어요</p>
            <p className="body-r mt-2 text-fg-secondary">
              네모닉 그림판에서 하나 만들어 볼까요?
            </p>
          </div>
        ) : (
          <>
            <div className="grid grid-cols-3 gap-x-2.5 gap-y-4">
              {filteredGalleryItems.map((item) => (
                <PhoneGalleryCard
                  key={item.id}
                  item={item}
                  onSelectItem={selectGalleryItem}
                />
              ))}
            </div>
            {activeFilterKey === 'all' && galleryHasNext && (
              <div className="mt-4 flex justify-center">
                <button
                  type="button"
                  onClick={() => void loadMoreGallery()}
                  disabled={galleryLoadingMore}
                  className="body-b inline-flex h-9 items-center justify-center gap-2 rounded-[0.45rem] border border-border-default bg-white px-4 text-fg-primary transition hover:bg-surface-subtle disabled:opacity-60"
                >
                  {galleryLoadingMore && (
                    <span
                      aria-hidden
                      className="size-3 animate-spin rounded-full border-2 border-fg-secondary/40 border-t-fg-primary"
                    />
                  )}
                  더 보기
                </button>
              </div>
            )}
          </>
        )}
      </main>

      {selectedItem && (
        <PhoneGalleryItemSheet
          item={selectedItem}
          onClose={closeGalleryItem}
        />
      )}
    </div>
  )
}
