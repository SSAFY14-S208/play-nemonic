import { Text, useTexture } from '@react-three/drei'
import type { ThreeEvent } from '@react-three/fiber'
import { useFrame, useThree } from '@react-three/fiber'
import {
  Suspense,
  type MutableRefObject,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react'
import * as THREE from 'three'
import {
  HUB_GAMES,
  HUB_MONITOR_SCREEN_POSITION,
  HUB_MONITOR_SCREEN_SIZE,
} from '@/shared/constants'
import {
  useHubMonitorTransitionStore,
  useHubOnboardingStore,
  useHubRoomStore,
} from '@/shared/stores'
import type { HubGameId } from '@/shared/types'
import { trackHubInvalidate } from '@/shared/utils'
import type { MonitorEntranceProgressRef, MonitorGameAction } from './hooks'
import {
  useMonitorEntranceSequence,
  useMonitorGameSelector,
  useMonitorLayerEntranceAnimation,
  useMonitorLogoAnimation,
  useMonitorStartButtonAnimation,
} from './hooks'

const MONITOR_FONT_URL = '/fonts/Paperlogy-6SemiBold.woff'
const MONITOR_ASSET_BASE_URL = '/images/hub-monitor'
const MONITOR_BACKGROUND_LAYER_Z = 0.012
const MONITOR_LOGO_LAYER_Z = 0.022
const MONITOR_RIBBON_LAYER_Z = 0.024
const MONITOR_START_BUTTON_LAYER_Z = 0.026
const MONITOR_HOTSPOT_LAYER_Z = 0.068
const MONITOR_START_BUTTON_POSITION: [number, number, number] = [
  0,
  -0.43,
  MONITOR_START_BUTTON_LAYER_Z,
]
const MONITOR_START_BUTTON_HOTSPOT_POSITION: [number, number, number] = [
  0,
  -0.43,
  MONITOR_HOTSPOT_LAYER_Z,
]
const MONITOR_START_BUTTON_SIZE: [number, number] = [1.1, 0.36]
const MONITOR_START_BUTTON_GLOW_SIZE: [number, number] = [1.2, 0.46]
const MONITOR_SIDE_HOTSPOT_SIZE: [number, number] = [0.36, 1.08]
const MONITOR_NAV_ARROW_FONT_SIZE = 0.34
const MONITOR_NAV_ARROW_OUTLINE_WIDTH = 0.01
const MONITOR_NAV_ARROW_BOB_DISTANCE = 0.052
const MONITOR_NAV_ARROW_BOB_SPEED = 3.4
const MONITOR_NAV_ARROW_PULSE_SCALE = 0.045
const MONITOR_TEXTURE_IDLE_TIMEOUT_MS = 1200
const MONITOR_TRANSITION_FEEDBACK_LAYER_Z = MONITOR_HOTSPOT_LAYER_Z - 0.008
const DISABLED_RAYCAST: THREE.Mesh['raycast'] = () => undefined
const configuredMonitorTextures = new WeakSet<THREE.Texture>()

interface MonitorScreenAsset {
  background: string
  logo: string
  logoPosition: [number, number, number]
  logoSize: [number, number]
  ribbon?: string
  ribbonPosition?: [number, number, number]
  ribbonSize?: [number, number]
  startButton: string
}

const MONITOR_SCREEN_ASSETS: Record<HubGameId, MonitorScreenAsset> = {
  'fortune-memo': {
    background: `${MONITOR_ASSET_BASE_URL}/fortune-bg.webp`,
    logo: `${MONITOR_ASSET_BASE_URL}/fortune-logo.webp`,
    logoPosition: [-0.65, 0.14, MONITOR_LOGO_LAYER_Z],
    logoSize: [0.86, 0.72],
    startButton: `${MONITOR_ASSET_BASE_URL}/fortune-start.webp`,
  },
  flipbook: {
    background: `${MONITOR_ASSET_BASE_URL}/flipbook-bg.webp`,
    logo: `${MONITOR_ASSET_BASE_URL}/flipbook-logo.webp`,
    logoPosition: [0, 0.16, MONITOR_LOGO_LAYER_Z],
    logoSize: [1.12, 0.64],
    ribbon: `${MONITOR_ASSET_BASE_URL}/flipbook-ribbon.webp`,
    ribbonPosition: [0, 0.55, MONITOR_RIBBON_LAYER_Z],
    ribbonSize: [1.16, 0.25],
    startButton: `${MONITOR_ASSET_BASE_URL}/flipbook-start.webp`,
  },
  'relay-drawing': {
    background: `${MONITOR_ASSET_BASE_URL}/relay-bg.webp`,
    logo: `${MONITOR_ASSET_BASE_URL}/relay-logo.webp`,
    logoPosition: [0, 0.18, MONITOR_LOGO_LAYER_Z],
    logoSize: [1.36, 0.78],
    startButton: `${MONITOR_ASSET_BASE_URL}/relay-start.webp`,
  },
  'infinite-canvas': {
    background: `${MONITOR_ASSET_BASE_URL}/infinite-bg.webp`,
    logo: `${MONITOR_ASSET_BASE_URL}/infinite-logo.webp`,
    logoPosition: [0, 0.15, MONITOR_LOGO_LAYER_Z],
    logoSize: [1.25, 0.86],
    startButton: `${MONITOR_ASSET_BASE_URL}/infinite-start.webp`,
  },
}

type MonitorGameSelectorScale = number | [number, number, number]

export interface MonitorGameTransitionControls {
  requestNextGame: () => void
  requestPreviousGame: () => void
}

interface MonitorGameSelectorProps {
  enableInternalHitboxes?: boolean
  position?: [number, number, number]
  quaternion?: [number, number, number, number]
  scale?: MonitorGameSelectorScale
  transitionControlsRef?: MutableRefObject<MonitorGameTransitionControls | null>
}

interface PendingMonitorGameRequest {
  gameIndex: number
  requestId: number
}

function configureMonitorTexture(texture: THREE.Texture) {
  if (configuredMonitorTextures.has(texture)) return

  texture.colorSpace = THREE.SRGBColorSpace
  texture.anisotropy = 4
  texture.minFilter = THREE.LinearFilter
  texture.magFilter = THREE.LinearFilter
  texture.generateMipmaps = false
  texture.needsUpdate = true

  configuredMonitorTextures.add(texture)
}

function configureMonitorTextureList(
  textureInput: THREE.Texture | THREE.Texture[],
) {
  const textureList = Array.isArray(textureInput)
    ? textureInput
    : [textureInput]

  textureList.forEach(configureMonitorTexture)
}

function getMonitorAssetTextureUrls(asset: MonitorScreenAsset) {
  return asset.ribbon
    ? [asset.background, asset.logo, asset.startButton, asset.ribbon]
    : [asset.background, asset.logo, asset.startButton]
}

const MONITOR_TEXTURE_URLS = Array.from(
  new Set(
    Object.values(MONITOR_SCREEN_ASSETS).flatMap(getMonitorAssetTextureUrls),
  ),
)

function getWrappedGameIndex(gameIndex: number) {
  return ((gameIndex % HUB_GAMES.length) + HUB_GAMES.length) % HUB_GAMES.length
}

function getMonitorAssetByGameIndex(gameIndex: number) {
  const game = HUB_GAMES[getWrappedGameIndex(gameIndex)] ?? HUB_GAMES[0]

  return MONITOR_SCREEN_ASSETS[game.id]
}

function preloadMonitorTextures(textureUrls: string[]) {
  if (textureUrls.length === 0) return

  useTexture.preload(textureUrls)
}

function scheduleMonitorTextureIdleTask(callback: () => void) {
  if (typeof window === 'undefined') return () => undefined

  if (window.requestIdleCallback && window.cancelIdleCallback) {
    const idleTaskHandle = window.requestIdleCallback(callback, {
      timeout: MONITOR_TEXTURE_IDLE_TIMEOUT_MS,
    })

    return () => {
      window.cancelIdleCallback(idleTaskHandle)
    }
  }

  const timeoutHandle = window.setTimeout(callback, 300)

  return () => {
    window.clearTimeout(timeoutHandle)
  }
}

function useMonitorTextureWarmupTrigger({
  hasEnteredHub,
  isMonitorFocused,
}: {
  hasEnteredHub: boolean
  isMonitorFocused: boolean
}) {
  const [
    shouldWarmAllMonitorTextures,
    setShouldWarmAllMonitorTextures,
  ] = useState(false)
  const hasRequestedWarmupRef = useRef(false)

  useEffect(() => {
    let cancelled = false

    if (hasRequestedWarmupRef.current) return
    if (!hasEnteredHub && !isMonitorFocused) return

    const startWarmup = () => {
      if (hasRequestedWarmupRef.current) return

      hasRequestedWarmupRef.current = true
      preloadMonitorTextures(MONITOR_TEXTURE_URLS)

      void (async () => {
        await Promise.resolve()

        if (cancelled) return

        setShouldWarmAllMonitorTextures(true)
      })()
    }

    if (isMonitorFocused) {
      startWarmup()

      return () => {
        cancelled = true
      }
    }

    const cancelIdleTask = scheduleMonitorTextureIdleTask(startWarmup)

    return () => {
      cancelled = true
      cancelIdleTask()
    }
  }, [hasEnteredHub, isMonitorFocused])

  return shouldWarmAllMonitorTextures
}

function setDocumentCursor(cursor: string) {
  if (typeof document === 'undefined') return

  document.body.style.cursor = cursor
}

function isMonitorKeyboardInputTarget(target: EventTarget | null) {
  if (!(target instanceof HTMLElement)) return false

  const tagName = target.tagName.toLowerCase()

  return (
    target.isContentEditable ||
    tagName === 'input' ||
    tagName === 'textarea' ||
    tagName === 'select'
  )
}

function MonitorTextureWarmupContent() {
  const gl = useThree((state) => state.gl)
  const invalidate = useThree((state) => state.invalidate)
  const textureList = useTexture(
    MONITOR_TEXTURE_URLS,
    configureMonitorTextureList,
  ) as THREE.Texture[]

  useEffect(() => {
    textureList.forEach((texture) => {
      configureMonitorTexture(texture)
      gl.initTexture(texture)
    })
    invalidate()
  }, [gl, invalidate, textureList])

  return null
}

function MonitorTextureWarmup({ isEnabled }: { isEnabled: boolean }) {
  return isEnabled ? <MonitorTextureWarmupContent /> : null
}

function PendingMonitorTextureCommit({
  gameIndex,
  onReady,
  requestId,
}: {
  gameIndex: number
  onReady: (gameIndex: number, requestId: number) => void
  requestId: number
}) {
  const gl = useThree((state) => state.gl)
  const invalidate = useThree((state) => state.invalidate)
  const asset = getMonitorAssetByGameIndex(gameIndex)
  const pendingTextureUrls = useMemo(() => {
    return getMonitorAssetTextureUrls(asset)
  }, [asset])
  const textureList = useTexture(
    pendingTextureUrls,
    configureMonitorTextureList,
  ) as THREE.Texture[]

  useEffect(() => {
    let cancelled = false

    ;(async () => {
      await Promise.resolve()

      if (cancelled) return

      textureList.forEach((texture) => {
        configureMonitorTexture(texture)
        gl.initTexture(texture)
      })
      invalidate()

      if (cancelled) return

      onReady(gameIndex, requestId)
    })()

    return () => {
      cancelled = true
    }
  }, [gameIndex, gl, invalidate, onReady, requestId, textureList])

  return null
}

function MonitorTransitionFeedback({
  glowColor,
  isActive,
}: {
  glowColor: string
  isActive: boolean
}) {
  const elapsedSecondsRef = useRef(0)
  const materialRef = useRef<THREE.MeshBasicMaterial>(null)
  const invalidate = useThree((state) => state.invalidate)

  useEffect(() => {
    elapsedSecondsRef.current = 0

    const material = materialRef.current
    if (material) {
      material.color.set(glowColor)
      material.opacity = isActive ? 0.1 : 0
      material.needsUpdate = true
    }

    if (isActive) {
      invalidate()
    }
  }, [glowColor, invalidate, isActive])

  useFrame((_, delta) => {
    const material = materialRef.current
    if (!material) return

    if (!isActive && material.opacity <= 0.001) return

    elapsedSecondsRef.current += delta

    const targetOpacity = isActive
      ? 0.08 + ((Math.sin(elapsedSecondsRef.current * 12) + 1) / 2) * 0.08
      : 0

    material.opacity = THREE.MathUtils.damp(
      material.opacity,
      targetOpacity,
      18,
      delta,
    )
    material.needsUpdate = true

    invalidate()
  })

  return (
    <mesh
      position={[0, 0, MONITOR_TRANSITION_FEEDBACK_LAYER_Z]}
      raycast={DISABLED_RAYCAST}
      renderOrder={MONITOR_TRANSITION_FEEDBACK_LAYER_Z * 1000}
    >
      <planeGeometry args={HUB_MONITOR_SCREEN_SIZE} />
      <meshBasicMaterial
        ref={materialRef}
        blending={THREE.AdditiveBlending}
        color={glowColor}
        depthWrite={false}
        opacity={0}
        side={THREE.FrontSide}
        toneMapped={false}
        transparent
      />
    </mesh>
  )
}

function MonitorTexturePlane({
  position,
  size,
  texture,
}: {
  position: [number, number, number]
  size: [number, number]
  texture: THREE.Texture
}) {
  return (
    <mesh
      position={position}
      raycast={DISABLED_RAYCAST}
      renderOrder={position[2] * 1000}
    >
      <planeGeometry args={size} />
      <meshBasicMaterial
        alphaTest={0.02}
        map={texture}
        side={THREE.FrontSide}
        toneMapped={false}
        transparent
      />
    </mesh>
  )
}

function AnimatedTexturePlane({
  entranceProgressRef,
  position,
  size,
  texture,
}: {
  entranceProgressRef?: MonitorEntranceProgressRef
  position: [number, number, number]
  size: [number, number]
  texture: THREE.Texture
}) {
  const { layerGroupRef, materialRef } = useMonitorLayerEntranceAnimation({
    entranceProgressRef,
    position,
  })

  return (
    <group
      ref={layerGroupRef}
      position={position}
      renderOrder={position[2] * 1000}
    >
      <mesh raycast={DISABLED_RAYCAST}>
        <planeGeometry args={size} />
        <meshBasicMaterial
          ref={materialRef}
          alphaTest={0.02}
          map={texture}
          opacity={1}
          side={THREE.FrontSide}
          toneMapped={false}
          transparent
        />
      </mesh>
    </group>
  )
}

function AnimatedLogo({
  entranceProgressRef,
  position,
  size,
  texture,
}: {
  entranceProgressRef?: MonitorEntranceProgressRef
  position: [number, number, number]
  size: [number, number]
  texture: THREE.Texture
}) {
  const { logoGroupRef, logoMaterialRef } = useMonitorLogoAnimation({
    entranceProgressRef,
    position,
  })

  return (
    <group
      ref={logoGroupRef}
      position={position}
      renderOrder={position[2] * 1000}
    >
      <mesh raycast={DISABLED_RAYCAST}>
        <planeGeometry args={size} />
        <meshBasicMaterial
          ref={logoMaterialRef}
          alphaTest={0.02}
          map={texture}
          opacity={1}
          side={THREE.FrontSide}
          toneMapped={false}
          transparent
        />
      </mesh>
    </group>
  )
}

function MonitorHotspot({
  action,
  enablePointerEvents,
  isArrowAnimated,
  label,
  onClick,
  position,
  size,
  symbol,
}: {
  action: MonitorGameAction
  enablePointerEvents: boolean
  isArrowAnimated: boolean
  label: string
  onClick: () => void
  position: [number, number, number]
  size: [number, number]
  symbol?: string
}) {
  const arrowElapsedTimeRef = useRef(0)
  const arrowGroupRef = useRef<THREE.Group>(null)
  const materialRef = useRef<THREE.MeshBasicMaterial>(null)
  const invalidate = useThree((state) => state.invalidate)
  const arrowDirection = action === 'previous' ? -1 : action === 'next' ? 1 : 0

  useEffect(() => {
    if (isArrowAnimated) {
      invalidate()
      return
    }

    const arrowGroup = arrowGroupRef.current
    if (!arrowGroup) return

    arrowElapsedTimeRef.current = 0
    arrowGroup.position.x = 0
    arrowGroup.scale.setScalar(1)
    invalidate()
  }, [invalidate, isArrowAnimated])

  useFrame((_, delta) => {
    const arrowGroup = arrowGroupRef.current

    if (!symbol || !arrowGroup || arrowDirection === 0) return
    if (!isArrowAnimated) return

    arrowElapsedTimeRef.current += delta

    const wave = Math.sin(
      arrowElapsedTimeRef.current * MONITOR_NAV_ARROW_BOB_SPEED,
    )
    const scale = 1 + ((wave + 1) / 2) * MONITOR_NAV_ARROW_PULSE_SCALE

    arrowGroup.position.x = arrowDirection * wave * MONITOR_NAV_ARROW_BOB_DISTANCE
    arrowGroup.scale.setScalar(scale)
    invalidate()
  })

  const setHovered = (isHovered: boolean) => {
    const material = materialRef.current

    if (material) {
      material.opacity = isHovered ? 0.22 : 0
      material.needsUpdate = true
    }

    setDocumentCursor(isHovered ? 'pointer' : '')
    invalidate()
  }

  const handleButtonPointerEnter = (event: ThreeEvent<PointerEvent>) => {
    event.stopPropagation()
    setHovered(true)
  }

  const handleButtonPointerLeave = (event: ThreeEvent<PointerEvent>) => {
    event.stopPropagation()
    setHovered(false)
  }

  const handleClick = (event: ThreeEvent<MouseEvent>) => {
    event.stopPropagation()
    onClick()
    invalidate()
  }

  return (
    <group position={position}>
      <mesh
        raycast={enablePointerEvents ? undefined : DISABLED_RAYCAST}
        userData={{ monitorAction: action }}
        onClick={enablePointerEvents ? handleClick : undefined}
        onPointerEnter={
          enablePointerEvents ? handleButtonPointerEnter : undefined
        }
        onPointerLeave={
          enablePointerEvents ? handleButtonPointerLeave : undefined
        }
      >
        <planeGeometry args={size} />
        <meshBasicMaterial
          ref={materialRef}
          colorWrite={false}
          color="#ffffff"
          depthWrite={false}
          opacity={0}
          side={THREE.FrontSide}
          transparent
        />
      </mesh>
      {symbol && (
        <group ref={arrowGroupRef} position={[0, 0, 0.012]}>
          <Text
            anchorX="center"
            anchorY="middle"
            color="#ffffff"
            font={MONITOR_FONT_URL}
            fontSize={MONITOR_NAV_ARROW_FONT_SIZE}
            outlineColor="#231648"
            outlineOpacity={0.7}
            outlineWidth={MONITOR_NAV_ARROW_OUTLINE_WIDTH}
            textAlign="center"
          >
            {symbol}
          </Text>
        </group>
      )}
      {!symbol && (
        <Text
          anchorX="center"
          anchorY="middle"
          color="#ffffff"
          font={MONITOR_FONT_URL}
          fontSize={0.001}
          position={[0, 0, 0.012]}
        >
          {label}
        </Text>
      )}
    </group>
  )
}

function AnimatedStartButton({
  enablePointerEvents,
  entranceProgressRef,
  glowColor,
  label,
  onClick,
  texture,
}: {
  enablePointerEvents: boolean
  entranceProgressRef?: MonitorEntranceProgressRef
  glowColor: string
  label: string
  onClick: () => void
  texture: THREE.Texture
}) {
  const {
    buttonGroupRef,
    buttonMaterialRef,
    glowMaterialRef,
    handleButtonPointerDown,
    handleButtonPointerEnter,
    handleButtonPointerLeave,
    handleButtonPointerUp,
    handleClick,
  } = useMonitorStartButtonAnimation({
    basePosition: MONITOR_START_BUTTON_POSITION,
    entranceProgressRef,
    onClick,
  })

  return (
    <group
      ref={buttonGroupRef}
      position={MONITOR_START_BUTTON_POSITION}
      renderOrder={MONITOR_START_BUTTON_LAYER_Z * 1000}
    >
      <mesh
        raycast={DISABLED_RAYCAST}
        renderOrder={MONITOR_START_BUTTON_LAYER_Z * 1000 - 1}
      >
        <planeGeometry args={MONITOR_START_BUTTON_GLOW_SIZE} />
        <meshBasicMaterial
          ref={glowMaterialRef}
          alphaTest={0.02}
          blending={THREE.AdditiveBlending}
          color={glowColor}
          depthWrite={false}
          map={texture}
          opacity={0}
          side={THREE.FrontSide}
          toneMapped={false}
          transparent
        />
      </mesh>
      <mesh
        raycast={DISABLED_RAYCAST}
        renderOrder={MONITOR_START_BUTTON_LAYER_Z * 1000}
      >
        <planeGeometry args={MONITOR_START_BUTTON_SIZE} />
        <meshBasicMaterial
          ref={buttonMaterialRef}
          alphaTest={0.02}
          map={texture}
          opacity={1}
          side={THREE.FrontSide}
          toneMapped={false}
          transparent
        />
      </mesh>
      <mesh
        raycast={enablePointerEvents ? undefined : DISABLED_RAYCAST}
        userData={{ monitorAction: 'start', monitorLabel: label }}
        position={[
          0,
          0,
          MONITOR_START_BUTTON_HOTSPOT_POSITION[2] -
            MONITOR_START_BUTTON_POSITION[2],
        ]}
        onClick={enablePointerEvents ? handleClick : undefined}
        onPointerDown={
          enablePointerEvents ? handleButtonPointerDown : undefined
        }
        onPointerEnter={
          enablePointerEvents ? handleButtonPointerEnter : undefined
        }
        onPointerLeave={
          enablePointerEvents ? handleButtonPointerLeave : undefined
        }
        onPointerUp={enablePointerEvents ? handleButtonPointerUp : undefined}
      >
        <planeGeometry args={MONITOR_START_BUTTON_SIZE} />
        <meshBasicMaterial
          colorWrite={false}
          color="#ffffff"
          depthWrite={false}
          opacity={0}
          side={THREE.FrontSide}
          transparent
        />
      </mesh>
      <Text
        anchorX="center"
        anchorY="middle"
        color="#ffffff"
        font={MONITOR_FONT_URL}
        fontSize={0.001}
        position={[0, 0, MONITOR_HOTSPOT_LAYER_Z - MONITOR_START_BUTTON_LAYER_Z]}
      >
        {label}
      </Text>
    </group>
  )
}

function MonitorGameScreen({
  asset,
  entranceProgressRef,
  textures,
}: {
  asset: MonitorScreenAsset
  entranceProgressRef?: MonitorEntranceProgressRef
  textures: Record<string, THREE.Texture>
}) {
  return (
    <>
      {asset.ribbon && asset.ribbonPosition && asset.ribbonSize && (
        <AnimatedTexturePlane
          entranceProgressRef={entranceProgressRef}
          position={asset.ribbonPosition}
          size={asset.ribbonSize}
          texture={textures[asset.ribbon]}
        />
      )}
      <AnimatedLogo
        entranceProgressRef={entranceProgressRef}
        position={asset.logoPosition}
        size={asset.logoSize}
        texture={textures[asset.logo]}
      />
    </>
  )
}

function AnimatedMonitorGameContent({
  enableInternalHitboxes,
  gameTitle,
  glowColor,
  onStartGame,
  selectedAsset,
  selectedGameIndex,
  textures,
}: {
  enableInternalHitboxes: boolean
  gameTitle: string
  glowColor: string
  onStartGame: () => void
  selectedAsset: MonitorScreenAsset
  selectedGameIndex: number
  textures: Record<string, THREE.Texture>
}) {
  const {
    buttonEntranceProgressRef,
    logoEntranceProgressRef,
    screenGroupRef,
  } = useMonitorEntranceSequence({
    selectedGameIndex,
  })

  return (
    <>
      <MonitorTexturePlane
        position={[0, 0, MONITOR_BACKGROUND_LAYER_Z]}
        size={HUB_MONITOR_SCREEN_SIZE}
        texture={textures[selectedAsset.background]}
      />
      <group ref={screenGroupRef}>
        <MonitorGameScreen
          asset={selectedAsset}
          entranceProgressRef={logoEntranceProgressRef}
          textures={textures}
        />
        <AnimatedStartButton
          enablePointerEvents={enableInternalHitboxes}
          entranceProgressRef={buttonEntranceProgressRef}
          glowColor={glowColor}
          label={`${gameTitle} 시작`}
          onClick={onStartGame}
          texture={textures[selectedAsset.startButton]}
        />
      </group>
    </>
  )
}

export default function MonitorGameSelector({
  enableInternalHitboxes = true,
  position = HUB_MONITOR_SCREEN_POSITION,
  quaternion,
  scale = 1,
  transitionControlsRef,
}: MonitorGameSelectorProps) {
  const {
    focusMonitor,
    selectedGame,
    selectedGameIndex,
    selectGame,
    startSelectedGame,
  } = useMonitorGameSelector({ enableKeyboardShortcuts: false })
  const invalidate = useThree((state) => state.invalidate)
  const isMonitorFocused = useHubRoomStore(
    (state) => state.focusKey === 'monitor',
  )
  const hasEnteredHub = useHubOnboardingStore((state) => state.hasEnteredHub)
  const shouldAnimateNavArrows = isMonitorFocused
  const shouldWarmAllMonitorTextures = useMonitorTextureWarmupTrigger({
    hasEnteredHub,
    isMonitorFocused,
  })
  const monitorTransitionRequest = useHubMonitorTransitionStore(
    (state) => state.currentRequest,
  )
  const clearMonitorTransitionRequest = useHubMonitorTransitionStore(
    (state) => state.clearRequest,
  )
  const requestMonitorGameTransition = useHubMonitorTransitionStore(
    (state) => state.requestGameTransition,
  )
  const [pendingGameRequest, setPendingGameRequest] =
    useState<PendingMonitorGameRequest | null>(null)
  const latestPendingRequestIdRef = useRef(0)
  const pendingTransitionRequestIdRef = useRef<number | null>(null)
  const requestedGameIndexRef = useRef(selectedGameIndex)
  const selectedAsset = MONITOR_SCREEN_ASSETS[selectedGame.id]
  const pendingGame =
    pendingGameRequest !== null
      ? HUB_GAMES[getWrappedGameIndex(pendingGameRequest.gameIndex)]
      : null
  const pendingGlowColor =
    pendingGame?.lightingColor ?? selectedGame.lightingColor
  const selectedTextureUrls = useMemo(() => {
    return getMonitorAssetTextureUrls(selectedAsset)
  }, [selectedAsset])
  const textureList = useTexture(
    selectedTextureUrls,
    configureMonitorTextureList,
  ) as THREE.Texture[]
  const textures = selectedTextureUrls.reduce<Record<string, THREE.Texture>>(
    (textureMap, textureUrl, textureIndex) => {
      textureMap[textureUrl] = textureList[textureIndex]
      return textureMap
    },
    {},
  )

  useEffect(() => {
    textureList.forEach(configureMonitorTexture)
  }, [textureList])

  useEffect(() => {
    if (pendingGameRequest) return

    requestedGameIndexRef.current = selectedGameIndex
  }, [pendingGameRequest, selectedGameIndex])

  const requestGameIndexTransition = useCallback(
    (gameIndex: number, transitionRequestId?: number) => {
      const nextGameIndex = getWrappedGameIndex(gameIndex)
      latestPendingRequestIdRef.current += 1
      pendingTransitionRequestIdRef.current = transitionRequestId ?? null
      requestedGameIndexRef.current = nextGameIndex

      if (nextGameIndex === selectedGameIndex) {
        if (transitionRequestId !== undefined) {
          clearMonitorTransitionRequest(transitionRequestId)
        }
        pendingTransitionRequestIdRef.current = null
        setPendingGameRequest(null)
        invalidate()
        return
      }

      preloadMonitorTextures(
        getMonitorAssetTextureUrls(getMonitorAssetByGameIndex(nextGameIndex)),
      )
      setPendingGameRequest({
        gameIndex: nextGameIndex,
        requestId: latestPendingRequestIdRef.current,
      })
      invalidate()
    },
    [clearMonitorTransitionRequest, invalidate, selectedGameIndex],
  )

  const requestGameTransition = useCallback(
    (direction: -1 | 1) => {
      const nextGameIndex = getWrappedGameIndex(
        requestedGameIndexRef.current + direction,
      )

      requestedGameIndexRef.current = nextGameIndex
      requestMonitorGameTransition(nextGameIndex)
      invalidate()
    },
    [invalidate, requestMonitorGameTransition],
  )

  const handlePendingTextureReady = useCallback(
    (gameIndex: number, requestId: number) => {
      if (latestPendingRequestIdRef.current !== requestId) return

      const transitionRequestId = pendingTransitionRequestIdRef.current
      pendingTransitionRequestIdRef.current = null

      if (transitionRequestId !== null) {
        clearMonitorTransitionRequest(transitionRequestId)
      }

      requestedGameIndexRef.current = gameIndex
      selectGame(gameIndex)
      setPendingGameRequest((currentRequest) =>
        currentRequest?.requestId === requestId ? null : currentRequest,
      )
      invalidate()
    },
    [clearMonitorTransitionRequest, invalidate, selectGame],
  )

  const requestPreviousGame = useCallback(() => {
    requestGameTransition(-1)
  }, [requestGameTransition])

  const requestNextGame = useCallback(() => {
    requestGameTransition(1)
  }, [requestGameTransition])

  useEffect(() => {
    if (!monitorTransitionRequest) return

    let cancelled = false

    ;(async () => {
      await Promise.resolve()

      if (cancelled) return

      requestGameIndexTransition(
        monitorTransitionRequest.gameIndex,
        monitorTransitionRequest.requestId,
      )
    })()

    return () => {
      cancelled = true
    }
  }, [
    monitorTransitionRequest,
    requestGameIndexTransition,
  ])

  useEffect(() => {
    if (!transitionControlsRef) return

    transitionControlsRef.current = {
      requestNextGame,
      requestPreviousGame,
    }

    return () => {
      transitionControlsRef.current = null
    }
  }, [requestNextGame, requestPreviousGame, transitionControlsRef])

  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (isMonitorKeyboardInputTarget(event.target)) return

      if (event.key === 'ArrowLeft') {
        event.preventDefault()
        trackHubInvalidate('monitor.keyboard.previous')
        requestPreviousGame()
        return
      }

      if (event.key === 'ArrowRight') {
        event.preventDefault()
        trackHubInvalidate('monitor.keyboard.next')
        requestNextGame()
        return
      }

      if (event.code === 'Space' || event.key === ' ' || event.key === 'Spacebar') {
        event.preventDefault()
        trackHubInvalidate('monitor.keyboard.start')
        startSelectedGame()
      }
    }

    window.addEventListener('keydown', handleKeyDown)

    return () => {
      window.removeEventListener('keydown', handleKeyDown)
    }
  }, [requestNextGame, requestPreviousGame, startSelectedGame])

  const handleScreenClick = (event: ThreeEvent<MouseEvent>) => {
    event.stopPropagation()
    focusMonitor()
  }

  return (
    <group
      position={position}
      quaternion={quaternion}
      scale={scale}
    >
      <Suspense fallback={null}>
        <MonitorTextureWarmup isEnabled={shouldWarmAllMonitorTextures} />
      </Suspense>
      <Suspense fallback={null}>
        {pendingGameRequest && (
          <PendingMonitorTextureCommit
            key={pendingGameRequest.requestId}
            gameIndex={pendingGameRequest.gameIndex}
            onReady={handlePendingTextureReady}
            requestId={pendingGameRequest.requestId}
          />
        )}
      </Suspense>

      <mesh
        raycast={enableInternalHitboxes ? undefined : DISABLED_RAYCAST}
        onClick={enableInternalHitboxes ? handleScreenClick : undefined}
      >
        <planeGeometry args={HUB_MONITOR_SCREEN_SIZE} />
        <meshStandardMaterial
          color="#17142b"
          emissive={selectedGame.lightingColor}
          emissiveIntensity={0.42}
          roughness={0.32}
          toneMapped={false}
        />
      </mesh>

      <AnimatedMonitorGameContent
        enableInternalHitboxes={enableInternalHitboxes}
        gameTitle={selectedGame.title}
        glowColor={selectedGame.lightingColor}
        onStartGame={startSelectedGame}
        selectedAsset={selectedAsset}
        selectedGameIndex={selectedGameIndex}
        textures={textures}
      />
      <MonitorTransitionFeedback
        glowColor={pendingGlowColor}
        isActive={pendingGameRequest !== null}
      />

      <MonitorHotspot
        action="previous"
        enablePointerEvents={enableInternalHitboxes}
        isArrowAnimated={shouldAnimateNavArrows}
        label="이전 게임"
        onClick={requestPreviousGame}
        position={[-1.08, 0, MONITOR_HOTSPOT_LAYER_Z]}
        size={MONITOR_SIDE_HOTSPOT_SIZE}
        symbol="‹"
      />
      <MonitorHotspot
        action="next"
        enablePointerEvents={enableInternalHitboxes}
        isArrowAnimated={shouldAnimateNavArrows}
        label="다음 게임"
        onClick={requestNextGame}
        position={[1.08, 0, MONITOR_HOTSPOT_LAYER_Z]}
        size={MONITOR_SIDE_HOTSPOT_SIZE}
        symbol="›"
      />
    </group>
  )
}
