import { useCallback, useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { useThree } from '@react-three/fiber'
import { HUB_GAMES } from '@/shared/constants'
import { useHubGameStore, useHubRoomStore } from '@/shared/stores'
import { trackHubInvalidate } from '@/shared/utils'

export type MonitorGameAction = 'previous' | 'next' | 'start'

function isKeyboardInputTarget(target: EventTarget | null) {
  if (!(target instanceof HTMLElement)) return false

  const tagName = target.tagName.toLowerCase()
  return (
    target.isContentEditable ||
    tagName === 'input' ||
    tagName === 'textarea' ||
    tagName === 'select'
  )
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

  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (isKeyboardInputTarget(event.target)) return

      if (event.key === 'ArrowLeft') {
        event.preventDefault()
        selectPreviousGame()
        trackHubInvalidate('monitor.keyboard.previous')
        invalidate()
        return
      }

      if (event.key === 'ArrowRight') {
        event.preventDefault()
        selectNextGame()
        trackHubInvalidate('monitor.keyboard.next')
        invalidate()
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
  }, [invalidate, selectNextGame, selectPreviousGame, startSelectedGame])

  return {
    focusMonitor,
    gameCount: HUB_GAMES.length,
    selectedGame,
    selectedGameIndex,
    selectNextGame,
    selectPreviousGame,
    startSelectedGame,
  }
}
