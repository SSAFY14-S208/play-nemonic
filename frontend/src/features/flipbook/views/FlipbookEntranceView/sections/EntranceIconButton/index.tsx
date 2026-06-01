import Image from 'next/image'
import { motion } from 'motion/react'

interface EntranceIconButtonProps {
  imageSrc: string
  imageWidth: number
  imageHeight: number
  label: string
  pressed?: boolean
  onClick: () => void
}

export function EntranceIconButton({
  imageSrc,
  imageWidth,
  imageHeight,
  label,
  pressed,
  onClick,
}: EntranceIconButtonProps) {
  return (
    <motion.button
      type="button"
      aria-label={label}
      aria-pressed={pressed}
      title={label}
      whileHover={{ y: -3, scale: 1.035 }}
      whileTap={{ y: 1, scale: 0.97 }}
      className="relative grid size-[clamp(48px,4.6vw,70px)] place-items-center focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-flipbook-primary"
      onClick={onClick}
    >
      <Image
        src={imageSrc}
        alt=""
        width={imageWidth}
        height={imageHeight}
        sizes="70px"
        className="h-full w-auto object-contain"
      />
      <span className="sr-only">{label}</span>
    </motion.button>
  )
}
