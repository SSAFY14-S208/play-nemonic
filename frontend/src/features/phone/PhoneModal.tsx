'use client'

import { useEffect } from 'react'
import { Dialog } from '@base-ui/react/dialog'
import { PhoneDrawingScreen, PhoneFrame, PhoneHomeScreen } from './components'
import { usePhoneStore } from './phoneStore'

function PhoneGalleryPlaceholder() {
  return (
    <div className="flex h-full flex-col bg-surface-default pt-28 text-center">
      <p className="caption-b text-primary-2">GALLERY</p>
      <h2 className="h3-b mt-2 text-fg-primary">내 갤러리</h2>
      <p className="body-r mt-4 px-10 text-fg-secondary">
        저장된 그림과 부스 결과물을 모아 보여줄 화면을 준비하고 있어요.
      </p>
    </div>
  )
}

export default function PhoneModal() {
  const activeScreen = usePhoneStore((state) => state.activeScreen)
  const addDrawingArtifact = usePhoneStore((state) => state.addDrawingArtifact)
  const closePhone = usePhoneStore((state) => state.closePhone)
  const galleryItems = usePhoneStore((state) => state.galleryItems)
  const goHome = usePhoneStore((state) => state.goHome)
  const isPhoneOpen = usePhoneStore((state) => state.isPhoneOpen)
  const openPhone = usePhoneStore((state) => state.openPhone)
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
        <Dialog.Popup className="fixed bottom-4 right-4 z-[var(--z-modal)] max-[480px]:right-1/2 max-[480px]:translate-x-1/2">
          <Dialog.Title className="sr-only">
            네모닉 월드 핸드폰
          </Dialog.Title>
          <PhoneFrame statusBarVariant={statusBarVariant}>
            {activeScreen === 'home' && (
              <PhoneHomeScreen
                recentGalleryItems={galleryItems}
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
            {activeScreen === 'gallery' && <PhoneGalleryPlaceholder />}
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
