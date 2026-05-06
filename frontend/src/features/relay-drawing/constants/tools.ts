// 드로잉 도구 / 색상 팔레트 / 위험 액션(전체 비우기).

import type { LucideIcon } from 'lucide-react'
import { Eraser, PaintBucket, Pencil, RotateCcw, Trash2, Undo2 } from 'lucide-react'

export type RelayToolKey = 'pencil' | 'marker' | 'bucket' | 'undo' | 'redo' | 'eraser'

export interface RelayTool {
  key: RelayToolKey
  label: string
  Icon: LucideIcon
  isPrimary?: boolean
}

export const RELAY_TOOLS: RelayTool[] = [
  { key: 'pencil', label: '연필', Icon: Pencil, isPrimary: true },
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

export const RELAY_DANGER_ACTION = {
  label: '비우기',
  Icon: Trash2,
}
