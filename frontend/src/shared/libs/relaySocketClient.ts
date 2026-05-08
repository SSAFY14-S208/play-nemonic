import { Client, type IFrame, type IMessage, type StompSubscription } from '@stomp/stompjs'

import { runtime } from '@/shared/config'
import { useUserStore } from '@/shared/stores'

import type { RelayWsEvent } from '@/shared/types'

// 가이드 §22
//   endpoint:                 /ws/relay
//   application prefix:       /app
//   topic prefix:             /topic
//   user queue prefix:        /user/queue
//   broadcast destination:    /topic/relay/rooms/{roomCode}
//   personal destination:     /user/queue/relay/rooms/{roomCode}
//   ping (client→server):     /app/relay/rooms/{roomCode}/ping
const RELAY_ENDPOINT = '/ws/relay'

// 가이드 §22 — CONNECT 시 native header로 룸 식별 + 사용자 식별을 함께 보낸다.
// 헤더는 서버가 세션-룸 귀속 / 강퇴·중복세션 판단에 사용한다.
// StompHeaders가 string 인덱스 시그니처를 요구하므로 Record로 둔다.
type ConnectHeaders = Record<string, string> & {
  roomCode: string
  'Anonymous-User-UUID': string
}

export type RelaySocketStatus =
  | 'idle' // 아직 connect() 안 부른 상태
  | 'connecting'
  | 'connected'
  | 'reconnecting' // STOMP 자동 재연결 대기 중 (initial backoff 후 재시도)
  | 'disconnected' // 사용자가 명시적으로 disconnect()를 부른 종료 상태

export type RelaySocketStatusListener = (status: RelaySocketStatus) => void

export type RelayEventListener = (event: RelayWsEvent) => void

interface ConnectArgs {
  roomCode: string
  userUuid: string
  onEvent: RelayEventListener
  onStatusChange?: RelaySocketStatusListener
}

// STOMP destination을 한 곳에서 만들어 두면 핸들러가 토픽 vs 큐를 헷갈릴 일이 없다.
const topicDestination = (roomCode: string) => `/topic/relay/rooms/${roomCode}`
const userQueueDestination = (roomCode: string) => `/user/queue/relay/rooms/${roomCode}`
const pingDestination = (roomCode: string) => `/app/relay/rooms/${roomCode}/ping`

// "왜 싱글턴인가": 한 사용자가 동시에 두 룸에 들어가는 시나리오는 가이드 §24의
// DUPLICATE_SESSION_CLOSED 정책이 막고 있다 — 같은 roomCode+UUID로 새 연결이
// 들어오면 기존 세션을 잘라낸다. 그래서 페이지/feature가 각자 STOMP Client를
// 만들면 같은 사용자가 두 클라이언트를 동시에 띄우는 셈이 되어 자기 자신을
// 끊는 사고가 난다. 이 모듈이 단일 진입점을 보장한다.
let activeClient: Client | null = null
let activeRoomCode: string | null = null
let activeStatus: RelaySocketStatus = 'idle'
let topicSubscription: StompSubscription | null = null
let userQueueSubscription: StompSubscription | null = null

const setStatus = (next: RelaySocketStatus, listener?: RelaySocketStatusListener) => {
  activeStatus = next
  listener?.(next)
}

// STOMP 메시지 본문은 JSON 문자열. 가이드 §23의 envelope 구조로 파싱한다.
const parseEvent = (message: IMessage): RelayWsEvent | null => {
  try {
    return JSON.parse(message.body) as RelayWsEvent
  } catch (error) {
    if (runtime.isDev) console.error('[relaySocket] invalid event payload', error, message.body)
    return null
  }
}

// HTTP(S) → WS(S) 자동 변환. runtime.websocketUrl이 비어있으면 apiUrl로
// 폴백한다. native WebSocket은 ws:/wss: 스킴을 요구한다.
const resolveBrokerUrl = (): string => {
  const raw = runtime.websocketUrl || runtime.apiUrl
  if (!raw) {
    throw new Error('[relaySocket] NEXT_PUBLIC_WEBSOCKET_URL / NEXT_PUBLIC_API_URL 둘 다 비어있다')
  }
  const swapped = raw.replace(/^http(s?):\/\//i, (_, secure) => (secure ? 'wss://' : 'ws://'))
  return `${swapped.replace(/\/$/, '')}${RELAY_ENDPOINT}`
}

/**
 * 룸에 연결한다. 이미 다른 룸에 연결된 상태라면 먼저 끊고 재연결한다.
 *
 * 사용 측은 `useRelaySocket` 훅을 통해서만 호출하는 게 원칙이다 — 직접 호출도
 * 가능하지만 React 라이프사이클 정리는 호출자 책임이 된다.
 */
export const connectRelaySocket = ({ roomCode, userUuid, onEvent, onStatusChange }: ConnectArgs) => {
  // 같은 룸으로 재진입 — 기존 연결을 그대로 쓰고 콜백만 갈아끼운다.
  if (activeClient && activeRoomCode === roomCode && activeClient.active) {
    rebindEventListener(roomCode, onEvent)
    onStatusChange?.(activeStatus)
    return
  }

  // 다른 룸으로 전환 — 기존 연결을 끊는다.
  if (activeClient) disconnectRelaySocket()

  setStatus('connecting', onStatusChange)

  const headers: ConnectHeaders = {
    roomCode,
    'Anonymous-User-UUID': userUuid,
  }

  const client = new Client({
    brokerURL: resolveBrokerUrl(),
    connectHeaders: headers,
    // 자동 재연결: 5초 간격. STOMP는 끊김 시 onWebSocketClose → reconnect 사이클을 자동으로 돈다.
    // 가이드 §27의 10초 유예 정책과 정합 — 5초 간격이면 유예 안에 한 번 이상 시도된다.
    reconnectDelay: 5_000,
    // heartbeat: 클라/서버 양쪽이 살아있는지 10초 단위로 확인. ping destination과는 별개로
    // STOMP 프로토콜 레벨의 핑이라 무료다.
    heartbeatIncoming: 10_000,
    heartbeatOutgoing: 10_000,
    debug: runtime.isDev ? (msg: string) => console.debug('[relaySocket]', msg) : () => {},
  })

  client.onConnect = (frame: IFrame) => {
    if (runtime.isDev) console.debug('[relaySocket] connected', frame.headers)
    setStatus('connected', onStatusChange)

    topicSubscription = client.subscribe(topicDestination(roomCode), (message) => {
      const event = parseEvent(message)
      if (event) onEvent(event)
    })

    userQueueSubscription = client.subscribe(userQueueDestination(roomCode), (message) => {
      const event = parseEvent(message)
      if (event) onEvent(event)
    })
  }

  // STOMP-level error frame (서버가 명시적으로 ERROR 프레임을 내려보낸 경우).
  // 4xx 인증 거부, 잘못된 룸 코드 등이 여기로 떨어진다.
  client.onStompError = (frame: IFrame) => {
    if (runtime.isDev) console.error('[relaySocket] STOMP error', frame.headers, frame.body)
  }

  // 트랜스포트 단절 — STOMP가 reconnectDelay 후 재시도한다. 명시적 disconnect도
  // 여기를 거치므로, activeClient가 살아있을 때만 reconnecting으로 간다.
  client.onWebSocketClose = (event) => {
    if (runtime.isDev) console.debug('[relaySocket] socket closed', event.code, event.reason)
    if (activeClient === client && activeStatus !== 'disconnected') {
      setStatus('reconnecting', onStatusChange)
    }
  }

  client.onWebSocketError = (error) => {
    if (runtime.isDev) console.error('[relaySocket] socket error', error)
  }

  activeClient = client
  activeRoomCode = roomCode
  client.activate()
}

// 같은 룸으로 재진입한 경우 콜백만 갈아끼우는 헬퍼.
// onConnect 시점에 이미 등록한 subscription의 핸들러를 교체한다.
const rebindEventListener = (roomCode: string, onEvent: RelayEventListener) => {
  if (!activeClient) return
  topicSubscription?.unsubscribe()
  userQueueSubscription?.unsubscribe()
  topicSubscription = activeClient.subscribe(topicDestination(roomCode), (message) => {
    const event = parseEvent(message)
    if (event) onEvent(event)
  })
  userQueueSubscription = activeClient.subscribe(userQueueDestination(roomCode), (message) => {
    const event = parseEvent(message)
    if (event) onEvent(event)
  })
}

/**
 * 명시적 종료 — 페이지 이탈, 강퇴 수신, 중복세션 수신 시 호출.
 * 자동 재연결을 멈추기 위해 status를 'disconnected'로 먼저 박고 deactivate한다.
 */
export const disconnectRelaySocket = () => {
  setStatus('disconnected')
  topicSubscription?.unsubscribe()
  userQueueSubscription?.unsubscribe()
  topicSubscription = null
  userQueueSubscription = null
  if (activeClient) {
    // deactivate는 Promise를 반환하지만 호출자가 await할 의무는 없다.
    // disconnect 중 또 다른 connect가 들어와도 activeClient null 가드로 재진입이 안전하다.
    void activeClient.deactivate()
  }
  activeClient = null
  activeRoomCode = null
}

/**
 * 클라이언트→서버 ping. 가이드 §22.
 * STOMP heartbeat과 별개로, 서버가 클라이언트의 "내가 살아있다" 신호를 룸 단위로
 * 받아 처리하는 용도. 호출 측 훅이 N초 주기로 호출한다.
 */
export const sendRelayPing = () => {
  if (!activeClient || !activeRoomCode) return
  if (!activeClient.connected) return
  activeClient.publish({
    destination: pingDestination(activeRoomCode),
    body: '',
  })
}

export const getRelaySocketStatus = (): RelaySocketStatus => activeStatus

export const getRelaySocketRoomCode = (): string | null => activeRoomCode

// 호출 측 편의: 페이지 진입 시 store의 userUuid를 매번 읽어 넘기지 않도록.
export const connectRelaySocketForCurrentUser = (
  args: Omit<ConnectArgs, 'userUuid'>,
): boolean => {
  const userUuid = useUserStore.getState().userUuid
  if (!userUuid) {
    if (runtime.isDev) console.warn('[relaySocket] userUuid 없음 — UserBootstrap 이후에 호출')
    return false
  }
  connectRelaySocket({ ...args, userUuid })
  return true
}
