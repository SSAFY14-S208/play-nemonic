// Zustand slice 패턴 — 책임별로 슬라이스를 쪼개고 store는 슬라이스의 합집합.
// 각 슬라이스 creator는 합쳐진 RelayDrawingStore 타입에 대해 동작해서, 슬라이스
// 내부에서 set()으로 다른 슬라이스 필드를 건드릴 수 있다(예: clearRoom이 캔버스
// 상태도 같이 비움). 강제 격리는 안 하지만, "어느 슬라이스가 어떤 액션의 주인"
// 이라는 가시성을 가지려고 분리한다.

import type {
  RelayRoomParticipantResponse,
  RelayRoomStateResponse,
  RelayRoomStatus,
} from '@/shared/types'

import type {
  RelayResultRevealStep,
  RelayRoundKey,
  RelayToolKey,
} from '../constants'
import type { RelayDrawLine, RelayDrawPoint, RelayRoundLines } from '../types'

// hydrateRoomState 인자 — REST(getRelayRoom)와 방 생성/입장 응답이 모두
// 만족하는 최소 교집합. 게임 진행 필드(currentPart 등)는 다루지 않는다.
export type RelayRoomHydratePayload = Pick<
  RelayRoomStateResponse,
  | 'roomCode'
  | 'status'
  | 'hostUserUuid'
  | 'timeLimitSeconds'
  | 'minParticipants'
  | 'maxParticipants'
  | 'participants'
>

export interface RoomSlice {
  roomCode: string | null
  roomStatus: RelayRoomStatus | null
  hostUserUuid: string | null
  participants: RelayRoomParticipantResponse[]
  timeLimitSeconds: number
  // 정원 — 가이드 §9·§15. 백엔드가 방 생성 시 결정해 응답에 함께 내려준다.
  minParticipants: number
  maxParticipants: number

  hydrateRoomState: (payload: RelayRoomHydratePayload) => void
  setRoomStatus: (roomStatus: RelayRoomStatus) => void
  setParticipants: (participants: RelayRoomParticipantResponse[]) => void
  setHostUserUuid: (hostUserUuid: string) => void
  setTimeLimitSeconds: (seconds: number) => void
  // clearRoom: 룸 떠나기 / ROOM_CLOSED 수신 시. 캔버스/결과 슬라이스 필드도 같이 비움.
  clearRoom: () => void
}

export interface CanvasSlice {
  activeRoundKey: RelayRoundKey
  selectedToolKey: RelayToolKey
  selectedColor: string
  strokeWidth: number
  roundLines: RelayRoundLines

  setActiveRoundKey: (roundKey: RelayRoundKey) => void
  // completeRound: 마지막 라운드면 result 슬라이스의 completedAt/resultRevealStep까지 마무리.
  completeRound: () => void
  setSelectedToolKey: (toolKey: RelayToolKey) => void
  setSelectedColor: (color: string) => void
  setStrokeWidth: (strokeWidth: number) => void
  commitLine: (line: RelayDrawLine) => void
  appendPointToLastLine: (point: RelayDrawPoint) => void
  undoLine: () => void
  clearRoundLines: () => void
}

export interface ResultSlice {
  resultRevealStep: RelayResultRevealStep
  completedAt: string | null

  goToNextResultReveal: () => void
  goToPreviousResultReveal: () => void
  // resetSession: 새 게임 시작 시 캔버스/결과 슬라이스를 초기화.
  resetSession: () => void
}

export type RelayDrawingStore = RoomSlice & CanvasSlice & ResultSlice
