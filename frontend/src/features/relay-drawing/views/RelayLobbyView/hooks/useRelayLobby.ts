'use client'

import { useRouter } from 'next/navigation'

import { completeFunnelStep } from '@/shared/libs'
import { useUserStore } from '@/shared/stores'

import { useRelayDrawingStore } from '@/features/relay-drawing/stores'

import { canStartRelayGame } from '../relayLobbyUtils'
import { useRelayLobbyKick } from './useRelayLobbyKick'
import { useRelayLobbySettings } from './useRelayLobbySettings'

interface UseRelayLobbyReturn {
  isHost: boolean
  canStartGame: boolean
  isStarting: boolean
  settingsError: string | null
  isUpdatingSettings: boolean
  kickingTargetUuid: string | null
  kickError: string | null
  startGame: () => void
  changeTimeLimit: (seconds: number) => void
  kickParticipant: (targetUserUuid: string) => void
  clearErrors: () => void
  leaveRoom: () => void
}

export function useRelayLobby(): UseRelayLobbyReturn {
  const router = useRouter()
  const userUuid = useUserStore((state) => state.userUuid)
  const roomCode = useRelayDrawingStore((state) => state.roomCode)
  const hostUserUuid = useRelayDrawingStore((state) => state.hostUserUuid)
  const participants = useRelayDrawingStore((state) => state.participants)
  const minParticipants = useRelayDrawingStore((state) => state.minParticipants)
  const clearRoom = useRelayDrawingStore((state) => state.clearRoom)
  const gameStartPhase = useRelayDrawingStore((state) => state.gameStartPhase)
  const setGameStartPhase = useRelayDrawingStore((state) => state.setGameStartPhase)

  const isHost = userUuid !== null && userUuid === hostUserUuid
  const isStarting = gameStartPhase === 'animating'
  const canStartGame = canStartRelayGame({
    isHost,
    isStarting,
    participants,
    minParticipants,
  })

  const {
    settingsError,
    isUpdatingSettings,
    changeTimeLimit,
    clearSettingsError,
  } = useRelayLobbySettings({ roomCode, isHost })
  const { kickingTargetUuid, kickError, kickParticipant, clearKickError } =
    useRelayLobbyKick({
      roomCode,
      isHost,
      currentUserUuid: userUuid,
    })

  const startGame = () => {
    if (!roomCode || !canStartGame) return

    completeFunnelStep('lobby', 3, {
      content_type: 'relay',
      room_id: roomCode,
      participant_count: participants.length,
    })
    setGameStartPhase('animating')
  }

  const clearErrors = () => {
    clearSettingsError()
    clearKickError()
  }

  const leaveRoom = () => {
    clearRoom()
    router.push('/relay-drawing')
  }

  return {
    isHost,
    canStartGame,
    isStarting,
    settingsError,
    isUpdatingSettings,
    kickingTargetUuid,
    kickError,
    startGame,
    changeTimeLimit,
    kickParticipant,
    clearErrors,
    leaveRoom,
  }
}
