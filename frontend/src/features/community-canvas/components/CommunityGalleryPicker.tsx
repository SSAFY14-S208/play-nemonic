'use client'

import { Check, ImageIcon, RefreshCw } from 'lucide-react'
import { cn } from '@/shared/libs'
import type { GalleryItemResponse } from '@/shared/types'

interface CommunityGalleryPickerProps {
  items: GalleryItemResponse[]
  status: 'idle' | 'loading' | 'success' | 'error'
  error: string | null
  selectedGalleryId: string | null
  className?: string
  onLoad: () => void
  onSelect: (galleryId: string) => void
}

export function CommunityGalleryPicker({
  items,
  status,
  error,
  selectedGalleryId,
  className,
  onLoad,
  onSelect,
}: CommunityGalleryPickerProps) {
  if (status === 'idle') {
    return (
      <div className={cn('grid min-h-[12rem] place-items-center rounded-[0.5rem] border border-border-default bg-surface-subtle p-5 text-center', className)}>
        <button
          type="button"
          onClick={onLoad}
          className="body-b inline-flex h-11 items-center gap-2 rounded-[0.45rem] bg-primary-1 px-5 text-fg-primary transition hover:-translate-y-0.5"
        >
          <ImageIcon className="size-4" />
          갤러리 불러오기
        </button>
      </div>
    )
  }

  if (status === 'loading') {
    return (
      <div className={cn('grid min-h-[12rem] place-items-center rounded-[0.5rem] border border-border-default bg-surface-subtle', className)}>
        <p className="body-b text-fg-secondary">갤러리 불러오는 중</p>
      </div>
    )
  }

  if (status === 'error') {
    return (
      <div className={cn('grid min-h-[12rem] place-items-center rounded-[0.5rem] border border-border-default bg-surface-subtle p-5 text-center', className)}>
        <div>
          <p className="body-r text-fg-secondary">{error}</p>
          <button
            type="button"
            onClick={onLoad}
            className="body-b mt-4 inline-flex h-11 items-center gap-2 rounded-[0.45rem] bg-primary-1 px-5 text-fg-primary"
          >
            <RefreshCw className="size-4" />
            다시 시도
          </button>
        </div>
      </div>
    )
  }

  if (items.length === 0) {
    return (
      <div className={cn('grid min-h-[12rem] place-items-center rounded-[0.5rem] border border-border-default bg-surface-subtle', className)}>
        <p className="body-r text-fg-secondary">갤러리에 붙일 항목이 없어요.</p>
      </div>
    )
  }

  return (
    <div className={cn('max-h-[12rem] overflow-y-auto rounded-[0.5rem] border border-border-default bg-surface-subtle p-2 [scrollbar-width:none] [&::-webkit-scrollbar]:hidden', className)}>
      <div className="grid grid-cols-3 gap-2">
        {items.map((item) => {
          const isSelected = selectedGalleryId === item.galleryId
          const previewUrl = item.contentUrl || item.thumbnailUrl

          return (
            <button
              key={item.galleryId}
              type="button"
              aria-pressed={isSelected}
              onClick={() => onSelect(item.galleryId)}
              className={cn(
                'group rounded-[0.45rem] border bg-surface-default p-1.5 text-left transition hover:-translate-y-0.5',
                isSelected
                  ? 'border-primary-1 ring-2 ring-primary-5'
                  : 'border-border-default',
              )}
            >
              <span
                className="relative block overflow-hidden rounded-[0.35rem] bg-white"
                style={{ aspectRatio: '1 / 1' }}
              >
                {previewUrl ? (
                  <span
                    role="img"
                    aria-label={`${item.kind} 갤러리 항목`}
                    className="absolute inset-0 block bg-center bg-no-repeat"
                    style={{
                      backgroundImage: `url("${previewUrl}")`,
                      backgroundSize: 'contain',
                    }}
                  />
                ) : (
                  <span className="grid h-full place-items-center text-fg-secondary">
                    <ImageIcon className="size-6" />
                  </span>
                )}
                {isSelected && (
                  <span className="absolute right-2 top-2 grid size-7 place-items-center rounded-full bg-primary-1 text-fg-primary shadow-sm">
                    <Check className="size-4" />
                  </span>
                )}
              </span>
              <span className="caption-b mt-2 block truncate text-fg-primary">{item.kind}</span>
            </button>
          )
        })}
      </div>
    </div>
  )
}
