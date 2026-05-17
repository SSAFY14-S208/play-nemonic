'use client'

import { Dialog } from '@base-ui/react/dialog'
import {
  PhoneCloseButton,
  PhoneDrawingScreen,
  PhoneFrame,
  PhoneGalleryScreen,
  PhoneHomeScreen,
  PhoneInquiryScreen,
  PhoneToast,
} from './components'
import { usePhoneScale } from './hooks'
import { usePhoneStore } from './phoneStore'

export default function PhoneModal() {
  const activeScreen = usePhoneStore((state) => state.activeScreen)
  const closePhone = usePhoneStore((state) => state.closePhone)
  const isPhoneOpen = usePhoneStore((state) => state.isPhoneOpen)
  const openPhone = usePhoneStore((state) => state.openPhone)

  const statusBarVariant = activeScreen === 'home' ? 'light' : 'dark'
  const scale = usePhoneScale()

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
            Play! Nemonic 핸드폰
          </Dialog.Title>
          <div
            className="relative w-fit"
            style={{
              transform: `scale(${scale})`,
              transformOrigin: 'center',
            }}
          >
            <PhoneFrame statusBarVariant={statusBarVariant}>
              {activeScreen === 'home' && <PhoneHomeScreen />}
              {activeScreen === 'drawing' && <PhoneDrawingScreen />}
              {activeScreen === 'gallery' && <PhoneGalleryScreen />}
              {activeScreen === 'inquiry' && <PhoneInquiryScreen />}
              <PhoneToast />
            </PhoneFrame>
            <PhoneCloseButton onClose={closePhone} />
          </div>
        </Dialog.Popup>
      </Dialog.Portal>
    </Dialog.Root>
  )
}
