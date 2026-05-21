'use client'

import { Group, Rect, Text } from 'react-konva'

interface HintPillProps {
  x: number
  y: number
  label: string
}

export default function HintPill({ x, y, label }: HintPillProps) {
  return (
    <Group>
      <Rect
        x={x}
        y={y}
        width={220}
        height={32}
        fill="#ffd873"
        cornerRadius={16}
        shadowColor="#c4891f"
        shadowBlur={10}
        shadowOpacity={0.14}
      />
      <Text
        x={x}
        y={y + 7}
        width={220}
        text={`👀 ${label}`}
        align="center"
        fontFamily="Paperlogy"
        fontSize={14}
        fontStyle="bold"
        fill="#ffffff"
      />
    </Group>
  )
}
