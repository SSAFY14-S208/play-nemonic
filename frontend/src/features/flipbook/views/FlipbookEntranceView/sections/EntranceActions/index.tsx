import Image from 'next/image'
import { motion } from 'motion/react'

import {
  FLIPBOOK_ACTION_BUTTON_IMAGE_QUALITY,
  FLIPBOOK_ACTION_BUTTON_IMAGE_SIZES,
  FLIPBOOK_ENTRANCE_ACTIONS,
  FLIPBOOK_SCENE_IMAGES,
  type FlipbookEntranceActionKey,
} from '../../constants'

interface EntranceActionsProps {
  visible: boolean
  interactive: boolean
  shouldInstantCompleteIntro: boolean
  isBusy: boolean
  errorMessage: string | null
  actionHandlers: Record<FlipbookEntranceActionKey, () => void>
}

interface EntranceImageButtonProps {
  buttonClassName: string
  imageCropClassName: string
  label: string
  disabled: boolean
  onClick: () => void
}

export function EntranceActions({
  visible,
  interactive,
  shouldInstantCompleteIntro,
  isBusy,
  errorMessage,
  actionHandlers,
}: EntranceActionsProps) {
  return (
    <motion.div
      className="pointer-events-none absolute inset-0 z-20"
      initial={false}
      animate={
        visible
          ? {
              opacity: 1,
              y: 0,
              transition: {
                duration: shouldInstantCompleteIntro ? 0 : 0.62,
                ease: [0.22, 0.8, 0.22, 1],
              },
            }
          : {
              opacity: 0,
              y: 28,
              transition: { duration: 0.18 },
            }
      }
    >
      <div className="absolute left-[13.49%] top-[31.76%] aspect-[486/238] w-[28.49%] overflow-hidden">
        <Image
          src={FLIPBOOK_SCENE_IMAGES.titleLogoSprite}
          alt="플립북"
          width={1536}
          height={1024}
          priority
          sizes="30vw"
          className="absolute h-[184.45%] w-auto max-w-none -left-[17.95%] -top-[56.72%]"
        />
      </div>

      {FLIPBOOK_ENTRANCE_ACTIONS.map((action) => (
        <EntranceImageButton
          key={action.key}
          buttonClassName={action.buttonClassName}
          imageCropClassName={action.imageCropClassName}
          label={isBusy ? '처리 중' : action.label}
          disabled={isBusy || !interactive}
          onClick={actionHandlers[action.key]}
        />
      ))}

      {errorMessage && (
        <p className="caption-b absolute left-[11.90%] top-[75.8%] w-[31.67%] rounded-full border border-flipbook-light bg-flipbook-paper/88 px-5 py-3 text-center text-flipbook-deep shadow-[0_8px_18px_var(--color-flipbook-shadow)]">
          {errorMessage}
        </p>
      )}
    </motion.div>
  )
}

function EntranceImageButton({
  buttonClassName,
  imageCropClassName,
  label,
  disabled,
  onClick,
}: EntranceImageButtonProps) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      aria-label={label}
      className={`pointer-events-auto absolute overflow-visible transform-gpu transition-transform duration-150 ease-out will-change-transform hover:-translate-y-1 active:translate-y-px active:scale-[0.99] focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-flipbook-primary disabled:cursor-not-allowed disabled:opacity-70 disabled:hover:translate-y-0 disabled:active:translate-y-0 disabled:active:scale-100 ${buttonClassName}`}
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
    </button>
  )
}
