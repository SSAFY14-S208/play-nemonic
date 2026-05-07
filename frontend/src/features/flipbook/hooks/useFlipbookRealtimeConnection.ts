'use client'

import { useCallback, useEffect, useRef } from 'react'
import { runtime } from '@/shared/config'
import {
  createJsonWebSocketTransport,
  type JsonWebSocketTransport,
} from '@/shared/libs'
import type { FlipbookClientMessage, FlipbookServerMessage } from '@/shared/types'
import { useFlipbookRealtimeStore } from '../flipbookRealtimeStore'

const DEFAULT_FLIPBOOK_WEBSOCKET_PATH = 'ws'
const NORMAL_CLOSE_CODE = 1000
const DISABLED_CLOSE_REASON = 'flipbook realtime disabled'
const UNMOUNT_CLOSE_REASON = 'flipbook realtime unmounted'

export function useFlipbookRealtimeConnection({ enabled }: { enabled: boolean }) {
  const connectionStatus = useFlipbookRealtimeStore((state) => state.connectionStatus)
  const outboundMessages = useFlipbookRealtimeStore((state) => state.outboundMessages)
  const setConnectionStatus = useFlipbookRealtimeStore((state) => state.setConnectionStatus)
  const markClientMessageSent = useFlipbookRealtimeStore((state) => state.markClientMessageSent)
  const applyServerMessage = useFlipbookRealtimeStore((state) => state.applyServerMessage)
  const transportRef =
    useRef<JsonWebSocketTransport<FlipbookClientMessage, FlipbookServerMessage> | null>(null)
  const sentRequestIdsRef = useRef<Set<string>>(new Set())
  const isClosingIntentionallyRef = useRef(false)

  const getTransport = useCallback(() => {
    if (!transportRef.current) {
      transportRef.current = createJsonWebSocketTransport<
        FlipbookClientMessage,
        FlipbookServerMessage
      >({
        path: runtime.websocketUrl ? '' : DEFAULT_FLIPBOOK_WEBSOCKET_PATH,
      })
    }

    return transportRef.current
  }, [])

  const connect = useCallback(() => {
    const transport = getTransport()

    if (transport.getReadyState() === WebSocket.OPEN) {
      return
    }

    if (transport.getReadyState() === WebSocket.CONNECTING) {
      return
    }

    isClosingIntentionallyRef.current = false
    setConnectionStatus(connectionStatus === 'disconnected' ? 'reconnecting' : 'connecting')
    transport.connect({
      onOpen: () => {
        setConnectionStatus('connected')
      },
      onMessage: (message) => {
        applyServerMessage(message)
      },
      onInvalidMessage: () => {
        setConnectionStatus('rejected')
      },
      onError: () => {
        setConnectionStatus('rejected')
      },
      onClose: () => {
        if (isClosingIntentionallyRef.current) {
          setConnectionStatus('idle')
          return
        }

        setConnectionStatus('disconnected')
      },
    })
  }, [applyServerMessage, connectionStatus, getTransport, setConnectionStatus])

  useEffect(() => {
    let cancelled = false

    ;(async () => {
      if (cancelled) return

      if (!enabled) {
        isClosingIntentionallyRef.current = true
        sentRequestIdsRef.current.clear()
        transportRef.current?.close(NORMAL_CLOSE_CODE, DISABLED_CLOSE_REASON)
        setConnectionStatus('idle')
        return
      }

      connect()
    })()

    return () => {
      cancelled = true
    }
  }, [connect, enabled, setConnectionStatus])

  useEffect(() => {
    let cancelled = false

    ;(async () => {
      if (cancelled || !enabled || outboundMessages.length === 0) return

      const transport = getTransport()
      if (transport.getReadyState() !== WebSocket.OPEN || connectionStatus !== 'connected') {
        connect()
        return
      }

      for (const message of outboundMessages) {
        if (cancelled || sentRequestIdsRef.current.has(message.requestId)) continue

        const sent = transport.send(message)
        if (!sent) {
          setConnectionStatus('reconnecting')
          connect()
          return
        }

        sentRequestIdsRef.current.add(message.requestId)
        markClientMessageSent(message.requestId)
      }
    })()

    return () => {
      cancelled = true
    }
  }, [
    connect,
    connectionStatus,
    enabled,
    getTransport,
    markClientMessageSent,
    outboundMessages,
    setConnectionStatus,
  ])

  useEffect(() => {
    const sentRequestIds = sentRequestIdsRef.current

    return () => {
      isClosingIntentionallyRef.current = true
      transportRef.current?.close(NORMAL_CLOSE_CODE, UNMOUNT_CLOSE_REASON)
      transportRef.current = null
      sentRequestIds.clear()
    }
  }, [])
}
