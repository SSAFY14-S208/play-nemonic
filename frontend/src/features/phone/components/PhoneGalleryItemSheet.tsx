'use client'

import Image from 'next/image'
import { useRouter } from 'next/navigation'
import { useEffect } from 'react'
import { ArrowRight, Download, Printer, Share2, Trash2, X } from 'lucide-react'
import { ShareSheet, useShareStore } from '@/features/share'
import { cn } from '@/shared/libs'
import { writeCommunityCanvasHandoffDraft } from '@/shared/utils'
import { PHONE_COLORS, PHONE_GALLERY_ITEM_STYLES } from '../constants'
import { useNemonicImagePrint } from '../hooks'
import { usePhoneStore } from '../phoneStore'
import type { PhoneGalleryItem } from '../types'
import { PhonePrintFrame } from './PhonePrintFrame'

interface PhoneGalleryItemSheetProps {
  item: PhoneGalleryItem
  onClose: () => void
}

function PhoneGalleryPreview({
  item,
  detailImageUrl,
}: {
  item: PhoneGalleryItem
  detailImageUrl: string | null
}) {
  const itemStyle = PHONE_GALLERY_ITEM_STYLES[item.kind]
  const previewUrl = detailImageUrl ?? item.imageDataUrl

  if (previewUrl) {
    return (
      <Image
        src={previewUrl}
        alt={`${item.title} 미리보기`}
        fill
        unoptimized
        className="object-contain"
      />
    )
  }

  return (
    <div
      className="flex h-full w-full items-center justify-center"
      style={{ background: itemStyle.background }}
    >
      <div
        className="relative grid size-[10.5rem] place-items-center border bg-white"
        style={{
          borderColor: `${itemStyle.color}33`,
          boxShadow: PHONE_COLORS.gallerySheetPreviewShadow,
        }}
      >
        <div
          aria-hidden
          className="size-16 rounded-[0.35rem]"
          style={{ background: itemStyle.color }}
        />
        <div className="absolute bottom-5 left-5 right-5 space-y-1.5">
          {Array.from({ length: 4 }).map((_, lineIndex) => (
            <span
              key={`sheet-preview-line-${lineIndex}`}
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

export function PhoneGalleryItemSheet({
  item,
  onClose,
}: PhoneGalleryItemSheetProps) {
  const router = useRouter()
  const itemStyle = PHONE_GALLERY_ITEM_STYLES[item.kind]
  const galleryDetail = usePhoneStore((state) => state.galleryDetail)
  const galleryDetailStatus = usePhoneStore(
    (state) => state.galleryDetailStatus,
  )
  const loadGalleryDetail = usePhoneStore((state) => state.loadGalleryDetail)
  const clearGalleryDetail = usePhoneStore((state) => state.clearGalleryDetail)
  const deleteGalleryItem = usePhoneStore((state) => state.deleteGalleryItem)
  const setToast = usePhoneStore((state) => state.setToast)
  const shareInfo = useShareStore((state) => state.shareInfo)
  const shareStatus = useShareStore((state) => state.shareStatus)
  const shareError = useShareStore((state) => state.shareError)
  const createShare = useShareStore((state) => state.createShare)
  const clearShare = useShareStore((state) => state.clearShare)
  const isSharing = shareStatus === 'loading'

  const isLoading = galleryDetailStatus === 'loading'
  const detailImageUrl =
    galleryDetail && galleryDetail.galleryId === item.id
      ? galleryDetail.contentUrl || galleryDetail.thumbnailUrl
      : null
  const printImageUrl = detailImageUrl ?? item.imageDataUrl ?? null
  const {
    isPreparingPrint,
    isPrintDisabled,
    printImage,
    printMessage,
  } = useNemonicImagePrint({
    imageUrl: printImageUrl,
    isImageLoading: isLoading,
    onPrintBlocked: setToast,
  })

  useEffect(() => {
    void loadGalleryDetail(item.id)
    return () => {
      clearGalleryDetail()
      clearShare()
    }
  }, [clearGalleryDetail, clearShare, item.id, loadGalleryDetail])

  useEffect(() => {
    if (shareError) setToast(shareError)
  }, [shareError, setToast])

  const handleDelete = async () => {
    if (typeof window !== 'undefined') {
      const confirmed = window.confirm('이 항목을 삭제할까요?')
      if (!confirmed) return
    }
    await deleteGalleryItem(item.id)
  }

  const handleCommunityAttach = () => {
    if (!printImageUrl) {
      setToast('커뮤니티에 붙일 이미지를 불러오지 못했어요.')
      return
    }

    writeCommunityCanvasHandoffDraft({
      sourceKind: 'GALLERY',
      title: item.title,
      imageUrl: printImageUrl,
      thumbnailUrl: item.imageDataUrl ?? printImageUrl,
      sourceGalleryId: item.id,
      sourceContentKind: item.kind,
    })
    router.push('/community-canvas')
  }

  return (
    <div className="absolute inset-0 z-30 flex items-end">
      <button
        type="button"
        aria-label="아이템 상세 닫기"
        className="absolute inset-0 backdrop-blur-[1px]"
        onClick={onClose}
        style={{ background: PHONE_COLORS.gallerySheetBackdrop }}
      />
      <section
        className="relative w-full rounded-t-[1rem] border-t border-border-default px-5 pb-7 pt-3"
        style={{
          background: PHONE_COLORS.galleryBackground,
          boxShadow: PHONE_COLORS.gallerySheetShadow,
        }}
      >
        <div className="mx-auto mb-4 h-1 w-10 rounded-full bg-border-default" />
        <header className="mb-3 flex items-center justify-between">
          <span
            className="caption-b inline-flex items-center gap-1.5 rounded-[0.45rem] px-2.5 py-1.5"
            style={{
              background: itemStyle.background,
              color: itemStyle.color,
            }}
          >
            <span
              aria-hidden
              className="size-1.5 rounded-full"
              style={{ background: itemStyle.color }}
            />
            {itemStyle.label}
          </span>
          <button
            type="button"
            aria-label="닫기"
            onClick={onClose}
            className="grid size-9 place-items-center rounded-full bg-white text-fg-secondary transition hover:text-fg-primary"
          >
            <X className="size-5" />
          </button>
        </header>

        <div
          className="relative mb-4 aspect-square overflow-hidden rounded-[0.45rem] bg-white"
          style={{ boxShadow: PHONE_COLORS.gallerySheetPreviewShadow }}
        >
          {isLoading && !detailImageUrl ? (
            <div className="absolute inset-0 animate-pulse bg-surface-subtle" />
          ) : (
            <PhoneGalleryPreview item={item} detailImageUrl={detailImageUrl} />
          )}
          {item.badgeLabel && (
            <span className="caption-b absolute left-3 top-3 rounded-[0.35rem] bg-white/90 px-2 py-1 text-fg-primary">
              {item.badgeLabel}
            </span>
          )}
        </div>

        <h3 className="h3-b text-fg-primary">{item.title}</h3>
        <p className="caption-r mt-1 text-fg-secondary">
          {item.createdAtLabel}
          {item.contributorLabel ? ` · ${item.contributorLabel}` : ''}
        </p>

        <div className="mt-5 grid grid-cols-3 gap-3">
          <button
            type="button"
            className="body-b flex h-11 items-center justify-center gap-2 rounded-[0.45rem] border border-border-default bg-white text-fg-primary transition hover:bg-surface-subtle"
          >
            <Download className="size-4" />
            저장
          </button>
          <button
            type="button"
            onClick={() => void createShare(item.id)}
            disabled={isSharing}
            className={cn(
              'body-b flex h-11 items-center justify-center gap-2 rounded-[0.45rem] border border-border-default bg-white text-fg-primary transition hover:bg-surface-subtle',
              isSharing && 'cursor-not-allowed opacity-60 hover:bg-white',
            )}
          >
            <Share2 className="size-4" />
            {isSharing ? '준비 중' : '공유'}
          </button>
          <button
            type="button"
            onClick={handleDelete}
            className="body-b flex h-11 items-center justify-center gap-2 rounded-[0.45rem] border border-red-200 bg-white text-red-500 transition hover:bg-red-50"
          >
            <Trash2 className="size-4" />
            삭제
          </button>
        </div>

        <button
          type="button"
          onClick={() => void printImage()}
          disabled={isPrintDisabled}
          aria-describedby={printMessage ? 'phone-gallery-print-status' : undefined}
          className={cn(
            'body-l-b mt-3 flex h-12 w-full items-center justify-center gap-2 rounded-[0.45rem] transition',
            isPrintDisabled
              ? 'cursor-not-allowed bg-surface-subtle text-fg-secondary'
              : 'bg-primary-1 text-fg-inverse hover:-translate-y-0.5',
          )}
        >
          <Printer className="size-5" />
          {isPreparingPrint ? '인쇄창 준비 중' : '네모닉 출력'}
        </button>
        {printMessage && (
          <p
            id="phone-gallery-print-status"
            className="caption-r mt-2 text-center text-fg-secondary"
          >
            {printMessage}
          </p>
        )}

        <button
          type="button"
          onClick={handleCommunityAttach}
          disabled={!printImageUrl}
          className={cn(
            'body-l-b mt-3 flex h-12 w-full items-center justify-center gap-2 rounded-[0.45rem] bg-fg-primary text-fg-inverse transition hover:-translate-y-0.5',
            !printImageUrl && 'cursor-not-allowed opacity-60',
          )}
        >
          커뮤니티 캔버스에 붙이기
          <ArrowRight className="size-5" />
        </button>
        <p className="caption-r mt-3 text-center text-fg-secondary">
          월드 캔버스에 메모지로 부착됩니다.
        </p>
      </section>
      <PhonePrintFrame imageUrl={printImageUrl} title={item.title} />
      {shareInfo && <ShareSheet shareInfo={shareInfo} onClose={clearShare} />}
    </div>
  )
}
