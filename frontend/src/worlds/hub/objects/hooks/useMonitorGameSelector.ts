import { useCallback, useEffect, useRef } from 'react'
import { useRouter } from 'next/navigation'
import { useThree, type ThreeEvent } from '@react-three/fiber'
import type * as THREE from 'three'
import { HUB_GAMES } from '@/shared/constants'
import { useHubGameStore, useHubRoomStore } from '@/shared/stores'
import { trackHubInvalidate } from '@/shared/utils'

export type MonitorGameAction = 'previous' | 'next' | 'start'

function setDocumentCursor(cursor: string) {
  if (typeof document === 'undefined') return
  document.body.style.cursor = cursor
}

export function useMonitorGameSelector() {
  const router = useRouter()
  const invalidate = useThree((state) => state.invalidate)
  const selectedGameIndex = useHubGameStore((state) => state.selectedGameIndex)
  const selectNextGame = useHubGameStore((state) => state.selectNextGame)
  const selectPreviousGame = useHubGameStore((state) => state.selectPreviousGame)
  const setFocus = useHubRoomStore((state) => state.setFocus)
  const selectedGame = HUB_GAMES[selectedGameIndex] ?? HUB_GAMES[0]

  useEffect(() => {
    trackHubInvalidate('monitor.selectedGame')
    invalidate()
  }, [invalidate, selectedGameIndex])

  const focusMonitor = useCallback(() => {
    setFocus('monitor')
    trackHubInvalidate('monitor.screenClick')
    invalidate()
  }, [invalidate, setFocus])

  const startSelectedGame = useCallback(() => {
    trackHubInvalidate('monitor.start.click')
    invalidate()
    router.push(selectedGame.route)
  }, [invalidate, router, selectedGame.route])

  const handleScreenPointerEnter = useCallback(() => {
    setDocumentCursor('pointer')
  }, [])

  const handleScreenPointerLeave = useCallback(() => {
    setDocumentCursor('')
  }, [])

  return {
    focusMonitor,
    gameCount: HUB_GAMES.length,
    handleScreenPointerEnter,
    handleScreenPointerLeave,
    selectedGame,
    selectedGameIndex,
    selectNextGame,
    selectPreviousGame,
    startSelectedGame,
  }
}

export function useMonitorButtonMaterial(
  action: MonitorGameAction,
  color: string,
) {
  const materialRef = useRef<THREE.MeshBasicMaterial>(null)
  const invalidate = useThree((state) => state.invalidate)

  const applyHoverState = useCallback(
    (isHovered: boolean) => {
      const material = materialRef.current

      if (material) {
        material.color.set(isHovered ? '#ffffff' : color)
        material.opacity = isHovered ? 0.95 : 0.84
        material.needsUpdate = true
      }

      setDocumentCursor(isHovered ? 'pointer' : '')
      trackHubInvalidate(`monitor.${action}.hover`)
      invalidate()
    },
    [action, color, invalidate],
  )

  const handlePointerEnter = useCallback(
    (event: ThreeEvent<PointerEvent>) => {
      event.stopPropagation()
      applyHoverState(true)
    },
    [applyHoverState],
  )

  const handlePointerLeave = useCallback(
    (event: ThreeEvent<PointerEvent>) => {
      event.stopPropagation()
      applyHoverState(false)
    },
    [applyHoverState],
  )

  const notifyClick = useCallback(() => {
    trackHubInvalidate(`monitor.${action}.click`)
    invalidate()
  }, [action, invalidate])

  return {
    handlePointerEnter,
    handlePointerLeave,
    materialRef,
    notifyClick,
  }
}
