import { Group, Layer, Line } from 'react-konva'

import { RELAY_STAGE_SIZE, type RelayRoundArea } from '@/features/relay-drawing/constants'
import type { RelayDrawLine } from '@/features/relay-drawing/types'

import RasterFillImage from './RasterFillImage'

interface DrawingLinesLayerProps {
  lines: RelayDrawLine[]
  drawArea: RelayRoundArea
}

function getLinePoints(line: RelayDrawLine) {
  return line.points.flatMap((point) => [point.x, point.y])
}

export default function DrawingLinesLayer({
  lines,
  drawArea,
}: DrawingLinesLayerProps) {
  return (
    <Layer>
      <Group
        clipX={0}
        clipY={drawArea.y}
        clipWidth={RELAY_STAGE_SIZE.width}
        clipHeight={drawArea.height}
      >
        {lines.map((line) => {
          if (line.kind === 'fill') {
            if (line.imageDataUrl) {
              return (
                <RasterFillImage
                  key={line.id}
                  imageDataUrl={line.imageDataUrl}
                  compositeOperation={line.compositeOperation}
                />
              )
            }

            return (
              <Line
                key={line.id}
                points={getLinePoints(line)}
                fill={line.color}
                opacity={line.opacity ?? 1}
                closed
                listening={false}
                globalCompositeOperation={line.compositeOperation ?? 'source-over'}
              />
            )
          }

          return (
            <Line
              key={line.id}
              points={getLinePoints(line)}
              stroke={line.color}
              strokeWidth={line.strokeWidth}
              opacity={line.opacity ?? 1}
              tension={0.45}
              lineCap="round"
              lineJoin="round"
              globalCompositeOperation={
                line.compositeOperation ??
                (line.color === '#fffdf7' ? 'destination-out' : 'source-over')
              }
            />
          )
        })}
      </Group>
    </Layer>
  )
}
