import { Text } from '@react-three/drei'
import type { ThreeEvent } from '@react-three/fiber'
import * as THREE from 'three'
import {
  HUB_MONITOR_SCREEN_POSITION,
  HUB_MONITOR_SCREEN_SIZE,
} from '@/shared/constants'
import type { MonitorGameAction } from './hooks'
import {
  useMonitorButtonMaterial,
  useMonitorGameSelector,
} from './hooks'

const MONITOR_FONT_URL = '/fonts/Paperlogy-6SemiBold.woff'

function MonitorActionButton({
  action,
  color,
  label,
  onClick,
  position,
  size,
  symbol,
}: {
  action: MonitorGameAction
  color: string
  label: string
  onClick: () => void
  position: [number, number, number]
  size: [number, number]
  symbol?: string
}) {
  const {
    handlePointerEnter,
    handlePointerLeave,
    materialRef,
    notifyClick,
  } = useMonitorButtonMaterial(action, color)

  const handleClick = (event: ThreeEvent<MouseEvent>) => {
    event.stopPropagation()
    onClick()
    notifyClick()
  }

  return (
    <group position={position}>
      <mesh
        userData={{ monitorAction: action }}
        onClick={handleClick}
        onPointerEnter={handlePointerEnter}
        onPointerLeave={handlePointerLeave}
      >
        <planeGeometry args={size} />
        <meshBasicMaterial
          ref={materialRef}
          color={color}
          opacity={0.84}
          side={THREE.FrontSide}
          transparent
        />
      </mesh>
      <Text
        anchorX="center"
        anchorY="middle"
        color="#ffffff"
        font={MONITOR_FONT_URL}
        fontSize={symbol ? 0.12 : 0.085}
        maxWidth={size[0] * 0.86}
        outlineColor="rgba(22, 18, 42, 0.18)"
        outlineWidth={0.002}
        position={[0, 0, 0.012]}
        textAlign="center"
      >
        {symbol ?? label}
      </Text>
    </group>
  )
}

export default function MonitorGameSelector() {
  const {
    focusMonitor,
    gameCount,
    handleScreenPointerEnter,
    handleScreenPointerLeave,
    selectedGame,
    selectedGameIndex,
    selectNextGame,
    selectPreviousGame,
    startSelectedGame,
  } = useMonitorGameSelector()

  const handleScreenClick = (event: ThreeEvent<MouseEvent>) => {
    event.stopPropagation()
    focusMonitor()
  }

  return (
    <group position={HUB_MONITOR_SCREEN_POSITION}>
      <mesh
        onClick={handleScreenClick}
        onPointerEnter={handleScreenPointerEnter}
        onPointerLeave={handleScreenPointerLeave}
      >
        <planeGeometry args={HUB_MONITOR_SCREEN_SIZE} />
        <meshStandardMaterial
          color="#17142b"
          emissive="#a995ff"
          emissiveIntensity={0.34}
          roughness={0.36}
          toneMapped={false}
        />
      </mesh>

      <mesh position={[0, 0.09, 0.018]}>
        <planeGeometry args={[2.14, 0.92]} />
        <meshBasicMaterial color="#151326" opacity={0.92} transparent />
      </mesh>

      <mesh position={[0, 0.52, 0.022]}>
        <planeGeometry args={[2.02, 0.032]} />
        <meshBasicMaterial color={selectedGame.accentColor} toneMapped={false} />
      </mesh>

      <Text
        anchorX="left"
        anchorY="middle"
        color="#ffffff"
        font={MONITOR_FONT_URL}
        fontSize={0.075}
        letterSpacing={0}
        position={[-0.96, 0.43, 0.038]}
      >
        NEMONIC PLAY
      </Text>

      <Text
        anchorX="right"
        anchorY="middle"
        color="#d7d0ff"
        font={MONITOR_FONT_URL}
        fontSize={0.064}
        letterSpacing={0}
        position={[0.96, 0.43, 0.038]}
      >
        {selectedGameIndex + 1} / {gameCount}
      </Text>

      <Text
        anchorX="center"
        anchorY="middle"
        color="#ffffff"
        font={MONITOR_FONT_URL}
        fontSize={0.16}
        fontWeight={700}
        letterSpacing={0}
        maxWidth={1.72}
        outlineColor="rgba(189, 157, 255, 0.28)"
        outlineWidth={0.006}
        position={[0, 0.22, 0.04]}
        textAlign="center"
      >
        {selectedGame.title}
      </Text>

      <Text
        anchorX="center"
        anchorY="middle"
        color="#dcd5ff"
        font={MONITOR_FONT_URL}
        fontSize={0.071}
        letterSpacing={0}
        lineHeight={1.25}
        maxWidth={1.66}
        position={[0, 0.01, 0.04]}
        textAlign="center"
      >
        {selectedGame.description}
      </Text>

      <MonitorActionButton
        action="previous"
        color="#4b416b"
        label="이전 게임"
        onClick={selectPreviousGame}
        position={[-0.76, -0.39, 0.042]}
        size={[0.28, 0.24]}
        symbol="‹"
      />
      <MonitorActionButton
        action="start"
        color={selectedGame.accentColor}
        label="시작하기"
        onClick={startSelectedGame}
        position={[0, -0.39, 0.042]}
        size={[0.76, 0.24]}
      />
      <MonitorActionButton
        action="next"
        color="#4b416b"
        label="다음 게임"
        onClick={selectNextGame}
        position={[0.76, -0.39, 0.042]}
        size={[0.28, 0.24]}
        symbol="›"
      />
    </group>
  )
}
