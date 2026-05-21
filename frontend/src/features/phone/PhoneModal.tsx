'use client'

import { Dialog } from '@base-ui/react/dialog'
import { motion } from 'motion/react'
import { useCallback, useState } from 'react'
import {
  PhoneCloseButton,
  PhoneDrawingScreen,
  PhoneFrame,
  PhoneGalleryScreen,
  PhoneHomeScreen,
  PhoneInquiryScreen,
  PhoneMobileCloseButton,
  PhoneTeleportScreen,
  PhoneToast,
} from './components'
import { PHONE_DESIGN_HEIGHT, PHONE_DESIGN_WIDTH } from './constants'
import { usePhoneStore } from './phoneStore'

const SLIDE_TRANSITION = {
  duration: 0.4,
  ease: [0.16, 1, 0.3, 1],
} as const

const FADE_TRANSITION = { duration: 0.3 } as const

export default function PhoneModal() {
  const activeScreen = usePhoneStore((state) => state.activeScreen)
  const closePhone = usePhoneStore((state) => state.closePhone)
  const isPhoneOpen = usePhoneStore((state) => state.isPhoneOpen)
  const openPhone = usePhoneStore((state) => state.openPhone)

  const [isClosing, setIsClosing] = useState(false)
  const dialogOpen = isPhoneOpen || isClosing

  const handleClose = useCallback(() => {
    setIsClosing(true)
  }, [])

  const handleExitComplete = useCallback(() => {
    setIsClosing(false)
    closePhone()
  }, [closePhone])

  const statusBarVariant = activeScreen === 'home' ? 'light' : 'dark'

  return (
    <Dialog.Root
      open={dialogOpen}
      onOpenChange={(isOpen) => {
        if (isOpen) {
          openPhone()
          return
        }

        handleClose()
      }}
    >
      <Dialog.Portal>
        <Dialog.Backdrop
          render={
            <motion.div
              initial={{ opacity: 0 }}
              animate={isClosing ? { opacity: 0 } : { opacity: 1 }}
              transition={FADE_TRANSITION}
            />
          }
          className="fixed inset-0 z-[14000] bg-black/20 backdrop-blur-[2px]"
        />
        <Dialog.Popup
          className="fixed inset-x-0 top-0 z-[14010] grid place-items-center overflow-hidden overscroll-contain px-4 [height:100dvh]"
          style={{
            paddingBottom: 'max(1rem, env(safe-area-inset-bottom))',
            paddingTop: 'max(1rem, env(safe-area-inset-top))',
          }}
        >
          <Dialog.Title className="sr-only">
            Play! Nemonic 핸드폰
          </Dialog.Title>
          <motion.div
            initial={{ y: '100vh', opacity: 0 }}
            animate={
              isClosing
                ? { y: '100vh', opacity: 0 }
                : { y: 0, opacity: 1 }
            }
            transition={SLIDE_TRANSITION}
            onAnimationComplete={() => {
              if (isClosing) handleExitComplete()
            }}
            style={{
              width: `${PHONE_DESIGN_WIDTH}px`,
              height: `${PHONE_DESIGN_HEIGHT}px`,
            }}
          >
            <div className="relative">
              <PhoneFrame statusBarVariant={statusBarVariant}>
                {activeScreen === 'home' && <PhoneHomeScreen />}
                {activeScreen === 'drawing' && <PhoneDrawingScreen />}
                {activeScreen === 'gallery' && <PhoneGalleryScreen />}
                {activeScreen === 'inquiry' && <PhoneInquiryScreen />}
                {activeScreen === 'teleport' && <PhoneTeleportScreen />}
                <PhoneToast />
              </PhoneFrame>
              <PhoneCloseButton onClose={handleClose} />
            </div>
          </motion.div>
          <motion.div
            initial={{ opacity: 0 }}
            animate={isClosing ? { opacity: 0 } : { opacity: 1 }}
            transition={{
              delay: isClosing ? 0 : 0.2,
              duration: 0.2,
            }}
          >
            <PhoneMobileCloseButton onClose={handleClose} />
          </motion.div>
        </Dialog.Popup>
      </Dialog.Portal>
    </Dialog.Root>
  )
}
