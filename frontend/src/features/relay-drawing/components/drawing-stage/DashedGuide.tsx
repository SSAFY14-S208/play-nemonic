'use client'

import { Rect } from 'react-konva'
import { RELAY_STAGE_SIZE } from '../../constants'

interface DashedGuideProps {
  verticalPosition: number
}

export default function DashedGuide({ verticalPosition }: DashedGuideProps) {
  const dashSegments = []

  for (
    let horizontalPosition = 8;
    horizontalPosition < RELAY_STAGE_SIZE.width;
    horizontalPosition += 12
  ) {
    dashSegments.push(horizontalPosition)
  }

  return (
    <>
      {dashSegments.map((horizontalPosition) => (
        <Rect
          key={`${horizontalPosition}-${verticalPosition}`}
          x={horizontalPosition}
          y={verticalPosition}
          width={6}
          height={2}
          fill="#d49b1f"
          opacity={0.38}
          cornerRadius={1}
        />
      ))}
    </>
  )
}
