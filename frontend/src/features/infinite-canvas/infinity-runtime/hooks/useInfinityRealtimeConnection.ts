'use client'

import { useCallback, useEffect, useRef, useState } from 'react'
import { Client, type IMessage } from '@stomp/stompjs'
import { runtime } from '@/shared/config'
import { useUserStore } from '@/shared/stores'
import type {
  InfiniteCanvasConnectionStatus,
  InfiniteCanvasCursorRequest,
  InfiniteCanvasOpsRequest,
  InfiniteCanvasRealtimeEvent,
} from '@/shared/types'

const INFINITE_CANVAS_WEBSOCKET_ENDPOINT = '/ws/infinite-canvas'
const RECONNECT_DELAY_MS = 3000
const HEARTBEAT_INTERVAL_MS = 5000

interface UseInfinityRealtimeConnectionOptions {
  enabled: boolean
  roomCode: string | null
  onEvent: (event: InfiniteCanvasRealtimeEvent) => void
}

const resolveInfiniteCanvasBrokerUrl = () => {
  const rawUrl = runtime.websocketUrl || runtime.apiUrl
  if (!rawUrl) {
    throw new Error('NEXT_PUBLIC_WEBSOCKET_URL / NEXT_PUBLIC_API_URL 둘 다 비어있습니다.')
  }

  const websocketUrl = rawUrl.replace(/^http(s?):\/\//i, (match, secure: string) =>
    secure ? 'wss://' : 'ws://',
  )
  const normalizedWebsocketUrl = websocketUrl.replace(/\/$/, '')

  if (normalizedWebsocketUrl.endsWith(INFINITE_CANVAS_WEBSOCKET_ENDPOINT)) {
    return normalizedWebsocketUrl
  }

  return `${normalizedWebsocketUrl}${INFINITE_CANVAS_WEBSOCKET_ENDPOINT}`
}

export function useInfinityRealtimeConnection({
  enabled,
  roomCode,
  onEvent,
}: UseInfinityRealtimeConnectionOptions) {
  const userUuid = useUserStore((state) => state.userUuid)
  const [connectionStatus, setConnectionStatus] =
    useState<InfiniteCanvasConnectionStatus>('idle')
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

  const publish = useCallback((destination: string, body: unknown) => {
    const client = stompClientRef.current
    if (!client?.connected) return false

    client.publish({
      destination,
      body: JSON.stringify(body ?? {}),
    })
    return true
  }, [])

  const startPingTimer = useCallback((client: Client, activeRoomCode: string) => {
    if (pingTimerRef.current !== null) {
      window.clearInterval(pingTimerRef.current)
    }

    pingTimerRef.current = window.setInterval(() => {
      if (!client.connected) return
      client.publish({
        destination: `/app/infinite-canvas/canvases/${activeRoomCode}/ping`,
        body: '{}',
      })
    }, HEARTBEAT_INTERVAL_MS)
  }, [])

  const handleMessage = useCallback((message: IMessage) => {
    try {
      const event = JSON.parse(message.body) as InfiniteCanvasRealtimeEvent
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

    const brokerURL = resolveInfiniteCanvasBrokerUrl()
    const client = new Client({
      brokerURL,
      connectHeaders: {
        roomCode,
        'Anonymous-User-UUID': userUuid,
      },
      reconnectDelay: RECONNECT_DELAY_MS,
      onConnect: () => {
        setConnectionStatus('connected')
        client.subscribe(`/topic/infinite-canvas/canvases/${roomCode}`, handleMessage)
        client.subscribe(`/user/queue/infinite-canvas/canvases/${roomCode}`, handleMessage)
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
  }, [roomCode, clearPingTimer, enabled, handleMessage, startPingTimer, userUuid])

  const sendOperations = useCallback(
    (payload: InfiniteCanvasOpsRequest) => {
      if (!roomCode) return false
      return publish(`/app/infinite-canvas/canvases/${roomCode}/ops`, payload)
    },
    [roomCode, publish],
  )

  const sendCursor = useCallback(
    (payload: InfiniteCanvasCursorRequest) => {
      if (!roomCode) return false
      return publish(`/app/infinite-canvas/canvases/${roomCode}/cursor`, payload)
    },
    [roomCode, publish],
  )

  const requestStateSync = useCallback(() => {
    if (!roomCode) return false
    return publish(`/app/infinite-canvas/canvases/${roomCode}/sync`, {})
  }, [roomCode, publish])

  const acquireLock = useCallback(
    (elementId: string) => {
      if (!roomCode) return false
      return publish(`/app/infinite-canvas/canvases/${roomCode}/locks/acquire`, { elementId })
    },
    [roomCode, publish],
  )

  const releaseLock = useCallback(
    (elementId: string) => {
      if (!roomCode) return false
      return publish(`/app/infinite-canvas/canvases/${roomCode}/locks/release`, { elementId })
    },
    [roomCode, publish],
  )

  return {
    connectionStatus,
    sendOperations,
    sendCursor,
    requestStateSync,
    acquireLock,
    releaseLock,
  } as const
}
