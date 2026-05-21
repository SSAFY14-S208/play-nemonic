'use client'

import Image from 'next/image'
import { motion, type MotionStyle } from 'motion/react'

const FLIPBOOK_BACKGROUND_IMAGES = {
  paper: '/images/flipbook-background/paper-background.png',
  stars: '/images/flipbook-background/stars.png',
  dots: '/images/flipbook-background/dots.png',
  crayon: '/images/flipbook-background/crayon-corners.png',
}

interface FlipbookPaperBackgroundProps {
  layerStyles?: {
    crayon?: MotionStyle
    stars?: MotionStyle
    dots?: MotionStyle
  }
}

export default function FlipbookPaperBackground({
  layerStyles,
}: FlipbookPaperBackgroundProps) {
  return (
    <div aria-hidden className="pointer-events-none absolute inset-0 z-0 overflow-hidden">
      <Image
        src={FLIPBOOK_BACKGROUND_IMAGES.paper}
        alt=""
        fill
        priority
        sizes="100vw"
        className="object-cover"
      />
      <div className="absolute inset-0 bg-flipbook-background/10" />
      <motion.div className="absolute inset-[-3%]" style={layerStyles?.crayon}>
        <Image
          src={FLIPBOOK_BACKGROUND_IMAGES.crayon}
          alt=""
          fill
          sizes="106vw"
          className="object-fill"
        />
      </motion.div>
      <motion.div className="absolute inset-[-3%]" style={layerStyles?.stars}>
        <Image
          src={FLIPBOOK_BACKGROUND_IMAGES.stars}
          alt=""
          fill
          sizes="106vw"
          className="object-fill"
        />
      </motion.div>
      <motion.div className="absolute inset-[-3%]" style={layerStyles?.dots}>
        <Image
          src={FLIPBOOK_BACKGROUND_IMAGES.dots}
          alt=""
          fill
          sizes="106vw"
          className="object-fill"
        />
      </motion.div>
    </div>
  )
}
