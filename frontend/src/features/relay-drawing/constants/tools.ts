// 드로잉 도구 / 색상 팔레트 / 위험 액션(전체 비우기).

import { Eraser, PaintBucket, Pencil, RotateCcw, Trash2, Undo2, type LucideIcon } from 'lucide-react'
import type { DrawingToolKey } from '@/shared/types'

export type RelayToolKey = DrawingToolKey
export type RelayToolCommandKey = RelayToolKey | 'undo' | 'redo'

export interface RelayTool {
  key: RelayToolCommandKey
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

export const RELAY_DANGER_ACTION = {
  label: '비우기',
  Icon: Trash2,
}
