'use client'

import { useMemo, useState } from 'react'
import type {
  PhoneGalleryFilterKey,
  PhoneGalleryItem,
} from '../types'

export function usePhoneGallery(
  galleryItems: PhoneGalleryItem[],
  selectedGalleryItemId: string | null,
) {
  const [activeFilterKey, setActiveFilterKey] =
    useState<PhoneGalleryFilterKey>('all')

  const filteredGalleryItems = useMemo(() => {
    if (activeFilterKey === 'all') return galleryItems

    return galleryItems.filter((item) => item.kind === activeFilterKey)
  }, [activeFilterKey, galleryItems])

  const selectedItem = useMemo(
    () =>
      galleryItems.find((item) => item.id === selectedGalleryItemId) ?? null,
    [galleryItems, selectedGalleryItemId],
  )

  return {
    activeFilterKey,
    filteredGalleryItems,
    selectedItem,
    setActiveFilterKey,
  }
}
