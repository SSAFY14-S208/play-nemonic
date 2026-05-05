'use client'

import { useMemo, useState } from 'react'
import type {
  PhoneGalleryFilterKey,
  PhoneGalleryItem,
} from '../types'

export function usePhoneGallery(galleryItems: PhoneGalleryItem[]) {
  const [activeFilterKey, setActiveFilterKey] =
    useState<PhoneGalleryFilterKey>('all')

  const filteredGalleryItems = useMemo(() => {
    if (activeFilterKey === 'all') return galleryItems

    return galleryItems.filter((item) => item.kind === activeFilterKey)
  }, [activeFilterKey, galleryItems])

  return {
    activeFilterKey,
    filteredGalleryItems,
    setActiveFilterKey,
  }
}
