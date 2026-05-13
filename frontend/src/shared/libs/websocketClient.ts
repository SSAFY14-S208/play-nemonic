import { runtime } from '@/shared/config'

export type JsonPrimitive = string | number | boolean | null
export type JsonObject = { [key: string]: JsonValue }
export type JsonArray = JsonValue[]
export type JsonValue = JsonPrimitive | JsonObject | JsonArray

export type JsonWebSocketState = 0 | 1 | 2 | 3

export type JsonWebSocketCallbacks<TIncomingMessage = JsonValue> = {
  onOpen?: (event: Event) => void
  onMessage?: (message: TIncomingMessage, event: MessageEvent<string>) => void
  onInvalidMessage?: (event: MessageEvent, error: unknown) => void
  onError?: (event: Event) => void
  onClose?: (event: CloseEvent) => void
}

export type JsonWebSocketTransportOptions<TIncomingMessage = JsonValue> = {
  url?: string
  path?: string
  protocols?: string | string[]
  callbacks?: JsonWebSocketCallbacks<TIncomingMessage>
}

export type JsonWebSocketTransport<
  TOutgoingMessage = JsonValue,
  TIncomingMessage = JsonValue,
> = {
  connect: (callbacks?: JsonWebSocketCallbacks<TIncomingMessage>) => WebSocket
  send: (message: TOutgoingMessage) => boolean
  close: (code?: number, reason?: string) => void
  getReadyState: () => JsonWebSocketState | null
  getSocket: () => WebSocket | null
  getUrl: () => string
}

const WEBSOCKET_PROTOCOL_BY_HTTP_PROTOCOL: Record<string, string> = {
  'http:': 'ws:',
  'https:': 'wss:',
}

const isBrowserLocationAvailable = () => typeof window !== 'undefined' && Boolean(window.location)

const getBrowserOrigin = () => {
  if (!isBrowserLocationAvailable()) {
    return undefined
  }

  return window.location.origin
}

const joinUrlPath = (basePath: string, path: string) => {
  if (!path) {
    return basePath
  }

  const normalizedBasePath = basePath.endsWith('/') ? basePath.slice(0, -1) : basePath
  const normalizedPath = path.startsWith('/') ? path : `/${path}`
  return `${normalizedBasePath}${normalizedPath}`
}

const toWebSocketUrl = (baseUrl: string, path = '') => {
  const browserOrigin = getBrowserOrigin()
  const url = new URL(baseUrl, browserOrigin)
  const websocketProtocol = WEBSOCKET_PROTOCOL_BY_HTTP_PROTOCOL[url.protocol] ?? url.protocol

  if (websocketProtocol !== 'ws:' && websocketProtocol !== 'wss:') {
    throw new Error(`Unsupported WebSocket URL protocol: ${url.protocol}`)
  }

  url.protocol = websocketProtocol
  url.pathname = joinUrlPath(url.pathname, path)

  return url.toString()
}

export const resolveWebSocketUrl = (path = '', baseUrl = runtime.websocketUrl || runtime.apiUrl) => {
  if (baseUrl) {
    return toWebSocketUrl(baseUrl, path)
  }

  const browserOrigin = getBrowserOrigin()
  if (browserOrigin) {
    return toWebSocketUrl(browserOrigin, path)
  }

  throw new Error('WebSocket URL is not configured')
}

export const createJsonWebSocketTransport = <
  TOutgoingMessage = JsonValue,
  TIncomingMessage = JsonValue,
>({
  url,
  path = '',
  protocols,
  callbacks,
}: JsonWebSocketTransportOptions<TIncomingMessage> = {}): JsonWebSocketTransport<
  TOutgoingMessage,
  TIncomingMessage
> => {
  let socket: WebSocket | null = null
  const websocketUrl = url ? toWebSocketUrl(url, path) : resolveWebSocketUrl(path)

  const bindCallbacks = (activeSocket: WebSocket, activeCallbacks?: JsonWebSocketCallbacks<TIncomingMessage>) => {
    activeSocket.onopen = (event) => {
      activeCallbacks?.onOpen?.(event)
    }

    activeSocket.onmessage = (event) => {
      if (typeof event.data !== 'string') {
        activeCallbacks?.onInvalidMessage?.(event, new Error('WebSocket message data is not a string'))
        return
      }

      try {
        const parsedMessage = JSON.parse(event.data) as TIncomingMessage
        activeCallbacks?.onMessage?.(parsedMessage, event as MessageEvent<string>)
      } catch (error) {
        activeCallbacks?.onInvalidMessage?.(event, error)
      }
    }

    activeSocket.onerror = (event) => {
      activeCallbacks?.onError?.(event)
    }

    activeSocket.onclose = (event) => {
      socket = null
      activeCallbacks?.onClose?.(event)
    }
  }

  return {
    connect: (connectCallbacks) => {
      if (socket && socket.readyState !== WebSocket.CLOSED) {
        return socket
      }

      socket = new WebSocket(websocketUrl, protocols)
      bindCallbacks(socket, connectCallbacks ?? callbacks)
      return socket
    },
    send: (message) => {
      if (!socket || socket.readyState !== WebSocket.OPEN) {
        return false
      }

      socket.send(JSON.stringify(message))
      return true
    },
    close: (code, reason) => {
      socket?.close(code, reason)
    },
    getReadyState: () => (socket?.readyState as JsonWebSocketState | undefined) ?? null,
    getSocket: () => socket,
    getUrl: () => websocketUrl,
  }
}
