import type { ReactNode } from 'react'

export interface FlipbookPrintFrame {
  id: string
  title: string
  frameNumber: number
  drawnByName?: string | null
  imageUrl?: string | null
  accentColor?: string
  outputMode?: 'nemonic-print' | 'gif-playback'
}

export interface FlipbookPrintParticipant {
  id: string
  name: string
  firstStartedWorkId: string
  firstStartedWorkTitle: string
  accentColor?: string
  frames: FlipbookPrintFrame[]
}

export type RenderFlipbookPrintPaper = (
  frame: FlipbookPrintFrame,
  frameIndex: number,
  participant: FlipbookPrintParticipant,
) => ReactNode
