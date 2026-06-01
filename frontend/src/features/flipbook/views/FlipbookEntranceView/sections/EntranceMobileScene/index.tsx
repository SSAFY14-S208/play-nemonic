import Image from 'next/image'
import { motion } from 'motion/react'

import { PhoneLauncherButton } from '@/shared/components'
import {
  FLIPBOOK_ACTION_BUTTON_IMAGE_QUALITY,
  FLIPBOOK_ACTION_BUTTON_IMAGE_SIZES,
  FLIPBOOK_SCENE_IMAGES,
} from '../../constants'

interface EntranceMobileSceneProps {
  visible: boolean
  isBusy: boolean
  isBgmMuted: boolean
  errorMessage: string | null
  onCreateRoom: () => void
  onOpenRoomCodeModal: () => void
  onOpenHowToPlay: () => void
  onToggleBgmMuted: () => void
}

interface EntranceMobileIconButtonProps {
  imageSrc: string
  imageWidth: number
  imageHeight: number
  label: string
  pressed?: boolean
  className: string
  onClick: () => void
}

interface EntranceMobileActionButtonProps {
  imageCropClassName: string
  label: string
  disabled: boolean
  onClick: () => void
}

export function EntranceMobileScene({
  visible,
  isBusy,
  isBgmMuted,
  errorMessage,
  onCreateRoom,
  onOpenRoomCodeModal,
  onOpenHowToPlay,
  onToggleBgmMuted,
}: EntranceMobileSceneProps) {
  return (
    <div className="absolute inset-0 z-10 overflow-hidden sm:hidden">
      <div
        aria-hidden
        className="pointer-events-none absolute left-[21.12%] top-[-3.49%] h-[16.76%] w-[139.47%] overflow-hidden"
      >
        <div className="relative h-[79.46%] w-[98.97%] rotate-[3.2deg] overflow-hidden">
          <Image
            src={FLIPBOOK_SCENE_IMAGES.furnitureSprite}
            alt=""
            width={1536}
            height={1024}
            loading="eager"
            sizes="140vw"
            className="absolute h-[593.26%] w-[191.05%] max-w-none -left-[36.96%] -top-[33.16%]"
          />
        </div>
      </div>

      <div
        aria-hidden
        className="pointer-events-none absolute left-[-10.68%] top-[42.09%] h-[55.94%] w-[169.66%] overflow-hidden"
      >
        <Image
          src={FLIPBOOK_SCENE_IMAGES.furnitureSprite}
          alt=""
          width={1536}
          height={1024}
          loading="eager"
          sizes="170vw"
          className="absolute h-[181.75%] w-[199.94%] max-w-none -left-[0.03%] -top-[81.75%]"
        />
      </div>

      <motion.div
        className="absolute inset-0 z-20"
        initial={false}
        animate={visible ? { opacity: 1, y: 0 } : { opacity: 0, y: '1.96%' }}
        transition={{ duration: 0.42, ease: [0.22, 0.8, 0.22, 1] }}
      >
        <div className="absolute right-[7.52%] top-[5.34%] flex items-center justify-end gap-2">
          <EntranceMobileIconButton
            imageSrc={FLIPBOOK_SCENE_IMAGES.howToPlayButton}
            imageWidth={63}
            imageHeight={70}
            label="게임 설명"
            className="aspect-[49/54] w-12"
            onClick={onOpenHowToPlay}
          />
          <EntranceMobileIconButton
            imageSrc={
              isBgmMuted
                ? FLIPBOOK_SCENE_IMAGES.soundMutedButton
                : FLIPBOOK_SCENE_IMAGES.soundOnButton
            }
            imageWidth={67}
            imageHeight={70}
            label={isBgmMuted ? '배경음악 켜기' : '배경음악 음소거'}
            pressed={isBgmMuted}
            className="aspect-[51/54] w-12"
            onClick={onToggleBgmMuted}
          />
          <PhoneLauncherButton className="size-12" />
        </div>

        <div className="absolute left-1/2 top-[24.54%] w-[86.17%] -translate-x-1/2">
          <div className="relative aspect-[486/238] w-full overflow-hidden">
            <Image
              src={FLIPBOOK_SCENE_IMAGES.titleLogoSprite}
              alt="플립북"
              width={1536}
              height={1024}
              priority
              sizes="86vw"
              className="absolute h-[184.45%] w-auto max-w-none -left-[17.95%] -top-[56.72%]"
            />
          </div>
        </div>

        <div className="absolute left-1/2 top-[58.43%] flex w-[41.5%] -translate-x-1/2 flex-col items-center gap-[2.18svh]">
          <EntranceMobileActionButton
            imageCropClassName="h-[383.52%] w-auto -left-[17.95%] -top-[150.56%]"
            label={isBusy ? '처리 중' : '방 만들기'}
            disabled={isBusy}
            onClick={onCreateRoom}
          />
          <EntranceMobileActionButton
            imageCropClassName="h-[383.52%] w-auto -left-[128.98%] -top-[150.56%]"
            label={isBusy ? '처리 중' : '입장하기'}
            disabled={isBusy}
            onClick={onOpenRoomCodeModal}
          />
        </div>

        {errorMessage && (
          <p className="caption-b absolute left-1/2 top-[76%] w-[86.17%] -translate-x-1/2 rounded-full border border-flipbook-light bg-flipbook-paper/88 px-4 py-3 text-center text-flipbook-deep shadow-[0_8px_18px_var(--color-flipbook-shadow)]">
            {errorMessage}
          </p>
        )}
      </motion.div>
    </div>
  )
}

function EntranceMobileIconButton({
  imageSrc,
  imageWidth,
  imageHeight,
  label,
  pressed,
  className,
  onClick,
}: EntranceMobileIconButtonProps) {
  return (
    <motion.button
      type="button"
      aria-label={label}
      aria-pressed={pressed}
      title={label}
      whileTap={{ scale: 0.96 }}
      className={`relative ${className}`}
      onClick={onClick}
    >
      <Image
        src={imageSrc}
        alt=""
        width={imageWidth}
        height={imageHeight}
        sizes="54px"
        className="h-full w-auto object-contain"
      />
      <span className="sr-only">{label}</span>
    </motion.button>
  )
}

function EntranceMobileActionButton({
  imageCropClassName,
  label,
  disabled,
  onClick,
}: EntranceMobileActionButtonProps) {
  return (
    <motion.button
      type="button"
      aria-label={label}
      disabled={disabled}
      whileTap={{ scale: 0.97 }}
      className="relative aspect-[635/267] w-full overflow-visible disabled:cursor-not-allowed disabled:opacity-70"
      onClick={onClick}
    >
      <span className="absolute inset-0 overflow-hidden" aria-hidden>
        <Image
          src={FLIPBOOK_SCENE_IMAGES.actionButtonsSprite}
          alt=""
          width={1536}
          height={1024}
          loading="eager"
          quality={FLIPBOOK_ACTION_BUTTON_IMAGE_QUALITY}
          sizes={FLIPBOOK_ACTION_BUTTON_IMAGE_SIZES}
          className={`absolute w-auto max-w-none ${imageCropClassName}`}
        />
      </span>
      <span className="sr-only">{label}</span>
    </motion.button>
  )
}
