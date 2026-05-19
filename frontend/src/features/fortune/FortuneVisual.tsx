'use client'

/* eslint-disable @next/next/no-img-element */

import type { CSSProperties } from 'react'
import { useCallback, useEffect, useRef, useState } from 'react'
import { useShallow } from 'zustand/react/shallow'

import { useNemonicPrintVibration } from '@/shared/hooks'
import { cn } from '@/shared/libs'

import {
  FORTUNE_PRINT_FALLBACK_TIMEOUT_SECONDS,
  FORTUNE_PRINT_SOUND_VIDEO_TIME_SECONDS,
  FORTUNE_PRINT_VIDEO_PATH,
  FORTUNE_REDUCED_MOTION_DURATION_SECONDS,
} from './constants'
import { useFortuneSessionStore } from './fortuneSessionStore'
import { useFortuneReducedMotion } from './hooks'

interface FortuneVisualProps {
  playEntrySpotlight?: boolean
  onEntrySceneReady?: () => void
  onPrintStart?: () => void
  onPrintComplete: () => void
}

/* ----- shared utility class fragments ----- */

const LAYER_BASE_CLASS =
  'absolute block max-w-none select-none pointer-events-none [will-change:transform,opacity,filter]'

const CURTAIN_PIECE_BASE_CLASS = cn(
  'absolute block w-auto h-auto max-w-none pointer-events-none [-webkit-user-drag:none] touch-none select-none [will-change:transform]',
  '[transform:translate3d(var(--fortune-curtain-rest-x),var(--fortune-curtain-rest-y),0)_rotate(var(--fortune-curtain-rest-rotate))]',
  '[transition:filter_120ms_ease,transform_120ms_cubic-bezier(0.19,1,0.22,1)]',
  'motion-reduce:animate-none motion-reduce:transition-none',
  'motion-reduce:[transform:translate3d(calc(var(--fortune-curtain-rest-x)+var(--fortune-curtain-idle-pull-x)),calc(var(--fortune-curtain-rest-y)+var(--fortune-curtain-idle-pull-y)),0)_rotate(calc(var(--fortune-curtain-rest-rotate)+var(--fortune-curtain-idle-pull-rotate)))_skewX(var(--fortune-curtain-idle-pull-skew-x))_scaleX(var(--fortune-curtain-idle-pull-scale-x))_scaleY(var(--fortune-curtain-idle-pull-scale-y))]',
)

const CURTAIN_ACTIVE_CLASS = cn(
  'animate-fortune-2d-curtain-tap-ripple',
  '[filter:drop-shadow(0_0.88rem_1.42rem_rgba(0,0,0,0.4))]',
  '[transform:translate3d(var(--fortune-curtain-rest-x),var(--fortune-curtain-rest-y),0)_rotate(var(--fortune-curtain-rest-rotate))_skewX(0deg)_scaleX(1)_scaleY(1)]',
  '[transition-duration:220ms]',
)

const CURTAIN_SOFT_ACTIVE_CLASS = cn(
  'animate-fortune-2d-curtain-soft-tap-ripple',
  '[filter:drop-shadow(0_0.86rem_1.42rem_rgba(0,0,0,0.38))]',
  '[transform:translate3d(var(--fortune-curtain-rest-x),var(--fortune-curtain-rest-y),0)_rotate(var(--fortune-curtain-rest-rotate))_skewX(0deg)_scaleX(1)_scaleY(1)]',
  '[transition-duration:320ms]',
)

const CURTAIN_PIECE_REDUCED_MOTION_SOFT_CLASS =
  'motion-reduce:[transform:translate3d(calc(var(--fortune-curtain-rest-x)+var(--fortune-curtain-soft-idle-x)),calc(var(--fortune-curtain-rest-y)+var(--fortune-curtain-soft-idle-y)),0)_rotate(calc(var(--fortune-curtain-rest-rotate)+var(--fortune-curtain-soft-idle-rotate)))_skewX(var(--fortune-curtain-soft-idle-skew-x))_scaleX(var(--fortune-curtain-soft-idle-scale-x))_scaleY(var(--fortune-curtain-soft-idle-scale-y))]'

const CURTAIN_LOWER_MASK_CLASS = cn(
  '[mask-image:linear-gradient(180deg,rgba(0,0,0,0)_0%,rgba(0,0,0,0.16)_8%,rgba(0,0,0,0.72)_18%,#000_30%)]',
  '[-webkit-mask-image:linear-gradient(180deg,rgba(0,0,0,0)_0%,rgba(0,0,0,0.16)_8%,rgba(0,0,0,0.72)_18%,#000_30%)]',
  '[mask-repeat:no-repeat] [-webkit-mask-repeat:no-repeat]',
  '[mask-size:100%_100%] [-webkit-mask-size:100%_100%]',
)

const PRINT_VIDEO_HIDE_CLASS = 'animate-none opacity-0'

const PRINT_VIDEO_CURTAIN_TRANSITION_CLASS =
  '[animation:none] [transition:transform_2s_cubic-bezier(0.32,0.72,0.24,1),opacity_1.6s_ease-out]'

const SPARKLE_BASE_CLASS = cn(
  'absolute',
  'w-[clamp(0.28rem,0.6vw,0.52rem)] h-[clamp(0.28rem,0.6vw,0.52rem)]',
  '[clip-path:polygon(50%_0,61%_36%,100%_50%,61%_64%,50%_100%,39%_64%,0_50%,39%_36%)]',
  'bg-[#f4d5ff]',
  '[filter:drop-shadow(0_0_0.34rem_rgba(229,185,255,0.84))_drop-shadow(0_0_0.78rem_rgba(133,85,255,0.58))]',
  'animate-fortune-2d-sparkle-twinkle motion-reduce:animate-none',
)

/* ----- per-curtain configuration ----- */

interface CurtainLayerConfig {
  className: string
  imageSrc: string
  style: CSSProperties
  positionClassName: string
  hasLowerMask: boolean
  reducedMotionTransform?: string
}

const FORTUNE_CURTAIN_LAYERS: readonly CurtainLayerConfig[] = [
  {
    className: 'fortune-2d-curtain-lower-left',
    imageSrc: '/images/fortune/stage/curtain-lower-left-tight.png',
    positionClassName:
      'bottom-[-8.8dvh] left-[-1.2vw] z-[1] h-[min(54dvh,35rem)] opacity-[0.94] max-[767px]:portrait:left-[-4vw] max-[767px]:portrait:bottom-[-8.4dvh] max-[767px]:portrait:h-[min(54dvh,35rem)]',
    hasLowerMask: true,
    style: {
      '--fortune-curtain-hover-x': 'clamp(0.016rem, 0.055vw, 0.045rem)',
      '--fortune-curtain-hover-y': 'clamp(0.004rem, 0.025dvh, 0.014rem)',
      '--fortune-curtain-hover-rotate': '0.075deg',
      '--fortune-curtain-pull-skew-x': '0.5deg',
      '--fortune-curtain-pull-scale-x': '0.9982',
      '--fortune-curtain-pull-scale-y': '1.0032',
      '--fortune-curtain-sway-x': 'clamp(-0.022rem, -0.045vw, -0.008rem)',
      '--fortune-curtain-sway-y': 'clamp(-0.014rem, -0.03dvh, -0.004rem)',
      '--fortune-curtain-sway-rotate': '-0.055deg',
      '--fortune-curtain-recoil-skew-x': '-0.22deg',
      '--fortune-curtain-recoil-scale-x': '1.0018',
      '--fortune-curtain-recoil-scale-y': '0.999',
      '--fortune-curtain-settle-x': 'clamp(0.004rem, 0.015vw, 0.014rem)',
      '--fortune-curtain-settle-y': 'clamp(0.001rem, 0.006dvh, 0.004rem)',
      '--fortune-curtain-settle-rotate': '0.018deg',
      '--fortune-curtain-settle-skew-x': '0.04deg',
      '--fortune-curtain-settle-scale-x': '1.0004',
      '--fortune-curtain-settle-scale-y': '1',
      '--fortune-curtain-idle-pull-x': '0px',
      '--fortune-curtain-idle-pull-y': '0px',
      '--fortune-curtain-idle-pull-skew-x': '0deg',
      '--fortune-curtain-idle-pull-scale-x': '1',
      '--fortune-curtain-soft-x': 'clamp(0.004rem, 0.018vw, 0.014rem)',
      '--fortune-curtain-soft-y': 'clamp(0.001rem, 0.006dvh, 0.004rem)',
      '--fortune-curtain-soft-rotate': '0.006deg',
      '--fortune-curtain-soft-skew-x': '0.01deg',
      '--fortune-curtain-soft-idle-x': '0px',
      '--fortune-curtain-soft-idle-y': '0px',
      '--fortune-curtain-soft-idle-rotate': '0deg',
      '--fortune-curtain-soft-idle-skew-x': '0deg',
      filter:
        'drop-shadow(0 0.85rem 1.5rem rgba(0, 0, 0, 0.42))',
      transformOrigin: '16% 4%',
    } as CSSProperties,
  },
  {
    className: 'fortune-2d-curtain-lower-right',
    imageSrc: '/images/fortune/stage/curtain-lower-right-tight.png',
    positionClassName:
      'right-[-1.2vw] bottom-[-8.8dvh] z-[1] h-[min(54dvh,35rem)] opacity-[0.94] max-[767px]:portrait:right-[-4vw] max-[767px]:portrait:bottom-[-8.4dvh] max-[767px]:portrait:h-[min(54dvh,35rem)]',
    hasLowerMask: true,
    style: {
      '--fortune-curtain-hover-x': 'clamp(-0.045rem, -0.055vw, -0.016rem)',
      '--fortune-curtain-hover-y': 'clamp(0.004rem, 0.025dvh, 0.014rem)',
      '--fortune-curtain-hover-rotate': '-0.075deg',
      '--fortune-curtain-pull-skew-x': '-0.5deg',
      '--fortune-curtain-pull-scale-x': '0.9982',
      '--fortune-curtain-pull-scale-y': '1.0032',
      '--fortune-curtain-sway-x': 'clamp(0.008rem, 0.045vw, 0.022rem)',
      '--fortune-curtain-sway-y': 'clamp(-0.014rem, -0.03dvh, -0.004rem)',
      '--fortune-curtain-sway-rotate': '0.055deg',
      '--fortune-curtain-recoil-skew-x': '0.22deg',
      '--fortune-curtain-recoil-scale-x': '1.0018',
      '--fortune-curtain-recoil-scale-y': '0.999',
      '--fortune-curtain-settle-x': 'clamp(-0.014rem, -0.015vw, -0.004rem)',
      '--fortune-curtain-settle-y': 'clamp(0.001rem, 0.006dvh, 0.004rem)',
      '--fortune-curtain-settle-rotate': '-0.018deg',
      '--fortune-curtain-settle-skew-x': '-0.04deg',
      '--fortune-curtain-settle-scale-x': '1.0004',
      '--fortune-curtain-settle-scale-y': '1',
      '--fortune-curtain-idle-pull-x': '0px',
      '--fortune-curtain-idle-pull-y': '0px',
      '--fortune-curtain-idle-pull-skew-x': '0deg',
      '--fortune-curtain-idle-pull-scale-x': '1',
      '--fortune-curtain-soft-x': 'clamp(-0.014rem, -0.018vw, -0.004rem)',
      '--fortune-curtain-soft-y': 'clamp(0.001rem, 0.006dvh, 0.004rem)',
      '--fortune-curtain-soft-rotate': '-0.006deg',
      '--fortune-curtain-soft-skew-x': '-0.01deg',
      '--fortune-curtain-soft-idle-x': '0px',
      '--fortune-curtain-soft-idle-y': '0px',
      '--fortune-curtain-soft-idle-rotate': '0deg',
      '--fortune-curtain-soft-idle-skew-x': '0deg',
      filter:
        'drop-shadow(0 0.85rem 1.5rem rgba(0, 0, 0, 0.42))',
      transformOrigin: '84% 4%',
    } as CSSProperties,
  },
  {
    className: 'fortune-2d-curtain-middle-left',
    imageSrc: '/images/fortune/stage/curtain-middle-left-tight.png',
    positionClassName:
      'top-[1.2dvh] left-[-4.6vw] z-[2] w-[min(30vw,24rem)] opacity-[0.96] max-[767px]:portrait:top-[1dvh] max-[767px]:portrait:left-[-18vw] max-[767px]:portrait:w-[36vw]',
    hasLowerMask: false,
    style: {
      '--fortune-curtain-hover-x': 'clamp(0.012rem, 0.045vw, 0.034rem)',
      '--fortune-curtain-hover-y': 'clamp(0.003rem, 0.02dvh, 0.012rem)',
      '--fortune-curtain-hover-rotate': '0.06deg',
      '--fortune-curtain-pull-skew-x': '0.42deg',
      '--fortune-curtain-pull-scale-x': '0.9987',
      '--fortune-curtain-pull-scale-y': '1.0026',
      '--fortune-curtain-sway-x': 'clamp(-0.018rem, -0.036vw, -0.006rem)',
      '--fortune-curtain-sway-y': 'clamp(-0.012rem, -0.026dvh, -0.004rem)',
      '--fortune-curtain-sway-rotate': '-0.044deg',
      '--fortune-curtain-recoil-skew-x': '-0.18deg',
      '--fortune-curtain-recoil-scale-x': '1.0014',
      '--fortune-curtain-recoil-scale-y': '0.9992',
      '--fortune-curtain-settle-x': 'clamp(0.003rem, 0.012vw, 0.011rem)',
      '--fortune-curtain-settle-y': 'clamp(0.001rem, 0.005dvh, 0.003rem)',
      '--fortune-curtain-settle-rotate': '0.014deg',
      '--fortune-curtain-settle-skew-x': '0.032deg',
      '--fortune-curtain-settle-scale-x': '1.0003',
      '--fortune-curtain-settle-scale-y': '1',
      '--fortune-curtain-idle-pull-x': '0px',
      '--fortune-curtain-idle-pull-y': '0px',
      '--fortune-curtain-idle-pull-skew-x': '0deg',
      '--fortune-curtain-idle-pull-scale-x': '1',
      '--fortune-curtain-soft-x': 'clamp(0.003rem, 0.014vw, 0.011rem)',
      '--fortune-curtain-soft-y': 'clamp(0.001rem, 0.005dvh, 0.003rem)',
      '--fortune-curtain-soft-rotate': '0.005deg',
      '--fortune-curtain-soft-skew-x': '0.008deg',
      '--fortune-curtain-soft-idle-x': '0px',
      '--fortune-curtain-soft-idle-y': '0px',
      '--fortune-curtain-soft-idle-rotate': '0deg',
      '--fortune-curtain-soft-idle-skew-x': '0deg',
      filter:
        'drop-shadow(0 0.72rem 1.25rem rgba(0, 0, 0, 0.38))',
      transformOrigin: '86% 6%',
    } as CSSProperties,
  },
  {
    className: 'fortune-2d-curtain-middle-right',
    imageSrc: '/images/fortune/stage/curtain-middle-right-tight.png',
    positionClassName:
      'top-[1.2dvh] right-[-4.6vw] z-[2] w-[min(30vw,24rem)] opacity-[0.96] max-[767px]:portrait:top-[1dvh] max-[767px]:portrait:right-[-18vw] max-[767px]:portrait:w-[36vw]',
    hasLowerMask: false,
    style: {
      '--fortune-curtain-hover-x': 'clamp(-0.034rem, -0.045vw, -0.012rem)',
      '--fortune-curtain-hover-y': 'clamp(0.003rem, 0.02dvh, 0.012rem)',
      '--fortune-curtain-hover-rotate': '-0.06deg',
      '--fortune-curtain-pull-skew-x': '-0.42deg',
      '--fortune-curtain-pull-scale-x': '0.9987',
      '--fortune-curtain-pull-scale-y': '1.0026',
      '--fortune-curtain-sway-x': 'clamp(0.006rem, 0.036vw, 0.018rem)',
      '--fortune-curtain-sway-y': 'clamp(-0.012rem, -0.026dvh, -0.004rem)',
      '--fortune-curtain-sway-rotate': '0.044deg',
      '--fortune-curtain-recoil-skew-x': '0.18deg',
      '--fortune-curtain-recoil-scale-x': '1.0014',
      '--fortune-curtain-recoil-scale-y': '0.9992',
      '--fortune-curtain-settle-x': 'clamp(-0.011rem, -0.012vw, -0.003rem)',
      '--fortune-curtain-settle-y': 'clamp(0.001rem, 0.005dvh, 0.003rem)',
      '--fortune-curtain-settle-rotate': '-0.014deg',
      '--fortune-curtain-settle-skew-x': '-0.032deg',
      '--fortune-curtain-settle-scale-x': '1.0003',
      '--fortune-curtain-settle-scale-y': '1',
      '--fortune-curtain-idle-pull-x': '0px',
      '--fortune-curtain-idle-pull-y': '0px',
      '--fortune-curtain-idle-pull-skew-x': '0deg',
      '--fortune-curtain-idle-pull-scale-x': '1',
      '--fortune-curtain-soft-x': 'clamp(-0.011rem, -0.014vw, -0.003rem)',
      '--fortune-curtain-soft-y': 'clamp(0.001rem, 0.005dvh, 0.003rem)',
      '--fortune-curtain-soft-rotate': '-0.005deg',
      '--fortune-curtain-soft-skew-x': '-0.008deg',
      '--fortune-curtain-soft-idle-x': '0px',
      '--fortune-curtain-soft-idle-y': '0px',
      '--fortune-curtain-soft-idle-rotate': '0deg',
      '--fortune-curtain-soft-idle-skew-x': '0deg',
      filter:
        'drop-shadow(0 0.72rem 1.25rem rgba(0, 0, 0, 0.38))',
      transformOrigin: '14% 6%',
    } as CSSProperties,
  },
  {
    className: 'fortune-2d-curtain-top-left',
    imageSrc: '/images/fortune/stage/curtain-top-left-tight.png',
    positionClassName:
      'top-[max(-4.35rem,-6.2dvh)] left-[-4.8vw] z-[3] w-[min(45vw,43rem)] opacity-[0.98] max-[767px]:portrait:top-[max(-4.45rem,-6.4dvh)] max-[767px]:portrait:left-[-5vw] max-[767px]:portrait:w-[46vw]',
    hasLowerMask: false,
    style: {
      '--fortune-curtain-hover-x': 'clamp(0.008rem, 0.032vw, 0.022rem)',
      '--fortune-curtain-hover-y': 'clamp(0.002rem, 0.014dvh, 0.008rem)',
      '--fortune-curtain-hover-rotate': '0.045deg',
      '--fortune-curtain-pull-skew-x': '0.28deg',
      '--fortune-curtain-pull-scale-x': '0.999',
      '--fortune-curtain-pull-scale-y': '1.0018',
      '--fortune-curtain-sway-x': 'clamp(-0.012rem, -0.026vw, -0.004rem)',
      '--fortune-curtain-sway-y': 'clamp(-0.008rem, -0.018dvh, -0.003rem)',
      '--fortune-curtain-sway-rotate': '-0.032deg',
      '--fortune-curtain-recoil-skew-x': '-0.12deg',
      '--fortune-curtain-recoil-scale-x': '1.001',
      '--fortune-curtain-recoil-scale-y': '0.9994',
      '--fortune-curtain-settle-x': 'clamp(0.002rem, 0.008vw, 0.007rem)',
      '--fortune-curtain-settle-y': 'clamp(0.001rem, 0.004dvh, 0.002rem)',
      '--fortune-curtain-settle-rotate': '0.01deg',
      '--fortune-curtain-settle-skew-x': '0.024deg',
      '--fortune-curtain-settle-scale-x': '1.0002',
      '--fortune-curtain-settle-scale-y': '1',
      '--fortune-curtain-idle-pull-x': '0px',
      '--fortune-curtain-idle-pull-y': '0px',
      '--fortune-curtain-idle-pull-skew-x': '0deg',
      '--fortune-curtain-idle-pull-scale-x': '1',
      '--fortune-curtain-soft-x': 'clamp(0.002rem, 0.01vw, 0.007rem)',
      '--fortune-curtain-soft-y': 'clamp(0.001rem, 0.003dvh, 0.002rem)',
      '--fortune-curtain-soft-rotate': '0.003deg',
      '--fortune-curtain-soft-skew-x': '0.005deg',
      '--fortune-curtain-soft-idle-x': '0px',
      '--fortune-curtain-soft-idle-y': '0px',
      '--fortune-curtain-soft-idle-rotate': '0deg',
      '--fortune-curtain-soft-idle-skew-x': '0deg',
      filter:
        'drop-shadow(0 0.5rem 1rem rgba(0, 0, 0, 0.34))',
      transformOrigin: '82% 5%',
    } as CSSProperties,
  },
  {
    className: 'fortune-2d-curtain-top-right',
    imageSrc: '/images/fortune/stage/curtain-top-right-tight.png',
    positionClassName:
      'top-[max(-4.35rem,-6.2dvh)] right-[-4.8vw] z-[3] w-[min(45vw,43rem)] opacity-[0.98] max-[767px]:portrait:top-[max(-4.45rem,-6.4dvh)] max-[767px]:portrait:right-[-5vw] max-[767px]:portrait:w-[46vw]',
    hasLowerMask: false,
    style: {
      '--fortune-curtain-hover-x': 'clamp(-0.022rem, -0.032vw, -0.008rem)',
      '--fortune-curtain-hover-y': 'clamp(0.002rem, 0.014dvh, 0.008rem)',
      '--fortune-curtain-hover-rotate': '-0.045deg',
      '--fortune-curtain-pull-skew-x': '-0.28deg',
      '--fortune-curtain-pull-scale-x': '0.999',
      '--fortune-curtain-pull-scale-y': '1.0018',
      '--fortune-curtain-sway-x': 'clamp(0.004rem, 0.026vw, 0.012rem)',
      '--fortune-curtain-sway-y': 'clamp(-0.008rem, -0.018dvh, -0.003rem)',
      '--fortune-curtain-sway-rotate': '0.032deg',
      '--fortune-curtain-recoil-skew-x': '0.12deg',
      '--fortune-curtain-recoil-scale-x': '1.001',
      '--fortune-curtain-recoil-scale-y': '0.9994',
      '--fortune-curtain-settle-x': 'clamp(-0.007rem, -0.008vw, -0.002rem)',
      '--fortune-curtain-settle-y': 'clamp(0.001rem, 0.004dvh, 0.002rem)',
      '--fortune-curtain-settle-rotate': '-0.01deg',
      '--fortune-curtain-settle-skew-x': '-0.024deg',
      '--fortune-curtain-settle-scale-x': '1.0002',
      '--fortune-curtain-settle-scale-y': '1',
      '--fortune-curtain-idle-pull-x': '0px',
      '--fortune-curtain-idle-pull-y': '0px',
      '--fortune-curtain-idle-pull-skew-x': '0deg',
      '--fortune-curtain-idle-pull-scale-x': '1',
      '--fortune-curtain-soft-x': 'clamp(-0.007rem, -0.01vw, -0.002rem)',
      '--fortune-curtain-soft-y': 'clamp(0.001rem, 0.003dvh, 0.002rem)',
      '--fortune-curtain-soft-rotate': '-0.003deg',
      '--fortune-curtain-soft-skew-x': '-0.005deg',
      '--fortune-curtain-soft-idle-x': '0px',
      '--fortune-curtain-soft-idle-y': '0px',
      '--fortune-curtain-soft-idle-rotate': '0deg',
      '--fortune-curtain-soft-idle-skew-x': '0deg',
      filter:
        'drop-shadow(0 0.5rem 1rem rgba(0, 0, 0, 0.34))',
      transformOrigin: '18% 5%',
    } as CSSProperties,
  },
] as const

const FORTUNE_CURTAIN_HIT_ZONES = [
  {
    className:
      'top-[56%] bottom-0 left-0 w-[38%]',
    curtainClassName: 'fortune-2d-curtain-lower-left',
    key: 'lower-left',
  },
  {
    className:
      'top-[56%] right-0 bottom-0 w-[38%]',
    curtainClassName: 'fortune-2d-curtain-lower-right',
    key: 'lower-right',
  },
  {
    className:
      'top-[24%] left-0 w-[38%] h-[32%]',
    curtainClassName: 'fortune-2d-curtain-middle-left',
    key: 'middle-left',
  },
  {
    className:
      'top-[24%] right-0 w-[38%] h-[32%]',
    curtainClassName: 'fortune-2d-curtain-middle-right',
    key: 'middle-right',
  },
  {
    className:
      'top-0 left-0 z-[9] w-[50%] h-[24%]',
    curtainClassName: 'fortune-2d-curtain-top-left',
    key: 'top-left',
  },
  {
    className:
      'top-0 right-0 z-[9] w-[50%] h-[24%]',
    curtainClassName: 'fortune-2d-curtain-top-right',
    key: 'top-right',
  },
] as const

const FORTUNE_SPARKLE_POSITIONS: readonly { style: CSSProperties; extraClass?: string }[] = [
  { style: { top: '13%', left: '45%' } },
  { style: { top: '25%', left: '62%', animationDelay: '-0.8s' } },
  { style: { top: '41%', left: '36%', animationDelay: '-1.7s' } },
  { style: { top: '44%', right: '36%', animationDelay: '-0.4s' } },
  {
    style: {
      top: '57%',
      left: '48%',
      width: 'clamp(0.46rem, 0.95vw, 0.86rem)',
      height: 'clamp(0.46rem, 0.95vw, 0.86rem)',
      animationDelay: '-2.1s',
    },
  },
  { style: { top: '34%', left: '52%', background: '#fff3c9', animationDelay: '-1.2s' } },
  { style: { top: '66%', left: '38%', animationDelay: '-2.4s' } },
  { style: { top: '68%', right: '38%', background: '#fff3c9', animationDelay: '-0.2s' } },
]

const CURTAIN_FRAME_TOP_RATIO = 0.24
const CURTAIN_FRAME_SIDE_RATIO = 0.34
const CURTAIN_FRAME_MIDDLE_RATIO = 0.56
const CUBE_HOVER_LEFT_RATIO = 0.402
const CUBE_HOVER_TOP_RATIO = 0.58
const CUBE_HOVER_WIDTH_RATIO = 0.2
const CUBE_HOVER_HEIGHT_RATIO = 0.22

export default function FortuneVisual({
  playEntrySpotlight = false,
  onEntrySceneReady,
  onPrintStart,
  onPrintComplete,
}: FortuneVisualProps) {
  const { isPrinting, result } = useFortuneSessionStore(
    useShallow((state) => ({
      isPrinting: state.step === 'printing',
      result: state.result,
    })),
  )
  const prefersReducedMotion = useFortuneReducedMotion()
  // 운세 인쇄 애니메이션 동안 디바이스 진동. reduced-motion 환경에서도
  // 햅틱은 시각 모션과 별개로 활성화한다 (접근성 측면에서 햅틱은 보조 피드백).
  useNemonicPrintVibration(isPrinting)
  const curtainFrameRef = useRef<HTMLDivElement>(null)
  const printVideoRef = useRef<HTMLVideoElement>(null)
  const printStartFiredRef = useRef(false)
  const printCompleteFiredRef = useRef(false)
  const [activeCurtainClassName, setActiveCurtainClassName] = useState<string | null>(null)
  const [isCubeHovered, setIsCubeHovered] = useState(false)
  const shouldShowPrintVideo = isPrinting && !prefersReducedMotion

  const firePrintCompleteOnce = () => {
    if (printCompleteFiredRef.current) {
      return
    }
    printCompleteFiredRef.current = true
    onPrintComplete()
  }

  const firePrintStartOnce = useCallback(() => {
    if (printStartFiredRef.current) {
      return
    }

    printStartFiredRef.current = true
    onPrintStart?.()
  }, [onPrintStart])

  useEffect(() => {
    if (!playEntrySpotlight) {
      return
    }

    const frameId = window.requestAnimationFrame(() => {
      onEntrySceneReady?.()
    })

    return () => {
      window.cancelAnimationFrame(frameId)
    }
  }, [onEntrySceneReady, playEntrySpotlight])

  useEffect(() => {
    if (!isPrinting) {
      printStartFiredRef.current = false
      return
    }

    printStartFiredRef.current = false
    printCompleteFiredRef.current = false

    if (prefersReducedMotion) {
      firePrintStartOnce()
    }

    const fallbackDuration = prefersReducedMotion
      ? FORTUNE_REDUCED_MOTION_DURATION_SECONDS
      : FORTUNE_PRINT_FALLBACK_TIMEOUT_SECONDS
    const timerId = window.setTimeout(() => {
      if (!printCompleteFiredRef.current) {
        printCompleteFiredRef.current = true
        onPrintComplete()
      }
    }, fallbackDuration * 1000)

    return () => {
      window.clearTimeout(timerId)
    }
  }, [firePrintStartOnce, isPrinting, onPrintComplete, prefersReducedMotion])

  useEffect(() => {
    if (!shouldShowPrintVideo) {
      return
    }

    const videoElement = printVideoRef.current
    if (!videoElement) {
      return
    }

    videoElement.currentTime = 0
    const playPromise = videoElement.play()
    if (playPromise && typeof playPromise.catch === 'function') {
      playPromise.catch(() => {
        if (!printCompleteFiredRef.current) {
          printCompleteFiredRef.current = true
          onPrintComplete()
        }
      })
    }

    return () => {
      videoElement.pause()
    }
  }, [onPrintComplete, shouldShowPrintVideo])

  useEffect(() => {
    if (isPrinting) {
      return
    }

    const handleWindowCurtainMove = (event: MouseEvent | PointerEvent) => {
      const curtainFrame = curtainFrameRef.current

      if (!curtainFrame) {
        return
      }

      const frameBounds = curtainFrame.getBoundingClientRect()
      const isPointerOutsideFrame =
        event.clientX < frameBounds.left ||
        event.clientX > frameBounds.right ||
        event.clientY < frameBounds.top ||
        event.clientY > frameBounds.bottom

      if (isPointerOutsideFrame) {
        setActiveCurtainClassName((currentClassName) => (currentClassName === null ? currentClassName : null))
        setIsCubeHovered(false)
        return
      }

      const pointerXRatio = (event.clientX - frameBounds.left) / frameBounds.width
      const pointerYRatio = (event.clientY - frameBounds.top) / frameBounds.height
      const curtainClassName = getCurtainClassNameFromFramePosition(pointerXRatio, pointerYRatio)
      const isPointerOverCube = getIsPointerOverCube(pointerXRatio, pointerYRatio)

      setActiveCurtainClassName((currentClassName) =>
        currentClassName === curtainClassName ? currentClassName : curtainClassName,
      )
      setIsCubeHovered((currentIsCubeHovered) =>
        currentIsCubeHovered === isPointerOverCube ? currentIsCubeHovered : isPointerOverCube,
      )
    }

    const handleWindowCurtainLeave = () => {
      setActiveCurtainClassName(null)
      setIsCubeHovered(false)
    }

    const handleWindowMouseOut = (event: MouseEvent) => {
      if (event.relatedTarget === null) {
        setActiveCurtainClassName(null)
        setIsCubeHovered(false)
      }
    }

    window.addEventListener('mousemove', handleWindowCurtainMove)
    window.addEventListener('mouseout', handleWindowMouseOut)
    window.addEventListener('pointermove', handleWindowCurtainMove)
    window.addEventListener('pointerleave', handleWindowCurtainLeave)

    return () => {
      window.removeEventListener('mousemove', handleWindowCurtainMove)
      window.removeEventListener('mouseout', handleWindowMouseOut)
      window.removeEventListener('pointermove', handleWindowCurtainMove)
      window.removeEventListener('pointerleave', handleWindowCurtainLeave)
    }
  }, [isPrinting])

  const effectiveActiveCurtainClassName = isPrinting ? null : activeCurtainClassName
  const effectiveIsCubeHovered = isPrinting ? false : isCubeHovered
  const activeCurtainSide = getCurtainSideFromClassName(effectiveActiveCurtainClassName)

  // Cube animations vary by state (printing wins over hover)
  const cubeAnimationClass = isPrinting
    ? 'animate-fortune-2d-cube-pulse [animation:fortune-2d-cube-pulse_1.5s_ease-in-out_infinite,fortune-2d-print-cube_var(--fortune-print-duration,4.2s)_cubic-bezier(0.18,0.78,0.3,1)_both]'
    : effectiveIsCubeHovered
      ? 'animate-fortune-2d-cube-pulse'
      : ''

  const cubeFlareAnimationClass = isPrinting
    ? '[animation:fortune-2d-flare-pulse_0.82s_ease-in-out_infinite]'
    : effectiveIsCubeHovered
      ? '[animation:fortune-2d-flare-pulse_2.4s_ease-in-out_infinite]'
      : ''

  const printNoteAnimationClass = isPrinting ? 'animate-fortune-2d-note-print' : ''

  // print-video-active overrides
  const printVideoCurtainSlideClass = (curtainClassName: string) => {
    if (!shouldShowPrintVideo) return ''
    if (curtainClassName.startsWith('fortune-2d-curtain-top-')) {
      return cn(PRINT_VIDEO_CURTAIN_TRANSITION_CLASS, '[transform:translate3d(0,-120%,0)]')
    }
    if (curtainClassName === 'fortune-2d-curtain-middle-left') {
      return cn(PRINT_VIDEO_CURTAIN_TRANSITION_CLASS, '[transform:translate3d(-120%,0,0)]')
    }
    if (curtainClassName === 'fortune-2d-curtain-middle-right') {
      return cn(PRINT_VIDEO_CURTAIN_TRANSITION_CLASS, '[transform:translate3d(120%,0,0)]')
    }
    if (curtainClassName.startsWith('fortune-2d-curtain-lower-')) {
      return cn(PRINT_VIDEO_CURTAIN_TRANSITION_CLASS, '[transform:translate3d(0,120%,0)]')
    }
    return ''
  }

  return (
    <div
      data-fortune-stage-root
      className={cn(
        // Root container: combines fortune-stage-visual + fortune-2d-visual.
        // tarot-background을 cover로 깔아서 stage가 viewport보다 작아져 빈 공간이
        // 생겨도 같은 배경 이미지로 자연스럽게 이어지도록 함 (검은 letterbox 방지).
        'absolute inset-0 z-[1] overflow-hidden bg-[#05010d]',
        "bg-[url('/images/fortune/stage/tarot-background.png')] bg-no-repeat bg-cover bg-center",
        '[filter:saturate(1.04)]',
      )}
    >
      <div
        className={cn(
          // .fortune-2d-stage
          '[--fortune-stage-aspect:1.4970684]',
          'absolute top-1/2 left-1/2 z-[1]',
          'w-[max(100vw,calc(100dvh*var(--fortune-stage-aspect)))]',
          'h-[max(100dvh,calc(100vw/var(--fortune-stage-aspect)))]',
          'min-w-[100vw] min-h-[100dvh] overflow-hidden',
          '[transform:translate3d(-50%,-50%,0)_scale(1.018)]',
          'animate-fortune-2d-stage-drift motion-reduce:animate-none',
          'pointer-events-none [transform-style:preserve-3d]',
          // Mobile portrait override — w/h는 기본 공식
          // (w: max(100vw, 100dvh*aspect) / h: max(100dvh, 100vw/aspect))을 그대로
          // 사용합니다. portrait에서도 100dvh*aspect > 100vw가 항상 성립하므로
          // h는 100dvh로 viewport 전체를 채우고 w는 100dvh*aspect로 가로 overflow가
          // 발생하지만 overflow-hidden으로 잘립니다. 이전엔 w-[220vw] + h-dvh로
          // 두 차원을 모두 강제해 aspect-ratio가 무시되고 stage가 정사각형에
          // 가까워지면서 캐릭터 본체와 팔의 위치가 어긋나는 문제가 있었습니다.
          'max-[767px]:portrait:top-[48%]',
        )}
        aria-hidden
      >
        <img
          className={cn(
            LAYER_BASE_CLASS,
            // .fortune-2d-background
            'inset-0 z-[1] w-full h-full object-cover',
            '[filter:saturate(1.08)_contrast(1.04)]',
            '[-webkit-user-drag:none]',
          )}
          src="/images/fortune/stage/tarot-background.png"
          alt=""
        />
        <img
          className={cn(
            LAYER_BASE_CLASS,
            // .fortune-2d-moon
            'top-[29.8%] left-[50.35%] z-[2] w-[52.2%]',
            '[transform:translate3d(-50%,-50%,0)_rotate(20deg)]',
            'opacity-[0.92]',
            'animate-fortune-2d-moon-drift motion-reduce:animate-none',
            '[-webkit-user-drag:none]',
          )}
          src="/images/fortune/stage/purple-moon.png"
          alt=""
        />
        <div
          className={cn(
            LAYER_BASE_CLASS,
            // .fortune-2d-nebula
            'top-[46%] left-1/2 z-[3] w-[58%] aspect-square',
            '[transform:translate3d(-50%,-50%,0)] rounded-full',
            '[background:radial-gradient(circle,rgba(218,169,255,0.2)_0_13%,rgba(138,62,255,0.12)_24%,rgba(70,23,149,0)_62%),conic-gradient(from_140deg,rgba(202,147,255,0),rgba(202,147,255,0.26),rgba(202,147,255,0))]',
            '[filter:blur(1.2rem)] [mix-blend-mode:screen] opacity-[0.78]',
          )}
        />
        <div
          className={cn(
            LAYER_BASE_CLASS,
            // .fortune-2d-character
            'top-[43.75%] left-1/2 z-[4] w-[73.85%] [aspect-ratio:1448/1086]',
            '[transform:translate3d(-50%,-50%,0)_rotate(-0.28deg)]',
            'animate-fortune-2d-popo-breathe motion-reduce:animate-none',
          )}
        >
          <img
            className={cn(
              // .fortune-2d-popo
              'absolute inset-0 w-full h-full max-w-none',
              '[filter:drop-shadow(0_1.8rem_3.2rem_rgba(0,0,0,0.42))_drop-shadow(0_0_1.8rem_rgba(139,64,255,0.34))]',
              '[-webkit-user-drag:none] select-none',
            )}
            src="/images/fortune/stage/wizard-popo.png"
            alt=""
          />
          <img
            className={cn(
              // .fortune-2d-eyes (base)
              'absolute top-[40.02%] left-[52.55%] z-[2] w-[10.36%] max-w-none',
              '[transform:translate3d(-50%,-50%,0)_rotate(-15.67deg)]',
              '[filter:drop-shadow(0_0_0.22rem_rgba(182,100,255,0.54))_drop-shadow(0_0_0.58rem_rgba(91,49,255,0.28))]',
              // .fortune-2d-eyes-open
              'animate-fortune-2d-eye-open motion-reduce:animate-none',
              '[-webkit-user-drag:none] select-none',
            )}
            src="/images/fortune/stage/eyes-open.png"
            alt=""
          />
          <img
            className={cn(
              // .fortune-2d-eyes (base)
              'absolute z-[2] max-w-none',
              '[transform:translate3d(-50%,-50%,0)_rotate(-15.67deg)]',
              '[filter:drop-shadow(0_0_0.22rem_rgba(182,100,255,0.54))_drop-shadow(0_0_0.58rem_rgba(91,49,255,0.28))]',
              // .fortune-2d-eyes-closed overrides
              'top-[40.18%] left-[52.43%] w-[10.78%] opacity-0',
              'animate-fortune-2d-eye-closed motion-reduce:animate-none',
              '[-webkit-user-drag:none] select-none',
            )}
            src="/images/fortune/stage/eyes-closed.png"
            alt=""
          />
        </div>
        <img
          className={cn(
            LAYER_BASE_CLASS,
            // .fortune-2d-table
            'top-[43.6%] left-[50.16%] z-[7] w-[100.3%]',
            '[transform:translate3d(-50%,-50%,0)]',
            '[filter:drop-shadow(0_-1.2rem_2.2rem_rgba(108,45,181,0.18))_drop-shadow(0_2rem_3.4rem_rgba(0,0,0,0.5))]',
            'motion-reduce:animate-none',
            '[-webkit-user-drag:none]',
          )}
          src="/images/fortune/stage/tarot-table-magic.png"
          alt=""
        />
        <img
          className={cn(
            LAYER_BASE_CLASS,
            // .fortune-2d-arm base
            'z-[8] w-[18.8%]',
            '[filter:drop-shadow(0_0.8rem_1.2rem_rgba(14,3,29,0.32))]',
            'animate-fortune-2d-arm-breathe motion-reduce:animate-none',
            '[transform-origin:62%_19%]',
            // .fortune-2d-arm-right
            'top-[67.2%] left-[43.4%]',
            '[transform:translate3d(-50%,-50%,0)_rotate(4deg)]',
            '[animation-duration:8.2s] [animation-delay:-1.35s]',
            '[-webkit-user-drag:none]',
          )}
          style={
            {
              '--fortune-arm-rest-rotate': '4deg',
              '--fortune-arm-swing-rotate': '-0.82deg',
              '--fortune-arm-settle-rotate': '0.24deg',
              '--fortune-arm-lift-y': '-0.42%',
              '--fortune-arm-settle-y': '-0.12%',
            } as CSSProperties
          }
          src="/images/fortune/stage/arm-left-table.png"
          alt=""
        />
        <img
          className={cn(
            LAYER_BASE_CLASS,
            // .fortune-2d-arm base
            'z-[8]',
            '[filter:drop-shadow(0_0.8rem_1.2rem_rgba(14,3,29,0.32))]',
            'animate-fortune-2d-arm-breathe motion-reduce:animate-none',
            '[transform-origin:39%_18%]',
            // .fortune-2d-arm-left overrides
            'top-[67%] left-[61%] w-[19.2%]',
            '[transform:translate3d(-50%,-50%,0)_rotate(-4deg)]',
            '[animation-duration:9.1s] [animation-delay:-4.2s]',
            '[-webkit-user-drag:none]',
          )}
          style={
            {
              '--fortune-arm-rest-rotate': '-4deg',
              '--fortune-arm-swing-rotate': '0.66deg',
              '--fortune-arm-settle-rotate': '-0.18deg',
              '--fortune-arm-lift-y': '-0.26%',
              '--fortune-arm-settle-y': '-0.08%',
            } as CSSProperties
          }
          src="/images/fortune/stage/arm-right-table.png"
          alt=""
        />
        <img
          className={cn(
            LAYER_BASE_CLASS,
            // .fortune-2d-cube — tighter asset, no built-in padding
            'top-[68%] left-1/2 z-[9] w-[13%]',
            '[transform:translate3d(-50%,-50%,0)]',
            '[filter:drop-shadow(0_0.65rem_1.1rem_rgba(13,3,27,0.3))]',
            '[transition:filter_240ms_ease,transform_240ms_ease]',
            'motion-reduce:animate-none',
            '[-webkit-user-drag:none]',
            cubeAnimationClass,
          )}
          src="/images/fortune/stage/magic-cube-box.png"
          alt=""
        />
        <div
          className={cn(
            LAYER_BASE_CLASS,
            // .fortune-2d-print-note
            'top-[66.8%] left-1/2 z-[10]',
            'flex items-center justify-center',
            'w-[min(16.4%,14rem)] [aspect-ratio:1.45] p-[0.82rem]',
            '[transform:translate3d(-50%,-58%,0)_rotateX(48deg)_scale(0.72)]',
            '[transform-origin:50%_0]',
            'border border-[rgba(126,76,177,0.24)] rounded-[0.38rem]',
            '[background:linear-gradient(180deg,rgba(255,255,255,0.94),rgba(255,247,211,0.92)),#fff6c9]',
            'text-[#5f3f83]',
            'font-[var(--font-fortune-serif)]',
            'text-[clamp(0.55rem,1.1vw,1rem)] leading-[1.16] text-center',
            'opacity-0',
            'shadow-[0_0.8rem_1.4rem_rgba(26,6,40,0.26),0_0_1.6rem_rgba(247,216,132,0.26)]',
            'motion-reduce:animate-none',
            printNoteAnimationClass,
            shouldShowPrintVideo && PRINT_VIDEO_HIDE_CLASS,
          )}
        >
          <span className="[display:-webkit-box] overflow-hidden [-webkit-box-orient:vertical] [-webkit-line-clamp:2]">
            {result?.postitLine ?? '오늘의 운세'}
          </span>
        </div>
        <div
          className={cn(
            LAYER_BASE_CLASS,
            // .fortune-2d-cube-flare
            'top-[67.4%] left-1/2 z-[6] w-[18.4%] [aspect-ratio:1.35]',
            '[transform:translate3d(-50%,-50%,0)] rounded-full',
            '[background:radial-gradient(ellipse,rgba(255,242,255,0.54),rgba(202,130,255,0.22)_34%,rgba(116,57,255,0)_70%),radial-gradient(ellipse,rgba(124,78,255,0.34),rgba(124,78,255,0)_68%)]',
            '[filter:blur(1.2rem)] [mix-blend-mode:screen] opacity-0',
            '[transition:opacity_220ms_ease]',
            'motion-reduce:animate-none',
            cubeFlareAnimationClass,
            shouldShowPrintVideo && PRINT_VIDEO_HIDE_CLASS,
          )}
        />
        <div
          className={cn(
            LAYER_BASE_CLASS,
            // .fortune-2d-vignette
            'inset-0 z-[12]',
            '[background:radial-gradient(ellipse_at_50%_45%,rgba(4,1,9,0)_0_42%,rgba(4,1,9,0.2)_70%,rgba(4,1,9,0.58)_100%),linear-gradient(180deg,rgba(4,1,9,0.2),rgba(4,1,9,0)_24%_68%,rgba(4,1,9,0.44))]',
          )}
        />
        <div
          className={cn(
            LAYER_BASE_CLASS,
            // .fortune-2d-sparkles container
            'inset-0 z-[13]',
            shouldShowPrintVideo && PRINT_VIDEO_HIDE_CLASS,
          )}
        >
          {FORTUNE_SPARKLE_POSITIONS.map((sparkle, index) => (
            <span
              key={index}
              className={cn(SPARKLE_BASE_CLASS, sparkle.extraClass)}
              style={sparkle.style}
            />
          ))}
        </div>
      </div>
      {/* Print video는 stage 박스가 viewport보다 작아질 수 있는 모바일에서도
          화면 전체를 덮어야 하므로 stage 밖, root 직속으로 빼서 viewport 전체를
          object-cover로 채우게 함. */}
      {shouldShowPrintVideo && (
        <video
          ref={printVideoRef}
          className={cn(
            'absolute inset-0 z-[11] block h-full w-full max-w-none select-none object-cover',
            'pointer-events-none bg-transparent opacity-0',
            '[transform:translateZ(0)] [will-change:opacity,transform] [contain:strict]',
            'animate-fortune-2d-print-video-fade',
          )}
          src={FORTUNE_PRINT_VIDEO_PATH}
          autoPlay
          muted
          playsInline
          preload="auto"
          controls={false}
          disablePictureInPicture
          onEnded={firePrintCompleteOnce}
          onError={firePrintCompleteOnce}
          aria-hidden
          onTimeUpdate={(event) => {
            if (event.currentTarget.currentTime >= FORTUNE_PRINT_SOUND_VIDEO_TIME_SECONDS) {
              firePrintStartOnce()
            }
          }}
        />
      )}
      <div
        ref={curtainFrameRef}
        className={cn(
          // .fortune-2d-curtain-frame
          'absolute inset-0 z-[3] overflow-hidden',
          shouldShowPrintVideo ? 'pointer-events-none' : 'pointer-events-auto',
        )}
        onPointerLeave={() => {
          setActiveCurtainClassName(null)
          setIsCubeHovered(false)
        }}
        aria-hidden
      >
        <span
          className={cn(
            // .fortune-2d-cube-hit-zone
            'absolute top-[58%] left-[40.2%] z-[16] block w-[20%] h-[22%] cursor-pointer',
            shouldShowPrintVideo ? 'pointer-events-none' : 'pointer-events-auto',
          )}
          onPointerEnter={() => setIsCubeHovered(true)}
          onPointerMove={() => setIsCubeHovered(true)}
          onPointerLeave={() => setIsCubeHovered(false)}
        />
        {FORTUNE_CURTAIN_HIT_ZONES.map((hitZone) => (
          <span
            key={hitZone.key}
            className={cn(
              // .fortune-2d-curtain-hit-zone base
              'absolute z-[8] block bg-transparent',
              shouldShowPrintVideo ? 'pointer-events-none' : 'pointer-events-auto',
              hitZone.className,
            )}
            data-fortune-curtain-hit-zone={hitZone.curtainClassName}
            onPointerDown={() => setActiveCurtainClassName(hitZone.curtainClassName)}
            onPointerEnter={() => setActiveCurtainClassName(hitZone.curtainClassName)}
            onPointerMove={() => setActiveCurtainClassName(hitZone.curtainClassName)}
            onPointerUp={() => setActiveCurtainClassName(null)}
            onPointerCancel={() => setActiveCurtainClassName(null)}
            onPointerLeave={() =>
              setActiveCurtainClassName((currentCurtainClassName) =>
                currentCurtainClassName === hitZone.curtainClassName ? null : currentCurtainClassName,
              )
            }
          />
        ))}
        {FORTUNE_CURTAIN_LAYERS.map((curtainLayer) => {
          const isActive = curtainLayer.className === effectiveActiveCurtainClassName
          const isSoftActive = isSameSideCurtainLayer(
            curtainLayer.className,
            effectiveActiveCurtainClassName,
            activeCurtainSide,
          )
          return (
            <img
              key={curtainLayer.className}
              className={cn(
                CURTAIN_PIECE_BASE_CLASS,
                curtainLayer.positionClassName,
                curtainLayer.hasLowerMask && CURTAIN_LOWER_MASK_CLASS,
                isActive && CURTAIN_ACTIVE_CLASS,
                isSoftActive && CURTAIN_SOFT_ACTIVE_CLASS,
                isSoftActive && CURTAIN_PIECE_REDUCED_MOTION_SOFT_CLASS,
                printVideoCurtainSlideClass(curtainLayer.className),
              )}
              style={curtainLayer.style}
              data-fortune-curtain-layer={curtainLayer.className}
              draggable={false}
              onDragStart={(event) => event.preventDefault()}
              src={curtainLayer.imageSrc}
              alt=""
            />
          )
        })}
      </div>
      <img
        className={cn(
          // .fortune-2d-screen-ornaments
          'absolute top-0 left-1/2 z-[5]',
          'w-[min(101vw,88rem)] h-[min(66dvh,50rem)] max-w-none object-fill',
          '[transform:translate3d(-50%,0,0)] opacity-[0.84]',
          '[filter:drop-shadow(0_0_0.42rem_rgba(190,99,255,0.28))_drop-shadow(0_0.9rem_1.2rem_rgba(0,0,0,0.34))]',
          'animate-fortune-2d-hanging-sway motion-reduce:animate-none',
          'pointer-events-none select-none',
          'max-[767px]:portrait:top-[0.6%] max-[767px]:portrait:w-[110vw] max-[767px]:portrait:h-[58dvh] max-[767px]:portrait:opacity-[0.74]',
          shouldShowPrintVideo &&
            '[animation:none] [transition:transform_2s_cubic-bezier(0.32,0.72,0.24,1),opacity_1.6s_ease-out] [transform:translate3d(-50%,-120%,0)] opacity-0',
        )}
        src="/images/fortune/stage/chain-ornaments.png"
        alt=""
        aria-hidden
      />
      {/* Vignette overlay (was .fortune-stage-visual::after) */}
      <div
        className={cn(
          'absolute inset-0 z-[2] pointer-events-none',
          '[background:radial-gradient(ellipse_at_50%_42%,rgba(8,2,18,0)_0_34%,rgba(8,2,18,0.16)_60%,rgba(4,1,10,0.46)_100%),linear-gradient(90deg,rgba(4,1,10,0.46),rgba(4,1,10,0.08)_27%_73%,rgba(4,1,10,0.46))]',
          'max-[800px]:[background:radial-gradient(ellipse_at_50%_37%,rgba(32,14,50,0)_0_29%,rgba(32,14,50,0.12)_55%,rgba(21,8,38,0.24)_100%),linear-gradient(90deg,rgba(24,10,44,0.22),rgba(24,10,44,0.03)_30%_70%,rgba(24,10,44,0.22))]',
        )}
        aria-hidden
      />
    </div>
  )
}

function getCurtainClassNameFromFramePosition(pointerXRatio: number, pointerYRatio: number) {
  if (pointerYRatio <= CURTAIN_FRAME_TOP_RATIO) {
    return pointerXRatio < 0.5 ? 'fortune-2d-curtain-top-left' : 'fortune-2d-curtain-top-right'
  }

  if (pointerXRatio <= CURTAIN_FRAME_SIDE_RATIO) {
    return pointerYRatio <= CURTAIN_FRAME_MIDDLE_RATIO
      ? 'fortune-2d-curtain-middle-left'
      : 'fortune-2d-curtain-lower-left'
  }

  if (pointerXRatio >= 1 - CURTAIN_FRAME_SIDE_RATIO) {
    return pointerYRatio <= CURTAIN_FRAME_MIDDLE_RATIO
      ? 'fortune-2d-curtain-middle-right'
      : 'fortune-2d-curtain-lower-right'
  }

  return null
}

function getIsPointerOverCube(pointerXRatio: number, pointerYRatio: number) {
  return (
    pointerXRatio >= CUBE_HOVER_LEFT_RATIO &&
    pointerXRatio <= CUBE_HOVER_LEFT_RATIO + CUBE_HOVER_WIDTH_RATIO &&
    pointerYRatio >= CUBE_HOVER_TOP_RATIO &&
    pointerYRatio <= CUBE_HOVER_TOP_RATIO + CUBE_HOVER_HEIGHT_RATIO
  )
}

function getCurtainSideFromClassName(curtainClassName: string | null) {
  if (curtainClassName?.endsWith('-left')) {
    return 'left'
  }

  if (curtainClassName?.endsWith('-right')) {
    return 'right'
  }

  return null
}

function isSameSideCurtainLayer(
  curtainClassName: string,
  activeCurtainClassName: string | null,
  activeCurtainSide: 'left' | 'right' | null,
) {
  return curtainClassName !== activeCurtainClassName && Boolean(activeCurtainSide && curtainClassName.endsWith(`-${activeCurtainSide}`))
}
