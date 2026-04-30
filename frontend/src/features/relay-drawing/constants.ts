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
  { key: 'face', label: '얼굴', status: 'done' },
  { key: 'body', label: '몸통', status: 'active' },
  { key: 'legs', label: '다리', status: 'pending' },
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

export const RELAY_PREVIEW_LINES = {
  faceCenterX: RELAY_STAGE_SIZE.width / 2,
  bodyTopY: 210,
}

export const RELAY_DANGER_ACTION = {
  label: '비우기',
  Icon: Trash2,
}
