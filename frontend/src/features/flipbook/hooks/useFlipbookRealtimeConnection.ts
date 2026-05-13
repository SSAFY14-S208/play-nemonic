'use client'

import { useCallback, useEffect, useRef, useState } from 'react'
import { Client, type IMessage } from '@stomp/stompjs'
import { runtime } from '@/shared/config'
import { useUserStore } from '@/shared/stores'
import type { FlipbookConnectionStatus, FlipbookRealtimeEvent } from '@/shared/types'

const FLIPBOOK_WEBSOCKET_ENDPOINT = '/ws/flipbook'
const RECONNECT_DELAY_MS = 3000
const HEARTBEAT_INTERVAL_MS = 5000

interface UseFlipbookRealtimeConnectionOptions {
  enabled: boolean
  roomCode: string | null
  onEvent: (event: FlipbookRealtimeEvent) => void
}

const resolveFlipbookBrokerUrl = () => {
  const rawUrl = runtime.websocketUrl || runtime.apiUrl
  if (!rawUrl) {
    throw new Error('NEXT_PUBLIC_WEBSOCKET_URL / NEXT_PUBLIC_API_URL 둘 다 비어있습니다.')
  }

  const websocketUrl = rawUrl.replace(/^http(s?):\/\//i, (match, secure: string) =>
    secure ? 'wss://' : 'ws://',
  )
  const normalizedWebsocketUrl = websocketUrl.replace(/\/$/, '')

  if (normalizedWebsocketUrl.endsWith(FLIPBOOK_WEBSOCKET_ENDPOINT)) {
    return normalizedWebsocketUrl
  }

  return `${normalizedWebsocketUrl}${FLIPBOOK_WEBSOCKET_ENDPOINT}`
}

export function useFlipbookRealtimeConnection({
  enabled,
  roomCode,
  onEvent,
}: UseFlipbookRealtimeConnectionOptions) {
  const userUuid = useUserStore((state) => state.userUuid)
  const [connectionStatus, setConnectionStatus] = useState<FlipbookConnectionStatus>('idle')
  const stompClientRef = useRef<Client | null>(null)
  const pingTimerRef = useRef<number | null>(null)
  const onEventRef = useRef(onEvent)

  useEffect(() => {
    onEventRef.current = onEvent
  }, [onEvent])

  const clearPingTimer = useCallback(() => {
    if (pingTimerRef.current === null) return
    window.clearInterval(pingTimerRef.current)
    pingTimerRef.current = null
  }, [])

  const startPingTimer = useCallback((client: Client, activeRoomCode: string) => {
    if (pingTimerRef.current !== null) {
      window.clearInterval(pingTimerRef.current)
    }

    pingTimerRef.current = window.setInterval(() => {
      if (!client.connected) return
      client.publish({
        destination: `/app/flipbook/rooms/${activeRoomCode}/ping`,
        body: '{}',
      })
    }, HEARTBEAT_INTERVAL_MS)
  }, [])

  const handleMessage = useCallback((message: IMessage) => {
    try {
      const event = JSON.parse(message.body) as FlipbookRealtimeEvent
      onEventRef.current(event)
    } catch {
      setConnectionStatus('rejected')
    }
  }, [])

  useEffect(() => {
    if (!enabled || !roomCode || !userUuid) {
      clearPingTimer()
      void stompClientRef.current?.deactivate()
      stompClientRef.current = null
      void Promise.resolve().then(() => setConnectionStatus('idle'))
      return
    }

    const brokerURL = resolveFlipbookBrokerUrl()
    const client = new Client({
      brokerURL,
      connectHeaders: {
        roomCode,
        'Anonymous-User-UUID': userUuid,
      },
      reconnectDelay: RECONNECT_DELAY_MS,
      onConnect: () => {
        setConnectionStatus('connected')
        client.subscribe(`/topic/flipbook/rooms/${roomCode}`, handleMessage)
        client.subscribe(`/user/queue/flipbook/rooms/${roomCode}`, handleMessage)
        startPingTimer(client, roomCode)
      },
      onStompError: () => {
        setConnectionStatus('rejected')
      },
      onWebSocketClose: () => {
        clearPingTimer()
        setConnectionStatus(client.active ? 'reconnecting' : 'disconnected')
      },
      onWebSocketError: () => {
        setConnectionStatus('rejected')
      },
    })

    void Promise.resolve().then(() => setConnectionStatus('connecting'))
    stompClientRef.current = client
    client.activate()

    return () => {
      clearPingTimer()
      void client.deactivate()
      if (stompClientRef.current === client) {
        stompClientRef.current = null
      }
    }
  }, [clearPingTimer, enabled, handleMessage, roomCode, startPingTimer, userUuid])

  return {
    connectionStatus,
  }
}
