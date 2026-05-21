import type { StaticImageData } from 'next/image'

export interface HowToPlayVisualImage {
  src: StaticImageData | string
  alt: string
  label?: string
}

export type HowToPlayVisual =
  | {
      type: 'room'
      roomCode: string
      participants: string[]
      images: HowToPlayVisualImage[]
      caption: string
    }
  | {
      type: 'single'
      badge: string
      frameLabel: string
      image: HowToPlayVisualImage
      caption: string
    }
  | {
      type: 'handoff'
      previousImage: HowToPlayVisualImage
      currentImage: HowToPlayVisualImage
      hintLabel: string
      caption: string
    }
  | {
      type: 'drawing-hint'
      hintImage: HowToPlayVisualImage
      resultImage: HowToPlayVisualImage
      hintLabel: string
      instruction: string
      caption: string
    }
  | {
      type: 'hint'
      image: HowToPlayVisualImage
      hintLabel: string
      caption: string
    }
  | {
      type: 'stack'
      images: HowToPlayVisualImage[]
      caption: string
    }
  | {
      type: 'filmstrip'
      images: HowToPlayVisualImage[]
      caption: string
    }
  | {
      type: 'onion-skin'
      hintImage: HowToPlayVisualImage
      hintLabel: string
      instruction: string
      caption: string
    }
  | {
      type: 'animation'
      images: HowToPlayVisualImage[]
      frameIntervalMs?: number
      caption: string
    }

export interface HowToPlayPanel {
  id: string
  eyebrow?: string
  title: string
  description: string
  bullets?: string[]
  visual?: HowToPlayVisual
}
