'use client'

import { Sparkles, Text } from '@react-three/drei'
import { Canvas, useFrame } from '@react-three/fiber'
import { Suspense, useEffect, useMemo, useRef } from 'react'
import { Object3D } from 'three'
import type { AmbientLight, DirectionalLight, PointLight, SpotLight } from 'three'

import { NemonicPrinterMesh } from '@/worlds/_shared/mesh'

import {
  preloadFortunePopoModel,
  useFortunePopoModel,
  useFortunePrinterMotion,
  useFortuneReducedMotion,
} from './hooks'
import type { FortuneResult } from './types'

interface FortuneVisualProps {
  isPrinting: boolean
  playEntrySpotlight?: boolean
  runEntrySpotlight?: boolean
  result: FortuneResult | null
  onEntrySceneReady?: () => void
  onPrintComplete: () => void
}

export default function FortuneVisual({
  isPrinting,
  playEntrySpotlight = false,
  runEntrySpotlight = false,
  result,
  onEntrySceneReady,
  onPrintComplete,
}: FortuneVisualProps) {
  const prefersReducedMotion = useFortuneReducedMotion()
  const stageClassName = playEntrySpotlight ? 'fortune-stage-visual fortune-stage-visual-entry' : 'fortune-stage-visual'

  return (
    <div className={stageClassName}>
      <div className="fortune-stage-aura" />
      <div className="fortune-stage-crown" />
      <div className="fortune-sparkle-field" aria-hidden>
        <span />
        <span />
        <span />
        <span />
        <span />
        <span />
      </div>
      <div className="fortune-curtain fortune-curtain-left" />
      <div className="fortune-curtain fortune-curtain-right" />
      <Canvas
        className="absolute inset-0 h-full w-full"
        camera={{ position: [0, 1.25, 6.4], fov: 34 }}
        gl={{ alpha: true }}
        shadows
        onCreated={({ camera }) => {
          camera.lookAt(0, 0.7, 0)
        }}
      >
        <Suspense fallback={null}>
          <FortunePrinterScene
            isPrinting={isPrinting}
            playEntrySpotlight={playEntrySpotlight}
            prefersReducedMotion={prefersReducedMotion}
            runEntrySpotlight={runEntrySpotlight}
            result={result}
            onSceneReady={playEntrySpotlight ? onEntrySceneReady : undefined}
            onPrintComplete={onPrintComplete}
          />
        </Suspense>
      </Canvas>
    </div>
  )
}

interface FortunePrinterSceneProps {
  isPrinting: boolean
  playEntrySpotlight: boolean
  prefersReducedMotion: boolean
  runEntrySpotlight: boolean
  result: FortuneResult | null
  onSceneReady?: () => void
  onPrintComplete: () => void
}

function FortunePrinterScene({
  isPrinting,
  playEntrySpotlight,
  prefersReducedMotion,
  runEntrySpotlight,
  result,
  onSceneReady,
  onPrintComplete,
}: FortunePrinterSceneProps) {
  const { paperGroupRef, printerGroupRef } = useFortunePrinterMotion({
    isPrinting,
    prefersReducedMotion,
    onPrintComplete,
  })

  useEffect(() => {
    onSceneReady?.()
  }, [onSceneReady])

  return (
    <>
      <FortuneStageLighting
        playEntrySpotlight={playEntrySpotlight}
        prefersReducedMotion={prefersReducedMotion}
        runEntrySpotlight={runEntrySpotlight}
      />
      <Sparkles count={54} scale={[5.2, 3.2, 1.6]} size={4.4} speed={0.26} opacity={0.62} color="#fff3b5" position={[0, 0.72, -0.85]} />
      <Sparkles count={22} scale={[3.2, 1.8, 0.8]} size={7.2} speed={0.14} opacity={0.32} color="#dfc6ff" position={[0, 0.36, 0.22]} />
      <group position={[0, FORTUNE_STAGE_CONTENT_Y_OFFSET, 0]}>
        <group position={[-1.62, 2.2, -1.36]} rotation={[0.18, -0.16, -0.42]}>
          <mesh>
            <torusGeometry args={[0.32, 0.035, 16, 72, Math.PI * 1.42]} />
            <meshStandardMaterial color="#fff4c8" emissive="#ffe88a" emissiveIntensity={0.58} roughness={0.46} />
          </mesh>
          <mesh position={[0.2, 0.06, 0]} rotation={[0, 0, Math.PI / 4]}>
            <octahedronGeometry args={[0.045, 0]} />
            <meshStandardMaterial color="#fff8d6" emissive="#ffe88a" emissiveIntensity={0.74} roughness={0.4} />
          </mesh>
        </group>
        <group position={[0, 1.78, -1.25]}>
          <mesh rotation={[0, 0, Math.PI / 4]}>
            <torusGeometry args={[1.55, 0.014, 12, 96, Math.PI]} />
            <meshStandardMaterial color="#ffe7a5" emissive="#ffd46a" emissiveIntensity={0.36} roughness={0.5} />
          </mesh>
        </group>
        <group position={[0, -1.08, -1.25]}>
          <FortunePopoMesh />
        </group>
        <group ref={printerGroupRef} position={[0, -0.55, 0.42]} rotation={[0, Math.PI, 0]} scale={0.78}>
          <NemonicPrinterMesh modelScale={16} withPhysics={false} />
        </group>
        <group ref={paperGroupRef} position={[0, -0.1, 0.43]} rotation={[-0.18, 0, 0]} visible={false}>
          <mesh castShadow>
            <boxGeometry args={[1.18, 0.04, 0.78]} />
            <meshStandardMaterial color="#fff9e8" roughness={0.72} />
          </mesh>
          <Text
            color="#5f4a78"
            fontSize={0.07}
            maxWidth={0.86}
            position={[0, 0.032, 0.01]}
            rotation={[-Math.PI / 2, 0, 0]}
            textAlign="center"
          >
            {result?.postitLine ?? '오늘의 운세'}
          </Text>
        </group>
      </group>
    </>
  )
}

const FORTUNE_STAGE_CONTENT_Y_OFFSET = 0.16
const ENTRY_SPOTLIGHT_DURATION = 1.72

interface FortuneStageLightingProps {
  playEntrySpotlight: boolean
  prefersReducedMotion: boolean
  runEntrySpotlight: boolean
}

function FortuneStageLighting({ playEntrySpotlight, prefersReducedMotion, runEntrySpotlight }: FortuneStageLightingProps) {
  const ambientLightRef = useRef<AmbientLight>(null)
  const directionalLightRef = useRef<DirectionalLight>(null)
  const warmFillLightRef = useRef<PointLight>(null)
  const lavenderFillLightRef = useRef<PointLight>(null)
  const rimLightRef = useRef<SpotLight>(null)
  const entrySpotlightRef = useRef<SpotLight>(null)
  const startedAtRef = useRef<number | null>(null)
  const hasSettledEntrySpotlightRef = useRef(false)
  const entryTarget = useMemo(() => new Object3D(), [])

  useEffect(() => {
    entryTarget.position.set(0, 0.56 + FORTUNE_STAGE_CONTENT_Y_OFFSET, -1.1)
    entryTarget.updateMatrixWorld()

    if (entrySpotlightRef.current) {
      entrySpotlightRef.current.target = entryTarget
      entrySpotlightRef.current.target.updateMatrixWorld()
    }
  }, [entryTarget])

  useEffect(() => {
    if (playEntrySpotlight && !runEntrySpotlight) {
      startedAtRef.current = null
      hasSettledEntrySpotlightRef.current = false
    }
  }, [playEntrySpotlight, runEntrySpotlight])

  useFrame(({ clock }) => {
    if (!playEntrySpotlight || prefersReducedMotion || !runEntrySpotlight || hasSettledEntrySpotlightRef.current) {
      return
    }

    const elapsed = clock.getElapsedTime()
    const progress = getEntrySpotlightProgress({
      elapsed,
      playEntrySpotlight,
      prefersReducedMotion,
      runEntrySpotlight,
      startedAtRef,
    })
    const stageReveal = smoothstep(0.54, 1, progress)
    const spotlightStrength = smoothstep(0.02, 0.18, progress) * Math.max(1 - smoothstep(0.76, 1, progress), 0.22)

    setLightIntensity(ambientLightRef.current, lerp(0.06, 0.82, stageReveal))
    setLightIntensity(directionalLightRef.current, lerp(0.08, 2.3, stageReveal))
    setLightIntensity(warmFillLightRef.current, lerp(0, 1.6, stageReveal))
    setLightIntensity(lavenderFillLightRef.current, lerp(0, 1.1, stageReveal))
    setLightIntensity(rimLightRef.current, lerp(0, 1.55, stageReveal))

    const entrySpotlight = entrySpotlightRef.current

    if (entrySpotlight) {
      entrySpotlight.intensity = 38 * spotlightStrength
      entrySpotlight.target.updateMatrixWorld()
    }

    if (progress >= 1) {
      hasSettledEntrySpotlightRef.current = true
    }
  })

  return (
    <>
      <ambientLight ref={ambientLightRef} intensity={playEntrySpotlight && !prefersReducedMotion ? 0.06 : 0.82} />
      <directionalLight ref={directionalLightRef} castShadow position={[3.2, 5.8, 4.4]} intensity={playEntrySpotlight && !prefersReducedMotion ? 0.08 : 2.3} />
      <pointLight ref={warmFillLightRef} position={[0, 2.1, 1.4]} intensity={playEntrySpotlight && !prefersReducedMotion ? 0 : 1.6} color="#fff2b8" distance={5.6} />
      <pointLight ref={lavenderFillLightRef} position={[-1.7, 1.2, 0.4]} intensity={playEntrySpotlight && !prefersReducedMotion ? 0 : 1.1} color="#f1c4ff" distance={4.2} />
      <spotLight ref={rimLightRef} position={[-3.4, 4.2, 2.6]} intensity={playEntrySpotlight && !prefersReducedMotion ? 0 : 1.55} angle={0.56} penumbra={0.55} />
      <primitive object={entryTarget} />
      <spotLight
        ref={entrySpotlightRef}
        color="#fff0b8"
        intensity={0}
        position={[0, 3.25, 5.45]}
        angle={0.29}
        penumbra={0.78}
        distance={8}
        decay={1.15}
      />
    </>
  )
}

interface GetEntrySpotlightProgressParams {
  elapsed: number
  playEntrySpotlight: boolean
  prefersReducedMotion: boolean
  runEntrySpotlight: boolean
  startedAtRef: React.MutableRefObject<number | null>
}

function getEntrySpotlightProgress({
  elapsed,
  playEntrySpotlight,
  prefersReducedMotion,
  runEntrySpotlight,
  startedAtRef,
}: GetEntrySpotlightProgressParams) {
  if (!playEntrySpotlight || prefersReducedMotion) {
    startedAtRef.current = null
    return 1
  }

  if (!runEntrySpotlight) {
    startedAtRef.current = null
    return 0
  }

  startedAtRef.current ??= elapsed

  return clamp01((elapsed - startedAtRef.current) / ENTRY_SPOTLIGHT_DURATION)
}

function setLightIntensity(light: { intensity: number } | null, intensity: number) {
  if (light) {
    light.intensity = intensity
  }
}

function lerp(start: number, end: number, amount: number) {
  return start + (end - start) * amount
}

function clamp01(value: number) {
  return Math.min(Math.max(value, 0), 1)
}

function smoothstep(edge0: number, edge1: number, value: number) {
  const amount = clamp01((value - edge0) / (edge1 - edge0))

  return amount * amount * (3 - 2 * amount)
}

function FortunePopoMesh() {
  const popoModel = useFortunePopoModel()

  return <primitive object={popoModel} dispose={null} />
}

preloadFortunePopoModel()
