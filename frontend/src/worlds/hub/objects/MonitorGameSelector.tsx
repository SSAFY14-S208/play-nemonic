import { Text, useTexture } from '@react-three/drei'
import type { ThreeEvent } from '@react-three/fiber'
import { useFrame, useThree } from '@react-three/fiber'
import { useEffect, useRef } from 'react'
import * as THREE from 'three'
import {
  HUB_MONITOR_SCREEN_POSITION,
  HUB_MONITOR_SCREEN_SIZE,
} from '@/shared/constants'
import { useHubRoomStore } from '@/shared/stores'
import type { HubGameId } from '@/shared/types'
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

const MONITOR_TEXTURE_URLS = Object.values(MONITOR_SCREEN_ASSETS).flatMap(
  (asset) =>
    asset.ribbon
      ? [asset.background, asset.logo, asset.startButton, asset.ribbon]
      : [asset.background, asset.logo, asset.startButton],
)

type MonitorGameSelectorScale = number | [number, number, number]

interface MonitorGameSelectorProps {
  position?: [number, number, number]
  quaternion?: [number, number, number, number]
  scale?: MonitorGameSelectorScale
}

function configureMonitorTexture(texture: THREE.Texture) {
  texture.colorSpace = THREE.SRGBColorSpace
  texture.anisotropy = 4
  texture.minFilter = THREE.LinearFilter
  texture.magFilter = THREE.LinearFilter
  texture.generateMipmaps = false
  texture.needsUpdate = true
}

function setDocumentCursor(cursor: string) {
  if (typeof document === 'undefined') return

  document.body.style.cursor = cursor
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
    <mesh position={position} renderOrder={position[2] * 1000}>
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
      <mesh>
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
      <mesh>
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
  isArrowAnimated,
  label,
  onClick,
  position,
  size,
  symbol,
}: {
  action: MonitorGameAction
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
        userData={{ monitorAction: action }}
        onClick={handleClick}
        onPointerEnter={handleButtonPointerEnter}
        onPointerLeave={handleButtonPointerLeave}
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
  accentColor,
  entranceProgressRef,
  label,
  onClick,
  texture,
}: {
  accentColor: string
  entranceProgressRef?: MonitorEntranceProgressRef
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
      <mesh renderOrder={MONITOR_START_BUTTON_LAYER_Z * 1000 - 1}>
        <planeGeometry args={MONITOR_START_BUTTON_GLOW_SIZE} />
        <meshBasicMaterial
          ref={glowMaterialRef}
          alphaTest={0.02}
          blending={THREE.AdditiveBlending}
          color={accentColor}
          depthWrite={false}
          map={texture}
          opacity={0}
          side={THREE.FrontSide}
          toneMapped={false}
          transparent
        />
      </mesh>
      <mesh renderOrder={MONITOR_START_BUTTON_LAYER_Z * 1000}>
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
        userData={{ monitorAction: 'start', monitorLabel: label }}
        position={[
          0,
          0,
          MONITOR_START_BUTTON_HOTSPOT_POSITION[2] -
            MONITOR_START_BUTTON_POSITION[2],
        ]}
        onClick={handleClick}
        onPointerDown={handleButtonPointerDown}
        onPointerEnter={handleButtonPointerEnter}
        onPointerLeave={handleButtonPointerLeave}
        onPointerUp={handleButtonPointerUp}
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
  accentColor,
  gameTitle,
  onStartGame,
  selectedAsset,
  selectedGameIndex,
  textures,
}: {
  accentColor: string
  gameTitle: string
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
          accentColor={accentColor}
          entranceProgressRef={buttonEntranceProgressRef}
          label={`${gameTitle} 시작`}
          onClick={onStartGame}
          texture={textures[selectedAsset.startButton]}
        />
      </group>
    </>
  )
}

export default function MonitorGameSelector({
  position = HUB_MONITOR_SCREEN_POSITION,
  quaternion,
  scale = 1,
}: MonitorGameSelectorProps) {
  const {
    focusMonitor,
    selectedGame,
    selectedGameIndex,
    selectNextGame,
    selectPreviousGame,
    startSelectedGame,
  } = useMonitorGameSelector()
  const shouldAnimateNavArrows = useHubRoomStore(
    (state) => state.focusKey === 'monitor',
  )
  const textureList = useTexture(MONITOR_TEXTURE_URLS) as THREE.Texture[]
  const textures = MONITOR_TEXTURE_URLS.reduce<Record<string, THREE.Texture>>(
    (textureMap, textureUrl, textureIndex) => {
      textureMap[textureUrl] = textureList[textureIndex]
      return textureMap
    },
    {},
  )
  const selectedAsset = MONITOR_SCREEN_ASSETS[selectedGame.id]

  useEffect(() => {
    textureList.forEach(configureMonitorTexture)
  }, [textureList])

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
      <mesh
        onClick={handleScreenClick}
      >
        <planeGeometry args={HUB_MONITOR_SCREEN_SIZE} />
        <meshStandardMaterial
          color="#17142b"
          emissive={selectedGame.accentColor}
          emissiveIntensity={0.42}
          roughness={0.32}
          toneMapped={false}
        />
      </mesh>

      <AnimatedMonitorGameContent
        accentColor={selectedGame.accentColor}
        gameTitle={selectedGame.title}
        onStartGame={startSelectedGame}
        selectedAsset={selectedAsset}
        selectedGameIndex={selectedGameIndex}
        textures={textures}
      />

      <MonitorHotspot
        action="previous"
        isArrowAnimated={shouldAnimateNavArrows}
        label="이전 게임"
        onClick={selectPreviousGame}
        position={[-1.08, 0, MONITOR_HOTSPOT_LAYER_Z]}
        size={MONITOR_SIDE_HOTSPOT_SIZE}
        symbol="‹"
      />
      <MonitorHotspot
        action="next"
        isArrowAnimated={shouldAnimateNavArrows}
        label="다음 게임"
        onClick={selectNextGame}
        position={[1.08, 0, MONITOR_HOTSPOT_LAYER_Z]}
        size={MONITOR_SIDE_HOTSPOT_SIZE}
        symbol="›"
      />
    </group>
  )
}
