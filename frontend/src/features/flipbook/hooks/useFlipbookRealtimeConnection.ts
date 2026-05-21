'use client'

import { useEffect, useRef, useState } from 'react'
import { Client, type IMessage, type StompSubscription } from '@stomp/stompjs'
import { runtime } from '@/shared/config'
import { useUserStore } from '@/shared/stores'
import type { FlipbookConnectionStatus, FlipbookRealtimeEvent } from '@/shared/types'

const FLIPBOOK_WEBSOCKET_ENDPOINT = '/ws/flipbook'
const RECONNECT_DELAY_MS = 3000
const HEARTBEAT_INTERVAL_MS = 5000
const STOMP_HEARTBEAT_INTERVAL_MS = 10000

type FlipbookEventListener = (event: FlipbookRealtimeEvent) => void
type FlipbookConnectionStatusListener = (status: FlipbookConnectionStatus) => void

interface UseFlipbookRealtimeConnectionOptions {
  enabled: boolean
  roomCode: string | null
  onEvent: FlipbookEventListener
}

interface ConnectFlipbookSocketOptions {
  roomCode: string
  userUuid: string
  onEvent: FlipbookEventListener
  onStatusChange?: FlipbookConnectionStatusListener
}

let activeClient: Client | null = null
let activeRoomCode: string | null = null
let activeStatus: FlipbookConnectionStatus = 'idle'
let activeEventListener: FlipbookEventListener | null = null
let activeStatusListener: FlipbookConnectionStatusListener | null = null
let topicSubscription: StompSubscription | null = null
let userQueueSubscription: StompSubscription | null = null
let pingTimer: number | null = null

const topicDestination = (roomCode: string) => `/topic/flipbook/rooms/${roomCode}`
const userQueueDestination = (roomCode: string) => `/user/queue/flipbook/rooms/${roomCode}`
const pingDestination = (roomCode: string) => `/app/flipbook/rooms/${roomCode}/ping`

const setActiveStatus = (nextStatus: FlipbookConnectionStatus) => {
  activeStatus = nextStatus
  activeStatusListener?.(nextStatus)
}

const resolveFlipbookBrokerUrl = () => {
  const rawUrl = runtime.websocketUrl || runtime.apiUrl
  if (!rawUrl) {
    throw new Error('NEXT_PUBLIC_WEBSOCKET_URL / NEXT_PUBLIC_API_URL 둘 다 비어있습니다.')
  }

  const websocketUrl = rawUrl.replace(/^https?:\/\//i, (protocol) =>
    protocol.toLowerCase().startsWith('https') ? 'wss://' : 'ws://',
  )
  const normalizedWebsocketUrl = websocketUrl.replace(/\/$/, '')

  if (normalizedWebsocketUrl.endsWith(FLIPBOOK_WEBSOCKET_ENDPOINT)) {
    return normalizedWebsocketUrl
  }

  return `${normalizedWebsocketUrl}${FLIPBOOK_WEBSOCKET_ENDPOINT}`
}

const clearPingTimer = () => {
  if (pingTimer === null) return

  window.clearInterval(pingTimer)
  pingTimer = null
}

const startPingTimer = (client: Client, roomCode: string) => {
  clearPingTimer()
  pingTimer = window.setInterval(() => {
    if (activeClient !== client || activeRoomCode !== roomCode || !client.connected) return

    client.publish({
      destination: pingDestination(roomCode),
      body: '{}',
    })
  }, HEARTBEAT_INTERVAL_MS)
}

const unsubscribeActiveSubscriptions = () => {
  topicSubscription?.unsubscribe()
  userQueueSubscription?.unsubscribe()
  topicSubscription = null
  userQueueSubscription = null
}

const parseFlipbookEvent = (message: IMessage): FlipbookRealtimeEvent | null => {
  try {
    return JSON.parse(message.body) as FlipbookRealtimeEvent
  } catch {
    setActiveStatus('rejected')
    return null
  }
}

const bindActiveSubscriptions = (roomCode: string) => {
  if (!activeClient?.connected || !activeEventListener) return

  unsubscribeActiveSubscriptions()
  topicSubscription = activeClient.subscribe(topicDestination(roomCode), (message) => {
    const event = parseFlipbookEvent(message)
    if (event) activeEventListener?.(event)
  })
  userQueueSubscription = activeClient.subscribe(userQueueDestination(roomCode), (message) => {
    const event = parseFlipbookEvent(message)
    if (event) activeEventListener?.(event)
  })
}

const disconnectFlipbookSocket = () => {
  setActiveStatus('disconnected')
  unsubscribeActiveSubscriptions()
  clearPingTimer()

  const client = activeClient
  activeClient = null
  activeRoomCode = null
  activeEventListener = null
  activeStatusListener = null

  void client?.deactivate()
}

const connectFlipbookSocket = ({
  roomCode,
  userUuid,
  onEvent,
  onStatusChange,
}: ConnectFlipbookSocketOptions) => {
  if (activeClient && activeRoomCode === roomCode && activeClient.active) {
    activeEventListener = onEvent
    activeStatusListener = onStatusChange ?? null
    bindActiveSubscriptions(roomCode)
    onStatusChange?.(activeStatus)
    return
  }

  if (activeClient) {
    disconnectFlipbookSocket()
  }

  activeRoomCode = roomCode
  activeEventListener = onEvent
  activeStatusListener = onStatusChange ?? null
  setActiveStatus('connecting')

  const client = new Client({
    brokerURL: resolveFlipbookBrokerUrl(),
    connectHeaders: {
      roomCode,
      'Anonymous-User-UUID': userUuid,
    },
    reconnectDelay: RECONNECT_DELAY_MS,
    heartbeatIncoming: STOMP_HEARTBEAT_INTERVAL_MS,
    heartbeatOutgoing: STOMP_HEARTBEAT_INTERVAL_MS,
    onConnect: () => {
      if (activeClient !== client || activeRoomCode !== roomCode) return

      setActiveStatus('connected')
      bindActiveSubscriptions(roomCode)
      startPingTimer(client, roomCode)
    },
    onStompError: () => {
      if (activeClient !== client) return

      setActiveStatus('rejected')
    },
    onWebSocketClose: () => {
      if (activeClient !== client) return

      clearPingTimer()
      setActiveStatus(client.active ? 'reconnecting' : 'disconnected')
    },
    onWebSocketError: () => {
      if (activeClient !== client) return

      setActiveStatus('rejected')
    },
  })

  activeClient = client
  client.activate()
}

const connectFlipbookSocketForCurrentUser = (
  options: Omit<ConnectFlipbookSocketOptions, 'userUuid'>,
) => {
  const userUuid = useUserStore.getState().userUuid
  if (!userUuid) return false

  connectFlipbookSocket({ ...options, userUuid })
  return true
}

export function useFlipbookRealtimeConnection({
  enabled,
  roomCode,
  onEvent,
}: UseFlipbookRealtimeConnectionOptions) {
  const userUuid = useUserStore((state) => state.userUuid)
  const [connectionStatus, setConnectionStatus] = useState<FlipbookConnectionStatus>('idle')
  const onEventRef = useRef(onEvent)

  useEffect(() => {
    onEventRef.current = onEvent
  }, [onEvent])

  useEffect(() => {
    if (!enabled || !roomCode || !userUuid) {
      if (activeClient) {
        disconnectFlipbookSocket()
      }
      void Promise.resolve().then(() => setConnectionStatus('idle'))
      return
    }

    const connected = connectFlipbookSocketForCurrentUser({
      roomCode,
      onEvent: (event) => onEventRef.current(event),
      onStatusChange: (nextStatus) => {
        void Promise.resolve().then(() => setConnectionStatus(nextStatus))
      },
    })
    if (!connected) return

    return () => {
      if (activeRoomCode === roomCode) {
        disconnectFlipbookSocket()
      }
    }
  }, [enabled, roomCode, userUuid])

  return {
    connectionStatus,
  }
}
