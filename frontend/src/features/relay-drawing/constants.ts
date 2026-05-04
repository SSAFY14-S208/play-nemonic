import type { LucideIcon } from 'lucide-react'
import {
  BadgeCheck,
  Brush,
  Cat,
  Crown,
  Download,
  Eraser,
  Link2,
  PaintBucket,
  Pencil,
  RotateCcw,
  Share2,
  Squirrel,
  Trash2,
  Undo2,
} from 'lucide-react'

export type RelayDrawingStep = 'booth' | 'lobby' | 'drawing' | 'result'
export type RelayRoundKey = 'face' | 'body' | 'legs'
export type RelayResultRevealStep = 'face' | 'body' | 'legs' | 'final'
export type RelayToolKey = 'pencil' | 'marker' | 'bucket' | 'undo' | 'redo' | 'eraser'

export interface RelayStepCopy {
  key: RelayDrawingStep
  label: string
}

export interface RelayParticipant {
  id: string
  name: string
  avatar: string
  role: string
  tone: 'fox' | 'cat' | 'bear'
  Icon: LucideIcon
}

export interface RelayRound {
  key: RelayRoundKey
  label: string
  status: 'done' | 'active' | 'pending'
}

export interface RelayResultReveal {
  key: RelayResultRevealStep
  order: number
  roleLabel: string
  participantName: string
  participantDisplayName: string
  avatar: string
  titleSuffix: string
  spotlightLabel: string
  nextLabel: string
}

export interface RelayTool {
  key: RelayToolKey
  label: string
  Icon: LucideIcon
  isPrimary?: boolean
}

export const RELAY_STEPS: RelayStepCopy[] = [
  { key: 'booth', label: '부스' },
  { key: 'lobby', label: '대기실' },
  { key: 'drawing', label: '드로잉' },
  { key: 'result', label: '결과' },
]

export const RELAY_PARTICIPANTS: RelayParticipant[] = [
  { id: 'fox', name: '여우 (나)', avatar: '여', role: '방장', tone: 'fox', Icon: Squirrel },
  { id: 'cat', name: '고양이', avatar: '고', role: '얼굴', tone: 'cat', Icon: Cat },
  { id: 'bear', name: '곰돌이', avatar: '곰', role: '다리', tone: 'bear', Icon: Crown },
]

export const RELAY_ROUNDS: RelayRound[] = [
  { key: 'face', label: '얼굴', status: 'active' },
  { key: 'body', label: '몸통', status: 'pending' },
  { key: 'legs', label: '다리', status: 'pending' },
]

export const RELAY_ROUND_ORDER: RelayRoundKey[] = ['face', 'body', 'legs']

export const RELAY_RESULT_REVEALS: RelayResultReveal[] = [
  {
    key: 'face',
    order: 1,
    roleLabel: '얼굴',
    participantName: '고양이',
    participantDisplayName: '고양이',
    avatar: '🐱',
    titleSuffix: '가 시작했어요',
    spotlightLabel: '방금 그린 사람',
    nextLabel: '다음 ▶',
  },
  {
    key: 'body',
    order: 2,
    roleLabel: '몸통',
    participantName: '여우',
    participantDisplayName: '여우 (나)',
    avatar: '🦊',
    titleSuffix: '가 이어 그렸어요',
    spotlightLabel: '방금 그린 사람',
    nextLabel: '다음 ▶',
  },
  {
    key: 'legs',
    order: 3,
    roleLabel: '다리',
    participantName: '곰돌이',
    participantDisplayName: '곰돌이',
    avatar: '🐻',
    titleSuffix: '가 마무리했어요',
    spotlightLabel: '방금 그린 사람',
    nextLabel: '결과 보기 ▶',
  },
  {
    key: 'final',
    order: 4,
    roleLabel: '완성',
    participantName: '고양이',
    participantDisplayName: '고양이',
    avatar: '🐱',
    titleSuffix: '님의 캐릭터',
    spotlightLabel: '합쳐진 캐릭터',
    nextLabel: '완성',
  },
]

export const RELAY_TOOLS: RelayTool[] = [
  { key: 'pencil', label: '연필', Icon: Pencil, isPrimary: true },
  { key: 'marker', label: '마커', Icon: Brush, isPrimary: true },
  { key: 'bucket', label: '채우기', Icon: PaintBucket, isPrimary: true },
  { key: 'undo', label: '되돌리기', Icon: Undo2 },
  { key: 'redo', label: '다시 실행', Icon: RotateCcw },
  { key: 'eraser', label: '지우개', Icon: Eraser },
]

export const RELAY_COLORS = [
  '#2f2a1e',
  '#ff5f67',
  '#ffc629',
  '#63c086',
  '#75a7df',
  '#5f88d6',
  '#ae80d7',
  '#f3a3c6',
  '#c16535',
  '#ffffff',
  '#9a9ca3',
]

export const RELAY_TIME_LIMITS_SECONDS = [30, 45, 60]

export const RELAY_ACTIONS = [
  { label: '링크 복사', Icon: Link2 },
  { label: 'QR 코드', Icon: BadgeCheck },
]

export const RELAY_RESULT_ACTIONS = [
  { label: '보관함에', Icon: Download },
  { label: '광장에 전시하기', Icon: Share2 },
]

export const RELAY_EMPTY_SLOTS = ['초대를 기다리는 중...', '초대를 기다리는 중...', '초대를 기다리는 중...']

export const RELAY_ROOM_CODE = 'ABC123'

export const RELAY_STAGE_SIZE = {
  width: 848,
  height: 720,
}

export const RELAY_FINAL_STAGE_SIZE = {
  width: 848,
  height: 1920,
}

export interface RelayRoundArea {
  y: number
  height: number
}

export interface RelayRoundRule {
  label: string
  helperText: string
  drawArea: RelayRoundArea
  exportArea: RelayRoundArea
  finalOffsetY: number
  incomingHintSourceRoundKey?: RelayRoundKey
  incomingHintSourceArea?: RelayRoundArea
  incomingHintTargetArea?: RelayRoundArea
  outgoingHintArea?: RelayRoundArea
}

export const RELAY_ROUND_RULES: Record<RelayRoundKey, RelayRoundRule> = {
  face: {
    label: '얼굴',
    helperText: '얼굴을 그리고, 아래 점선 구간만 다음 사람에게 힌트로 넘겨요',
    drawArea: { y: 0, height: 720 },
    exportArea: { y: 0, height: 720 },
    finalOffsetY: 0,
    outgoingHintArea: { y: 600, height: 120 },
  },
  body: {
    label: '몸통',
    helperText: '위쪽 힌트 선을 보고 몸통을 이어 그리고, 아래 구간을 다음 힌트로 남겨요',
    drawArea: { y: 0, height: 720 },
    exportArea: { y: 0, height: 720 },
    finalOffsetY: 600,
    incomingHintSourceRoundKey: 'face',
    incomingHintSourceArea: { y: 600, height: 120 },
    incomingHintTargetArea: { y: 0, height: 120 },
    outgoingHintArea: { y: 600, height: 120 },
  },
  legs: {
    label: '다리',
    helperText: '위쪽 힌트 선을 보고 다리를 이어 그려 캐릭터를 완성해요',
    drawArea: { y: 0, height: 720 },
    exportArea: { y: 0, height: 720 },
    finalOffsetY: 1200,
    incomingHintSourceRoundKey: 'body',
    incomingHintSourceArea: { y: 600, height: 120 },
    incomingHintTargetArea: { y: 0, height: 120 },
  },
}

export const RELAY_ROUND_SEGMENTS = Object.fromEntries(
  Object.entries(RELAY_ROUND_RULES).map(([roundKey, roundRule]) => [
    roundKey,
    {
      label: roundRule.label,
      helperText: roundRule.helperText,
      y: roundRule.drawArea.y,
      height: roundRule.drawArea.height,
    },
  ]),
) as Record<
  RelayRoundKey,
  {
    label: string
    helperText: string
    y: number
    height: number
  }
>

export const RELAY_PREVIEW_LINES = {
  faceCenterX: RELAY_STAGE_SIZE.width / 2,
  bodyTopY: 210,
}

export const RELAY_DANGER_ACTION = {
  label: '비우기',
  Icon: Trash2,
}
