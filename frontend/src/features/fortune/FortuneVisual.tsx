'use client'

/* eslint-disable @next/next/no-img-element */

import { useEffect, useRef, useState } from 'react'

import { cn } from '@/shared/libs'

import {
  FORTUNE_PRINT_DURATION_SECONDS,
  FORTUNE_REDUCED_MOTION_DURATION_SECONDS,
} from './constants'
import { useFortuneReducedMotion } from './hooks'
import type { FortuneResult } from './types'

interface FortuneVisualProps {
  isPrinting: boolean
  playEntrySpotlight?: boolean
  runEntrySpotlight?: boolean
  result: FortuneResult | null
  onEntrySceneReady?: () => void
  onPrintComplete: () => void
}

const FORTUNE_CURTAIN_LAYERS = [
  {
    className: 'fortune-2d-curtain-lower-left',
    imageSrc: '/images/fortune/stage/curtain-lower-left-tight.png',
  },
  {
    className: 'fortune-2d-curtain-lower-right',
    imageSrc: '/images/fortune/stage/curtain-lower-right-tight.png',
  },
  {
    className: 'fortune-2d-curtain-middle-left',
    imageSrc: '/images/fortune/stage/curtain-middle-left-tight.png',
  },
  {
    className: 'fortune-2d-curtain-middle-right',
    imageSrc: '/images/fortune/stage/curtain-middle-right-tight.png',
  },
  {
    className: 'fortune-2d-curtain-top-left',
    imageSrc: '/images/fortune/stage/curtain-top-left-tight.png',
  },
  {
    className: 'fortune-2d-curtain-top-right',
    imageSrc: '/images/fortune/stage/curtain-top-right-tight.png',
  },
] as const

const FORTUNE_CURTAIN_HIT_ZONES = [
  'fortune-2d-curtain-hit-zone-left',
  'fortune-2d-curtain-hit-zone-right',
  'fortune-2d-curtain-hit-zone-top-left',
  'fortune-2d-curtain-hit-zone-top-right',
] as const

const CURTAIN_FRAME_TOP_RATIO = 0.24
const CURTAIN_FRAME_SIDE_RATIO = 0.34
const CURTAIN_FRAME_MIDDLE_RATIO = 0.56

export default function FortuneVisual({
  isPrinting,
  playEntrySpotlight = false,
  runEntrySpotlight = false,
  result,
  onEntrySceneReady,
  onPrintComplete,
}: FortuneVisualProps) {
  const prefersReducedMotion = useFortuneReducedMotion()
  const curtainFrameRef = useRef<HTMLDivElement>(null)
  const [activeCurtainClassName, setActiveCurtainClassName] = useState<string | null>(null)

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
      return
    }

    const printDuration = prefersReducedMotion
      ? FORTUNE_REDUCED_MOTION_DURATION_SECONDS
      : FORTUNE_PRINT_DURATION_SECONDS
    const timerId = window.setTimeout(onPrintComplete, printDuration * 1000)

    return () => {
      window.clearTimeout(timerId)
    }
  }, [isPrinting, onPrintComplete, prefersReducedMotion])

  useEffect(() => {
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
        return
      }

      const pointerXRatio = (event.clientX - frameBounds.left) / frameBounds.width
      const pointerYRatio = (event.clientY - frameBounds.top) / frameBounds.height
      const curtainClassName = getCurtainClassNameFromFramePosition(pointerXRatio, pointerYRatio)

      setActiveCurtainClassName((currentClassName) =>
        currentClassName === curtainClassName ? currentClassName : curtainClassName,
      )
    }

    const handleWindowCurtainLeave = () => {
      setActiveCurtainClassName(null)
    }

    const handleWindowMouseOut = (event: MouseEvent) => {
      if (event.relatedTarget === null) {
        setActiveCurtainClassName(null)
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
  }, [])

  const activeCurtainSide = getCurtainSideFromClassName(activeCurtainClassName)

  return (
    <div
      className={cn(
        'fortune-stage-visual fortune-2d-visual',
        playEntrySpotlight && 'fortune-stage-visual-entry',
        runEntrySpotlight && 'fortune-2d-entry-ready',
        isPrinting && 'fortune-2d-printing',
      )}
    >
      <div className="fortune-2d-stage" aria-hidden>
        <img className="fortune-2d-layer fortune-2d-background" src="/images/fortune/stage/tarot-background.png" alt="" />
        <img className="fortune-2d-layer fortune-2d-moon" src="/images/fortune/stage/purple-moon.png" alt="" />
        <div className="fortune-2d-layer fortune-2d-nebula" />
        <div className="fortune-2d-layer fortune-2d-character">
          <img className="fortune-2d-popo" src="/images/fortune/stage/wizard-popo.png" alt="" />
          <img className="fortune-2d-eyes fortune-2d-eyes-open" src="/images/fortune/stage/eyes-open.png" alt="" />
          <img className="fortune-2d-eyes fortune-2d-eyes-closed" src="/images/fortune/stage/eyes-closed.png" alt="" />
        </div>
        <img className="fortune-2d-layer fortune-2d-table" src="/images/fortune/stage/tarot-table-magic.png" alt="" />
        <img className="fortune-2d-layer fortune-2d-arm fortune-2d-arm-right" src="/images/fortune/stage/arm-left-table.png" alt="" />
        <img className="fortune-2d-layer fortune-2d-arm fortune-2d-arm-left" src="/images/fortune/stage/arm-right-table.png" alt="" />
        <img className="fortune-2d-layer fortune-2d-cube" src="/images/fortune/stage/magic-cube-box.png" alt="" />
        <div className="fortune-2d-layer fortune-2d-print-note">
          <span>{result?.postitLine ?? '오늘의 운세'}</span>
        </div>
        <div className="fortune-2d-layer fortune-2d-cube-flare" />
        <div className="fortune-2d-layer fortune-2d-vignette" />
        <div className="fortune-2d-layer fortune-2d-sparkles">
          <span />
          <span />
          <span />
          <span />
          <span />
          <span />
          <span />
          <span />
        </div>
      </div>
      <div
        ref={curtainFrameRef}
        className={cn(
          'fortune-2d-curtain-frame',
          activeCurtainSide === 'left' && 'is-left-active',
          activeCurtainSide === 'right' && 'is-right-active',
        )}
        aria-hidden
      >
        {FORTUNE_CURTAIN_HIT_ZONES.map((hitZoneClassName) => (
          <span key={hitZoneClassName} className={cn('fortune-2d-curtain-hit-zone', hitZoneClassName)} />
        ))}
        {FORTUNE_CURTAIN_LAYERS.map((curtainLayer) => (
          <img
            key={curtainLayer.className}
            className={cn(
              'fortune-2d-curtain-piece',
              curtainLayer.className,
              isCurtainLayerActive(curtainLayer.className, activeCurtainClassName, activeCurtainSide) && 'is-active',
            )}
            data-fortune-curtain-layer={curtainLayer.className}
            draggable={false}
            onDragStart={(event) => event.preventDefault()}
            src={curtainLayer.imageSrc}
            alt=""
          />
        ))}
      </div>
      <img className="fortune-2d-screen-ornaments" src="/images/fortune/stage/chain-ornaments.png" alt="" aria-hidden />
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

function getCurtainSideFromClassName(curtainClassName: string | null) {
  if (curtainClassName?.endsWith('-left')) {
    return 'left'
  }

  if (curtainClassName?.endsWith('-right')) {
    return 'right'
  }

  return null
}

function isCurtainLayerActive(
  curtainClassName: string,
  activeCurtainClassName: string | null,
  activeCurtainSide: 'left' | 'right' | null,
) {
  return curtainClassName === activeCurtainClassName || Boolean(activeCurtainSide && curtainClassName.endsWith(`-${activeCurtainSide}`))
}
