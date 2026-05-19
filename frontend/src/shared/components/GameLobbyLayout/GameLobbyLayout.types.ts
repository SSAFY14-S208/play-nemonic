import type { StaticImageData } from 'next/image'

import type { HowToPlayPanel } from '../HowToPlayModal'

export interface GameLobbyTheme {
  /** CTA 버튼 배경, 활성 상태, 호스트 배지 */
  accent: string
  /** 아이콘 강조 (미지정 시 accent) */
  accentStrong?: string
  /** 기본 텍스트 */
  ink: string
  /** 카드 배경 (불투명) */
  paper: string
  /** 카드 배경 (반투명, 미지정 시 paper) */
  paperAlpha?: string
  /** 입력/비활성 버튼 배경 (미지정 시 paper) */
  active?: string
  /** 테두리 */
  line: string
  /** 보조 텍스트 */
  muted: string
  /** 대기 슬롯 텍스트 (미지정 시 muted) */
  dash?: string
  /** QR 코드 모듈 색상 (미지정 시 ink) */
  qrDark?: string
  /** QR 코드 배경 색상 (미지정 시 '#ffffff') */
  qrLight?: string
}

export interface LobbyParticipant {
  userUuid: string
  nickname: string
  host: boolean
  connected: boolean
}

export interface GameLobbyLayoutProps {
  // ── 테마 ──
  theme: GameLobbyTheme

  // ── 타이틀 ──
  titleImage: StaticImageData | string
  titleImageAlt: string
  subtitle?: string

  // ── 룸 ──
  roomCode: string

  // ── 참여자 ──
  participants: LobbyParticipant[]
  maxParticipants: number
  minParticipants: number
  currentUserUuid: string | null
  /** px 단위. 초과 시 overflow-y: auto 스크롤 */
  participantListMaxHeight?: number
  /** 대기 슬롯 안내 텍스트. 기본: "초대를 기다리는 중..." */
  waitingSlotText?: string

  // ── 강퇴 ──
  kickingTargetUuid?: string | null
  kickError?: string | null
  onKickParticipant?: (targetUserUuid: string) => void

  // ── 호스트 ──
  isHost: boolean

  // ── 제한 시간 (선택 섹션 — 미지정 시 미렌더) ──
  timeLimitSeconds?: number
  timeLimitAllowedSeconds?: number[]
  onChangeTimeLimit?: (seconds: number) => void
  settingsError?: string | null

  // ── 시작 ──
  canStartGame: boolean
  isStarting: boolean
  startButtonLabel: string
  onStartGame: () => void
  /** 비호스트에게 보이는 메시지. 기본: "방장이 게임을 시작할 때까지 기다려주세요" */
  nonHostMessage?: string

  // ── 나가기 ──
  onLeave: () => void

  // ── 애니메이션 ──
  isExiting?: boolean

  // ── 배경 이미지 (선택) ──
  /** 전체 배경으로 깔리는 이미지. fill + object-cover로 렌더 */
  backgroundImage?: StaticImageData | string
  /** 배경 이미지 위에 덧씌우는 CSS gradient. 예: "radial-gradient(...)" */
  backgroundOverlay?: string

  // ── 게임 설명 모달 (선택) ──
  howToPlayPanels?: HowToPlayPanel[]
  howToPlayTitle?: string
  howToPlaySubtitle?: string
  howToPlayAccentColor?: string
  autoOpenHowToPlay?: boolean
}
