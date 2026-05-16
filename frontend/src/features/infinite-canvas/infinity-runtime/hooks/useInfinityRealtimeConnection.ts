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
  canvasId: string | null
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
  canvasId,
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

  const startPingTimer = useCallback((client: Client, activeCanvasId: string) => {
    if (pingTimerRef.current !== null) {
      window.clearInterval(pingTimerRef.current)
    }

    pingTimerRef.current = window.setInterval(() => {
      if (!client.connected) return
      client.publish({
        destination: `/app/infinite-canvas/canvases/${activeCanvasId}/ping`,
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
    if (!enabled || !canvasId || !userUuid) {
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
        canvasId,
        'Anonymous-User-UUID': userUuid,
      },
      reconnectDelay: RECONNECT_DELAY_MS,
      onConnect: () => {
        setConnectionStatus('connected')
        client.subscribe(`/topic/infinite-canvas/canvases/${canvasId}`, handleMessage)
        client.subscribe(`/user/queue/infinite-canvas/canvases/${canvasId}`, handleMessage)
        startPingTimer(client, canvasId)
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
  }, [canvasId, clearPingTimer, enabled, handleMessage, startPingTimer, userUuid])

  const sendOperations = useCallback(
    (payload: InfiniteCanvasOpsRequest) => {
      if (!canvasId) return false
      return publish(`/app/infinite-canvas/canvases/${canvasId}/ops`, payload)
    },
    [canvasId, publish],
  )

  const sendCursor = useCallback(
    (payload: InfiniteCanvasCursorRequest) => {
      if (!canvasId) return false
      return publish(`/app/infinite-canvas/canvases/${canvasId}/cursor`, payload)
    },
    [canvasId, publish],
  )

  const acquireLock = useCallback(
    (elementId: string) => {
      if (!canvasId) return false
      return publish(`/app/infinite-canvas/canvases/${canvasId}/locks/acquire`, { elementId })
    },
    [canvasId, publish],
  )

  const releaseLock = useCallback(
    (elementId: string) => {
      if (!canvasId) return false
      return publish(`/app/infinite-canvas/canvases/${canvasId}/locks/release`, { elementId })
    },
    [canvasId, publish],
  )

  return {
    connectionStatus,
    sendOperations,
    sendCursor,
    acquireLock,
    releaseLock,
  } as const
}
