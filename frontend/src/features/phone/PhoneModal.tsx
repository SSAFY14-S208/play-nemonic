'use client'

import { Dialog } from '@base-ui/react/dialog'
import {
  PhoneCloseButton,
  PhoneDrawingScreen,
  PhoneFrame,
  PhoneGalleryScreen,
  PhoneHomeScreen,
  PhoneToast,
} from './components'
import { usePhoneStore } from './phoneStore'

export default function PhoneModal() {
  const activeScreen = usePhoneStore((state) => state.activeScreen)
  const closePhone = usePhoneStore((state) => state.closePhone)
  const isPhoneOpen = usePhoneStore((state) => state.isPhoneOpen)
  const openPhone = usePhoneStore((state) => state.openPhone)

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
        <Dialog.Popup
          className="fixed inset-0 z-[var(--z-modal)] grid place-items-center p-4"
          onClick={(event) => {
            if (event.target === event.currentTarget) {
              closePhone()
            }
          }}
        >
          <Dialog.Title className="sr-only">
            네모닉 월드 핸드폰
          </Dialog.Title>
          <div className="relative w-fit">
            <PhoneFrame statusBarVariant={statusBarVariant}>
              {activeScreen === 'home' && <PhoneHomeScreen />}
              {activeScreen === 'drawing' && <PhoneDrawingScreen />}
              {activeScreen === 'gallery' && <PhoneGalleryScreen />}
              <PhoneToast />
            </PhoneFrame>
            <PhoneCloseButton onClose={closePhone} />
          </div>
        </Dialog.Popup>
      </Dialog.Portal>
    </Dialog.Root>
  )
}
