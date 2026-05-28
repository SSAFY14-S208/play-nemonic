'use client'

import { useEffect, useRef, useState } from 'react'

import {
  connectRelaySocketForCurrentUser,
  disconnectRelaySocket,
  sendRelayPing,
  type RelaySocketStatus,
} from '@/shared/libs'
import { useUserStore } from '@/shared/stores'

import { dispatchRelaySocketEvent } from './relaySocketDispatcher'
import type { RelayEventHandlers } from './relaySocketTypes'
export type { RelayEventHandler, RelayEventHandlers } from './relaySocketTypes'

const PING_INTERVAL_MS = 25_000

interface UseRelaySocketArgs {
  roomCode: string | null | undefined
  handlers: RelayEventHandlers
  enabled?: boolean
}

interface UseRelaySocketReturn {
  status: RelaySocketStatus
  isConnected: boolean
  isReconnecting: boolean
}

export const useRelaySocket = ({
  roomCode,
  handlers,
  enabled = true,
}: UseRelaySocketArgs): UseRelaySocketReturn => {
  const userUuid = useUserStore((state) => state.userUuid)
  const [status, setStatus] = useState<RelaySocketStatus>('idle')
  const handlersRef = useRef<RelayEventHandlers>(handlers)

  useEffect(() => {
    handlersRef.current = handlers
  })

  useEffect(() => {
    if (!enabled || !roomCode || !userUuid) return

    const connected = connectRelaySocketForCurrentUser({
      roomCode,
      onEvent: (event) => dispatchRelaySocketEvent(handlersRef.current, event),
      onStatusChange: setStatus,
    })
    if (!connected) return

    const pingTimer = window.setInterval(sendRelayPing, PING_INTERVAL_MS)

    return () => {
      window.clearInterval(pingTimer)
      disconnectRelaySocket()
    }
  }, [enabled, roomCode, userUuid])

  return {
    status,
    isConnected: status === 'connected',
    isReconnecting: status === 'reconnecting',
  }
}
