import { useCallback, useState } from 'react'
import type { ThreeEvent } from '@react-three/fiber'
import { useThree } from '@react-three/fiber'
import { useRouter } from 'next/navigation'
import CommunityCanvasWhiteboardPreviewMesh from '@/worlds/hub/objects/CommunityCanvasWhiteboardPreviewMesh'
import { useMonitorGameSelector } from '@/worlds/hub/objects/hooks'
import MonitorGameSelector from '@/worlds/hub/objects/MonitorGameSelector'
import { HUB_COMMUNITY_CANVAS_PATH } from '@/worlds/hub/constants'
import { useHubRoomStore } from '@/shared/stores'
import type { RoomPreviewHubHitboxConfigs } from '../useRoomPreviewHubHitboxCalibration'
import RoomPreviewHubHitboxMesh from './RoomPreviewHubHitboxMesh'

const STAGE6_MONITOR_DOM_POSITION: [number, number, number] = [
  -0.21411,
  0.47817,
  -0.6362,
]

const STAGE6_MONITOR_DOM_QUATERNION: [number, number, number, number] = [
  0,
  -0.006045,
  0,
  0.999982,
]

const STAGE6_MONITOR_DOM_SCALE = 0.1429

const STAGE6_WHITEBOARD_DOM_POSITION: [number, number, number] = [
  -0.648,
  0.56229,
  -0.10868,
]

const STAGE6_WHITEBOARD_DOM_QUATERNION: [number, number, number, number] = [
  0,
  -0.707107,
  0,
  0.707107,
]

const NEMONIC_SINGLE_ROOM_PATH = '/nemonic'
const NEMONIC_FOCUS_KEY = 'printer'
const COMMUNITY_BOARD_FOCUS_KEY = 'communityBoard'

const HITBOX_COLORS = {
  communityBoard: '#63f0a3',
  monitorNext: '#8f7aff',
  monitorPrevious: '#8f7aff',
  monitorScreen: '#4dd8ff',
  monitorStart: '#ffd166',
  nemonic: '#ff5f86',
} satisfies Record<keyof RoomPreviewHubHitboxConfigs, string>

function setDocumentCursor(cursor: string) {
  if (typeof document === 'undefined') return

  document.body.style.cursor = cursor
}

export default function RoomPreviewHubDomSurfaces({
  hitboxConfigs,
  showHitboxes,
}: {
  hitboxConfigs: RoomPreviewHubHitboxConfigs
  showHitboxes: boolean
}) {
  const router = useRouter()
  const invalidate = useThree((state) => state.invalidate)
  const focusKey = useHubRoomStore((state) => state.focusKey)
  const setFocus = useHubRoomStore((state) => state.setFocus)
  const {
    focusMonitor,
    selectNextGame,
    selectPreviousGame,
    startSelectedGame,
  } = useMonitorGameSelector({ enableKeyboardShortcuts: false })
  const [isWhiteboardHovered, setIsWhiteboardHovered] = useState(false)

  const setHitboxHovered = useCallback((isHovered: boolean) => {
    setDocumentCursor(isHovered ? 'pointer' : '')
  }, [])

  const handleNemonicClick = useCallback(
    (event: ThreeEvent<MouseEvent>) => {
      event.stopPropagation()
      setDocumentCursor('')

      if (focusKey !== NEMONIC_FOCUS_KEY) {
        setFocus(NEMONIC_FOCUS_KEY)
        invalidate()
        return
      }

      router.push(NEMONIC_SINGLE_ROOM_PATH)
    },
    [focusKey, invalidate, router, setFocus],
  )

  const handleWhiteboardClick = useCallback(
    (event: ThreeEvent<MouseEvent>) => {
      event.stopPropagation()
      setDocumentCursor('')
      setIsWhiteboardHovered(false)

      if (focusKey !== COMMUNITY_BOARD_FOCUS_KEY) {
        setFocus(COMMUNITY_BOARD_FOCUS_KEY)
        invalidate()
        return
      }

      router.push(HUB_COMMUNITY_CANVAS_PATH)
    },
    [focusKey, invalidate, router, setFocus],
  )

  const handleWhiteboardPointerEnter = useCallback(
    (event: ThreeEvent<PointerEvent>) => {
      event.stopPropagation()
      setDocumentCursor('pointer')
      setIsWhiteboardHovered(true)
      invalidate()
    },
    [invalidate],
  )

  const handleWhiteboardPointerLeave = useCallback(
    (event: ThreeEvent<PointerEvent>) => {
      event.stopPropagation()
      setDocumentCursor('')
      setIsWhiteboardHovered(false)
      invalidate()
    },
    [invalidate],
  )

  const handlePointerEnter = useCallback(
    (event: ThreeEvent<PointerEvent>) => {
      event.stopPropagation()
      setHitboxHovered(true)
    },
    [setHitboxHovered],
  )

  const handlePointerLeave = useCallback(
    (event: ThreeEvent<PointerEvent>) => {
      event.stopPropagation()
      setHitboxHovered(false)
    },
    [setHitboxHovered],
  )

  const handleMonitorScreenClick = useCallback(
    (event: ThreeEvent<MouseEvent>) => {
      event.stopPropagation()
      focusMonitor()
    },
    [focusMonitor],
  )

  const handleMonitorPreviousClick = useCallback(
    (event: ThreeEvent<MouseEvent>) => {
      event.stopPropagation()
      selectPreviousGame()
      invalidate()
    },
    [invalidate, selectPreviousGame],
  )

  const handleMonitorNextClick = useCallback(
    (event: ThreeEvent<MouseEvent>) => {
      event.stopPropagation()
      selectNextGame()
      invalidate()
    },
    [invalidate, selectNextGame],
  )

  const handleMonitorStartClick = useCallback(
    (event: ThreeEvent<MouseEvent>) => {
      event.stopPropagation()
      startSelectedGame()
    },
    [startSelectedGame],
  )

  return (
    <>
      <MonitorGameSelector
        enableInternalHitboxes={false}
        position={STAGE6_MONITOR_DOM_POSITION}
        quaternion={STAGE6_MONITOR_DOM_QUATERNION}
        scale={STAGE6_MONITOR_DOM_SCALE}
      />
      <CommunityCanvasWhiteboardPreviewMesh
        flipContentX
        isExpanded={isWhiteboardHovered}
        position={STAGE6_WHITEBOARD_DOM_POSITION}
        quaternion={STAGE6_WHITEBOARD_DOM_QUATERNION}
      />
      <RoomPreviewHubHitboxMesh
        color={HITBOX_COLORS.nemonic}
        config={hitboxConfigs.nemonic}
        isVisible={showHitboxes}
        name="NEMONIC"
        onClick={handleNemonicClick}
        onPointerEnter={handlePointerEnter}
        onPointerLeave={handlePointerLeave}
      />
      <group
        position={STAGE6_MONITOR_DOM_POSITION}
        quaternion={STAGE6_MONITOR_DOM_QUATERNION}
        scale={STAGE6_MONITOR_DOM_SCALE}
      >
        <RoomPreviewHubHitboxMesh
          color={HITBOX_COLORS.monitorScreen}
          config={hitboxConfigs.monitorScreen}
          isVisible={showHitboxes}
          name="MONITOR_SCREEN"
          onClick={handleMonitorScreenClick}
          onPointerEnter={handlePointerEnter}
          onPointerLeave={handlePointerLeave}
        />
        <RoomPreviewHubHitboxMesh
          color={HITBOX_COLORS.monitorPrevious}
          config={hitboxConfigs.monitorPrevious}
          isVisible={showHitboxes}
          name="MONITOR_PREVIOUS"
          onClick={handleMonitorPreviousClick}
          onPointerEnter={handlePointerEnter}
          onPointerLeave={handlePointerLeave}
        />
        <RoomPreviewHubHitboxMesh
          color={HITBOX_COLORS.monitorNext}
          config={hitboxConfigs.monitorNext}
          isVisible={showHitboxes}
          name="MONITOR_NEXT"
          onClick={handleMonitorNextClick}
          onPointerEnter={handlePointerEnter}
          onPointerLeave={handlePointerLeave}
        />
        <RoomPreviewHubHitboxMesh
          color={HITBOX_COLORS.monitorStart}
          config={hitboxConfigs.monitorStart}
          isVisible={showHitboxes}
          name="MONITOR_START"
          onClick={handleMonitorStartClick}
          onPointerEnter={handlePointerEnter}
          onPointerLeave={handlePointerLeave}
        />
      </group>
      <group
        position={STAGE6_WHITEBOARD_DOM_POSITION}
        quaternion={STAGE6_WHITEBOARD_DOM_QUATERNION}
      >
        <RoomPreviewHubHitboxMesh
          color={HITBOX_COLORS.communityBoard}
          config={hitboxConfigs.communityBoard}
          isVisible={showHitboxes}
          name="COMMUNITY_BOARD"
          onClick={handleWhiteboardClick}
          onPointerEnter={handleWhiteboardPointerEnter}
          onPointerLeave={handleWhiteboardPointerLeave}
        />
      </group>
    </>
  )
}
