'use client'

import { useEffect } from 'react'
import { Dialog } from '@base-ui/react/dialog'
import {
  PhoneDrawingScreen,
  PhoneFrame,
  PhoneGalleryScreen,
  PhoneHomeScreen,
} from './components'
import { usePhoneStore } from './phoneStore'

export default function PhoneModal() {
  const activeScreen = usePhoneStore((state) => state.activeScreen)
  const addDrawingArtifact = usePhoneStore((state) => state.addDrawingArtifact)
  const closePhone = usePhoneStore((state) => state.closePhone)
  const galleryItems = usePhoneStore((state) => state.galleryItems)
  const goHome = usePhoneStore((state) => state.goHome)
  const isPhoneOpen = usePhoneStore((state) => state.isPhoneOpen)
  const openPhone = usePhoneStore((state) => state.openPhone)
  const closeGalleryItem = usePhoneStore((state) => state.closeGalleryItem)
  const selectGalleryItem = usePhoneStore((state) => state.selectGalleryItem)
  const selectedGalleryItemId = usePhoneStore((state) => state.selectedGalleryItemId)
  const showDrawing = usePhoneStore((state) => state.showDrawing)
  const showGallery = usePhoneStore((state) => state.showGallery)
  const toastMessage = usePhoneStore((state) => state.toastMessage)
  const dismissToast = usePhoneStore((state) => state.dismissToast)

  useEffect(() => {
    if (!toastMessage) return

    const toastTimer = window.setTimeout(() => {
      dismissToast()
    }, 2200)

    return () => {
      window.clearTimeout(toastTimer)
    }
  }, [dismissToast, toastMessage])

  const statusBarVariant = activeScreen === 'home' ? 'light' : 'dark'
  const selectedGalleryItem =
    galleryItems.find((item) => item.id === selectedGalleryItemId) ?? null

  return (
    <Dialog.Root
      open={isPhoneOpen}
      onOpenChange={(isOpen) => {
        if (isOpen) {
          openPhone()
          return
        }

        closePhone()
      }}
    >
      <Dialog.Portal>
        <Dialog.Backdrop className="fixed inset-0 z-[var(--z-overlay)] bg-black/20 backdrop-blur-[2px]" />
        <Dialog.Popup className="fixed inset-0 z-[var(--z-modal)] grid place-items-center p-4">
          <Dialog.Title className="sr-only">
            네모닉 월드 핸드폰
          </Dialog.Title>
          <PhoneFrame statusBarVariant={statusBarVariant}>
            {activeScreen === 'home' && (
              <PhoneHomeScreen
                onOpenDrawing={showDrawing}
                onOpenGallery={showGallery}
              />
            )}
            {activeScreen === 'drawing' && (
              <PhoneDrawingScreen
                onBack={goHome}
                onCreateArtifact={addDrawingArtifact}
              />
            )}
            {activeScreen === 'gallery' && (
              <PhoneGalleryScreen
                galleryItems={galleryItems}
                selectedItem={selectedGalleryItem}
                onBack={goHome}
                onCloseItem={closeGalleryItem}
                onSelectItem={selectGalleryItem}
              />
            )}
            {toastMessage && (
              <div className="body-b absolute left-1/2 top-1/2 z-[var(--z-toast)] w-[min(18rem,calc(100%-2rem))] -translate-x-1/2 -translate-y-1/2 rounded-[var(--radius-xl)] bg-[rgba(17,21,29,0.88)] px-5 py-3 text-center text-fg-inverse shadow-lg">
                {toastMessage}
              </div>
            )}
          </PhoneFrame>
        </Dialog.Popup>
      </Dialog.Portal>
    </Dialog.Root>
  )
}
