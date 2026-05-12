// Zustand slice 패턴 — 책임별로 슬라이스를 쪼개고 store는 슬라이스의 합집합.
// 각 슬라이스 creator는 합쳐진 RelayDrawingStore 타입에 대해 동작해서, 슬라이스
// 내부에서 set()으로 다른 슬라이스 필드를 건드릴 수 있다(예: clearRoom이 캔버스
// 상태도 같이 비움). 강제 격리는 안 하지만, "어느 슬라이스가 어떤 액션의 주인"
// 이라는 가시성을 가지려고 분리한다.

import type {
  RelayPart,
  RelayRoomMyAssignmentResponse,
  RelayRoomParticipantResponse,
  RelayRoomResultItemResponse,
  RelayRoomStateResponse,
  RelayRoomStatus,
} from '@/shared/types'

import type {
  RelayResultRevealStep,
  RelayRoundKey,
  RelayToolKey,
} from '../constants'
import type { RelayDrawLine, RelayDrawPoint, RelayRoundLines } from '../types'

// WS 종료성 이벤트 수신 시 모달에 표시할 사유.
// 핸들러가 즉시 clear/redirect 하지 않고, 이 값을 store에 세팅하면
// RelayRoomPage가 RelayDismissalModal을 렌더한다.
//
// KICKED는 모달 흐름이 아니라 즉시 redirect + toast로 처리되므로 여기 포함되지 않는다
// (useRelayRoom의 KICKED_FROM_ROOM 핸들러 참고).
export type RelayDismissalReason = 'DUPLICATE_SESSION' | 'ROOM_CLOSED'

// hydrateRoomState 인자 — REST(getRelayRoom)와 방 생성/입장 응답이 모두
// 만족하는 최소 교집합. 게임 진행 필드(currentPart 등)는 다루지 않는다.
// timeLimitAllowedSeconds는 REST 응답에만 포함되고 WS 이벤트에는 없으므로 optional.
export type RelayRoomHydratePayload = Pick<
  RelayRoomStateResponse,
  | 'roomCode'
  | 'status'
  | 'hostUserUuid'
  | 'timeLimitSeconds'
  | 'minParticipants'
  | 'maxParticipants'
  | 'participants'
> & {
  timeLimitAllowedSeconds?: number[]
}

export interface RoomSlice {
  roomCode: string | null
  roomStatus: RelayRoomStatus | null
  hostUserUuid: string | null
  participants: RelayRoomParticipantResponse[]
  timeLimitSeconds: number
  // 백엔드가 방 생성 시 허용 가능한 제한 시간 목록을 내려준다.
  // 로비 UI에서 시간 선택 버튼을 이 배열로 렌더링한다.
  timeLimitAllowedSeconds: number[]
  // 정원 — 가이드 §9·§15. 백엔드가 방 생성 시 결정해 응답에 함께 내려준다.
  minParticipants: number
  maxParticipants: number
  // 종료성 이벤트 사유 — 모달 표시 후 clearRoom + 부스 이동.
  dismissalReason: RelayDismissalReason | null

  hydrateRoomState: (payload: RelayRoomHydratePayload) => void
  setRoomStatus: (roomStatus: RelayRoomStatus) => void
  setParticipants: (participants: RelayRoomParticipantResponse[]) => void
  setHostUserUuid: (hostUserUuid: string) => void
  setTimeLimitSeconds: (seconds: number) => void
  setDismissalReason: (reason: RelayDismissalReason) => void
  // clearRoom: 룸 떠나기 / 모달 확인 시. 캔버스/결과 슬라이스 필드도 같이 비움.
  clearRoom: () => void
}

export interface CanvasSlice {
  activeRoundKey: RelayRoundKey
  selectedToolKey: RelayToolKey
  selectedColor: string
  selectedOpacity: number
  strokeWidth: number
  recentColors: string[]
  roundLines: RelayRoundLines

  // 서버 배정 — getRelayRoomAssignmentMe 응답으로 채워진다.
  canvasIndex: number | null
  currentPart: RelayPart | null
  partDeadlineAt: string | null
  hintImageUrl: string | null

  // 제출 상태
  isSubmitting: boolean
  isSubmitted: boolean
  submittedCount: number
  totalCount: number
  // 현재 파트에서 이미 제출한 참여자의 userUuid 목록 — 우측 친구 패널이
  // "X님 완료" 표시를 띄우는 데 쓴다. PART_STARTED / GAME_STARTED / setAssignment
  // 시점에 비워지고, PART_SUBMITTED / PART_AUTO_SUBMITTED 수신 시 추가된다.
  submittedUserUuids: string[]

  // 라운드별 데드라인/제출 상태 — 라운드 전환 시 cross-round auto-submit 방지.
  // roundDeadlines[round] === null이면 해당 라운드 데드라인 미수신 → 자동 제출 금지.
  // roundSubmitted[round] === true이면 해당 라운드 제출 완료 → 재제출 금지.
  roundDeadlines: Record<RelayRoundKey, string | null>
  roundSubmitted: Record<RelayRoundKey, boolean>

  // 라운드 전환 애니메이션
  isTransitioning: boolean

  // PART_TIME_UP 수신 ~ PART_STARTED(또는 ALL_PARTS_COMPLETED) 사이.
  // RelayDrawingView가 검은 오버레이 + 스피너로 "다음 파트 준비 중" 표시.
  isPartTimeUp: boolean

  // WS 이벤트(GAME_STARTED/PART_STARTED) 전용 카운터 — effect 트리거용.
  // partDeadlineAt을 effect 의존성으로 쓰면 setAssignment 내부 set이
  // 재트리거를 유발하므로, 트리거와 데이터 세팅을 분리한다.
  partFetchTrigger: number

  // PART_TIME_UP 수신 시 본인이 미제출자 목록에 있으면 increment.
  // useRelayDrawingGame이 effect로 감지해 submitDrawing을 호출한다.
  // 같은 카운터 값으로 재호출되지 않는 단조 증가 트리거 — partFetchTrigger와 동일 패턴.
  pendingAutoSubmitTrigger: number

  // undo로 빠져나간 라인을 라운드별로 보관하는 redo 스택. 새 라인이 commit되면
  // 비워진다(표준 redo 동작 — 새 액션 후엔 redo가 의미를 잃기 때문).
  roundRedoStack: RelayRoundLines

  setActiveRoundKey: (roundKey: RelayRoundKey) => void
  // completeRound: 로컬 미리보기 용도(서버 연결 없이 라운드 전환).
  // 실제 게임 흐름에서는 submitDrawing → PART_STARTED → setAssignment 순서.
  completeRound: () => void
  setSelectedToolKey: (toolKey: RelayToolKey) => void
  setSelectedColor: (color: string) => void
  setSelectedOpacity: (opacity: number) => void
  setStrokeWidth: (strokeWidth: number) => void
  addRecentColor: (color: string) => void
  commitLine: (line: RelayDrawLine) => void
  appendPointToLastLine: (point: RelayDrawPoint) => void
  undoLine: () => void
  redoLine: () => void
  clearRoundLines: () => void

  // 서버 배정 적용 — getRelayRoomAssignmentMe 응답으로 캔버스/파트/힌트를 세팅하고
  // 이전 라운드 드로잉 데이터를 비운다.
  setAssignment: (assignment: RelayRoomMyAssignmentResponse) => void
  setPartDeadlineAt: (deadline: string) => void
  setIsSubmitting: (isSubmitting: boolean) => void
  markSubmitted: (roundKey?: RelayRoundKey) => void
  setRoundDeadline: (roundKey: RelayRoundKey, deadline: string) => void
  updateSubmissionProgress: (submittedCount: number, totalCount: number) => void
  // 한 파트에서 같은 사용자가 두 번 들어오는 경우는 백엔드가 막지만, 클라이언트
  // 입장에서도 dedup으로 안전망. 같은 userUuid면 무시된다.
  addSubmittedUserUuid: (userUuid: string) => void
  clearSubmittedUserUuids: () => void
  // beginTransition / advanceToNextRound: 라운드 전환 애니메이션 제어.
  beginTransition: () => void
  advanceToNextRound: () => void
  incrementPartFetchTrigger: () => void
  setPartTimeUp: (value: boolean) => void
  triggerPendingAutoSubmit: () => void
  clearAssignment: () => void
}

export interface ResultSlice {
  resultRevealStep: RelayResultRevealStep
  completedAt: string | null

  // 서버 결과 — getRelayRoomResults 응답으로 채워진다.
  resultItems: RelayRoomResultItemResponse[]
  activeResultIndex: number

  setResults: (items: RelayRoomResultItemResponse[]) => void
  setActiveResultIndex: (index: number) => void
  goToNextResultReveal: () => void
  goToPreviousResultReveal: () => void
  // resetSession: 새 게임 시작 시 캔버스/결과 슬라이스를 초기화.
  resetSession: () => void
}

export type RelayDrawingStore = RoomSlice & CanvasSlice & ResultSlice
