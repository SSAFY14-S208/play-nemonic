'use client'

import { Dialog } from '@base-ui/react/dialog'
import {
  PhoneCloseButton,
  PhoneDrawingScreen,
  PhoneFrame,
  PhoneGalleryScreen,
  PhoneHomeScreen,
  PhoneInquiryScreen,
  PhoneMobileCloseButton,
  PhoneToast,
} from './components'
import { PHONE_DESIGN_HEIGHT, PHONE_DESIGN_WIDTH, usePhoneScale } from './hooks'
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
        <Dialog.Popup
          className="fixed inset-x-0 top-0 z-[var(--z-modal)] grid place-items-center overflow-hidden overscroll-contain px-4 [height:100dvh]"
          style={{
            paddingBottom: 'max(1rem, env(safe-area-inset-bottom))',
            paddingTop: 'max(1rem, env(safe-area-inset-top))',
          }}
        >
          <Dialog.Title className="sr-only">
            Play! Nemonic 핸드폰
          </Dialog.Title>
          <div
            style={{
              width: `${PHONE_DESIGN_WIDTH * scale}px`,
              height: `${PHONE_DESIGN_HEIGHT * scale}px`,
            }}
          >
            <div
              className="relative origin-top-left"
              style={{
                transform: `scale(${scale})`,
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
          </div>
          <PhoneMobileCloseButton onClose={closePhone} />
        </Dialog.Popup>
      </Dialog.Portal>
    </Dialog.Root>
  )
}
