'use client'

import {
  RelayDrawingView,
  RelayFinalizingView,
  RelayLobbyView,
  RelayResultView,
} from './components'
import { useRelayDrawingStore } from './stores'

// 라우트: /relay-drawing/[roomCode]
//
// 한 룸의 게임 흐름 전체를 담당한다. WebSocket 연결 1개가 lobby ↔ drawing ↔
// result 전환을 모두 관통하므로(가이드 §22) 라우트 단위가 아니라 이 페이지
// 안에서 view를 스왑한다.
//
// 분기 기준은 서버에서 받은 roomStatus다 — 가이드 §25의 "REST = 진실의
// 기준점, WS = 변화" 원칙. 새로고침으로 RelayRoomPage가 다시 마운트돼도
// 첫 REST hydrate가 끝나면 자연스럽게 올바른 view에 도달한다.
//
// roomStatus가 null인 동안(=REST hydrate 진행 중)은 lobby로 폴백한다.
// CLOSED는 다음 단계에서 모달로 처리할 예정.
export default function RelayRoomPage() {
  const roomStatus = useRelayDrawingStore((state) => state.roomStatus)

  if (roomStatus === 'PLAYING') return <RelayDrawingView />
  if (roomStatus === 'FINALIZING') return <RelayFinalizingView />
  if (roomStatus === 'FINISHED') return <RelayResultView />
  return <RelayLobbyView />
}
