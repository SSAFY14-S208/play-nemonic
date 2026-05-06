'use client'

import { create } from 'zustand'
import { PHONE_INITIAL_GALLERY_ITEMS } from './constants'
import type { PhoneGalleryItem, PhoneScreenKey } from './types'

interface PhoneStore {
  activeScreen: PhoneScreenKey
  galleryItems: PhoneGalleryItem[]
  isPhoneOpen: boolean
  selectedGalleryItemId: string | null
  toastMessage: string | null
  addDrawingArtifact: (imageDataUrl: string, action: 'save' | 'print') => void
  closeGalleryItem: () => void
  closePhone: () => void
  dismissToast: () => void
  goHome: () => void
  openPhone: () => void
  selectGalleryItem: (itemId: string) => void
  showDrawing: () => void
  showGallery: () => void
}

function createDrawingArtifact(imageDataUrl: string): PhoneGalleryItem {
  return {
    id: `phone-drawing-${Date.now()}`,
    kind: 'phone',
    title: '내가 그린 메모',
    createdAtLabel: '방금 전',
    badgeLabel: 'NEW',
    imageDataUrl,
    isNew: true,
  }
}

export const usePhoneStore = create<PhoneStore>((set) => ({
  activeScreen: 'home',
  galleryItems: PHONE_INITIAL_GALLERY_ITEMS,
  isPhoneOpen: false,
  selectedGalleryItemId: null,
  toastMessage: null,
  addDrawingArtifact: (imageDataUrl, action) =>
    set((state) => ({
      activeScreen: 'gallery',
      galleryItems: [createDrawingArtifact(imageDataUrl), ...state.galleryItems],
      selectedGalleryItemId: null,
      toastMessage:
        action === 'print'
          ? '네모닉 출력 요청을 보냈어요.'
          : '갤러리에 저장했어요.',
    })),
  closeGalleryItem: () => set({ selectedGalleryItemId: null }),
  closePhone: () =>
    set({
      activeScreen: 'home',
      isPhoneOpen: false,
      selectedGalleryItemId: null,
      toastMessage: null,
    }),
  dismissToast: () => set({ toastMessage: null }),
  goHome: () => set({ activeScreen: 'home', selectedGalleryItemId: null }),
  openPhone: () =>
    set({
      activeScreen: 'home',
      isPhoneOpen: true,
      selectedGalleryItemId: null,
    }),
  selectGalleryItem: (itemId) => set({ selectedGalleryItemId: itemId }),
  showDrawing: () => set({ activeScreen: 'drawing', selectedGalleryItemId: null }),
  showGallery: () => set({ activeScreen: 'gallery', selectedGalleryItemId: null }),
}))
