'use client'

/* eslint-disable @next/next/no-img-element */

import { useEffect, useRef, useState } from 'react'
import { useShallow } from 'zustand/react/shallow'

import { cn } from '@/shared/libs'

import {
  FORTUNE_PRINT_FALLBACK_TIMEOUT_SECONDS,
  FORTUNE_PRINT_VIDEO_PATH,
  FORTUNE_REDUCED_MOTION_DURATION_SECONDS,
} from './constants'
import { useFortuneSessionStore } from './fortuneSessionStore'
import { useFortuneReducedMotion } from './hooks'

interface FortuneVisualProps {
  playEntrySpotlight?: boolean
  runEntrySpotlight?: boolean
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
  {
    className: 'fortune-2d-curtain-hit-zone-lower-left',
    curtainClassName: 'fortune-2d-curtain-lower-left',
  },
  {
    className: 'fortune-2d-curtain-hit-zone-lower-right',
    curtainClassName: 'fortune-2d-curtain-lower-right',
  },
  {
    className: 'fortune-2d-curtain-hit-zone-middle-left',
    curtainClassName: 'fortune-2d-curtain-middle-left',
  },
  {
    className: 'fortune-2d-curtain-hit-zone-middle-right',
    curtainClassName: 'fortune-2d-curtain-middle-right',
  },
  {
    className: 'fortune-2d-curtain-hit-zone-top-left',
    curtainClassName: 'fortune-2d-curtain-top-left',
  },
  {
    className: 'fortune-2d-curtain-hit-zone-top-right',
    curtainClassName: 'fortune-2d-curtain-top-right',
  },
] as const

const CURTAIN_FRAME_TOP_RATIO = 0.24
const CURTAIN_FRAME_SIDE_RATIO = 0.34
const CURTAIN_FRAME_MIDDLE_RATIO = 0.56
const CUBE_HOVER_LEFT_RATIO = 0.402
const CUBE_HOVER_TOP_RATIO = 0.58
const CUBE_HOVER_WIDTH_RATIO = 0.2
const CUBE_HOVER_HEIGHT_RATIO = 0.22

export default function FortuneVisual({
  playEntrySpotlight = false,
  runEntrySpotlight = false,
  onEntrySceneReady,
  onPrintComplete,
}: FortuneVisualProps) {
  const { isPrinting, result } = useFortuneSessionStore(
    useShallow((state) => ({
      isPrinting: state.step === 'printing',
      result: state.result,
    })),
  )
  const prefersReducedMotion = useFortuneReducedMotion()
  const curtainFrameRef = useRef<HTMLDivElement>(null)
  const printVideoRef = useRef<HTMLVideoElement>(null)
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

    printCompleteFiredRef.current = false

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
  }, [isPrinting, onPrintComplete, prefersReducedMotion])

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

  return (
    <div
      className={cn(
        'fortune-stage-visual fortune-2d-visual',
        playEntrySpotlight && 'fortune-stage-visual-entry',
        runEntrySpotlight && 'fortune-2d-entry-ready',
        isPrinting && 'fortune-2d-printing',
        shouldShowPrintVideo && 'fortune-2d-print-video-active',
        effectiveIsCubeHovered && 'fortune-2d-cube-hovered',
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
        {shouldShowPrintVideo && (
          <video
            ref={printVideoRef}
            className="fortune-2d-layer fortune-2d-print-video"
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
          />
        )}
      </div>
      <div
        ref={curtainFrameRef}
        className="fortune-2d-curtain-frame"
        onPointerLeave={() => {
          setActiveCurtainClassName(null)
          setIsCubeHovered(false)
        }}
        aria-hidden
      >
        <span
          className="fortune-2d-cube-hit-zone"
          onPointerEnter={() => setIsCubeHovered(true)}
          onPointerMove={() => setIsCubeHovered(true)}
          onPointerLeave={() => setIsCubeHovered(false)}
        />
        {FORTUNE_CURTAIN_HIT_ZONES.map((hitZone) => (
          <span
            key={hitZone.className}
            className={cn('fortune-2d-curtain-hit-zone', hitZone.className)}
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
        {FORTUNE_CURTAIN_LAYERS.map((curtainLayer) => (
          <img
            key={curtainLayer.className}
            className={cn(
              'fortune-2d-curtain-piece',
              curtainLayer.className,
              curtainLayer.className === effectiveActiveCurtainClassName && 'is-active',
              isSameSideCurtainLayer(curtainLayer.className, effectiveActiveCurtainClassName, activeCurtainSide) && 'is-soft-active',
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
