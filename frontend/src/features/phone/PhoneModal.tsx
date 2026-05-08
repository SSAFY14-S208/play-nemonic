'use client'

import { Dialog } from '@base-ui/react/dialog'
import { useEffect, useState } from 'react'
import {
  PhoneCloseButton,
  PhoneDrawingScreen,
  PhoneFrame,
  PhoneGalleryScreen,
  PhoneHomeScreen,
  PhoneToast,
} from './components'
import { usePhoneStore } from './phoneStore'

// 폰 디자인 사이즈. 자식 요소들이 이 크기를 전제로 px/rem이 박혀 있어
// 폰 frame을 가변으로 두면 비율이 깨진다. 디자인 사이즈는 고정하고
// viewport에 맞춰 wrapper만 transform: scale로 비례 축소시킨다.
const PHONE_DESIGN_WIDTH = 393
const PHONE_DESIGN_HEIGHT = 815
const VIEWPORT_PADDING_X = 24 // 1.5rem (좌우 여백)
const VIEWPORT_PADDING_Y = 32 // 2rem (상하 여백)

function usePhoneScale() {
  const [scale, setScale] = useState(1)
  useEffect(() => {
    const recompute = () => {
      const availW = window.innerWidth - VIEWPORT_PADDING_X
      const availH = window.innerHeight - VIEWPORT_PADDING_Y
      setScale(
        Math.min(1, availW / PHONE_DESIGN_WIDTH, availH / PHONE_DESIGN_HEIGHT),
      )
    }
    recompute()
    window.addEventListener('resize', recompute)
    return () => window.removeEventListener('resize', recompute)
  }, [])
  return scale
}

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
            네모닉 월드 핸드폰
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
              <PhoneToast />
            </PhoneFrame>
            <PhoneCloseButton onClose={closePhone} />
          </div>
        </Dialog.Popup>
      </Dialog.Portal>
    </Dialog.Root>
  )
}
