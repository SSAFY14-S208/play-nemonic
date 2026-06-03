import type { Variants } from 'motion/react'

export const memoVariants: Variants = {
  hidden: (tiltDeg: number) => ({
    opacity: 0,
    scale: 1.18,
    y: -12,
    rotate: tiltDeg,
  }),
  tilted: (tiltDeg: number) => ({
    opacity: 1,
    scale: 1,
    y: 0,
    rotate: tiltDeg,
    transition: {
      type: 'spring',
      stiffness: 320,
      damping: 16,
      mass: 0.9,
      opacity: { duration: 0.18 },
    },
  }),
  straight: {
    opacity: 1,
    scale: 1,
    y: 0,
    rotate: 0,
    transition: {
      type: 'spring',
      stiffness: 220,
      damping: 24,
      mass: 0.8,
    },
  },
}

export const stackVariants: Variants = {
  hidden: {},
  tilted: {
    transition: {
      delayChildren: 0.15,
      staggerChildren: 0.45,
    },
  },
  straight: {
    transition: {},
  },
}
