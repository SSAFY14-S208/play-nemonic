'use client'

import { useEffect, useState } from 'react'
import { Group, Image as KonvaImage, Line, Rect, Text } from 'react-konva'
import { RELAY_STAGE_SIZE, type RelayRoundArea } from '../../constants'
import type { RelayDrawLine } from '../../types'
import DashedGuide from './DashedGuide'
import HintPill from './HintPill'
import RasterFillImage from './RasterFillImage'

interface PreviousRoundHintProps {
  hintTargetArea: RelayRoundArea
  hintVerticalOffset: number
  /** 서버에서 내려준 힌트 이미지 URL — 있으면 로컬 라인 대신 서버 이미지 표시. */
  hintImageUrl?: string | null
  /** 로컬 미리보기 모드에서 이전 라운드 라인 데이터. */
  previousRoundLines?: RelayDrawLine[]
}

export default function PreviousRoundHint({
  hintTargetArea,
  hintVerticalOffset,
  hintImageUrl,
  previousRoundLines = [],
}: PreviousRoundHintProps) {
  const [serverHintImage, setServerHintImage] = useState<HTMLImageElement | null>(null)

  useEffect(() => {
    let isCancelled = false

    void (async () => {
      if (!hintImageUrl) {
        if (!isCancelled) setServerHintImage(null)
        return
      }

      const imageElement = new window.Image()
      imageElement.crossOrigin = 'anonymous'
      imageElement.onload = () => {
        if (!isCancelled) setServerHintImage(imageElement)
      }
      imageElement.src = hintImageUrl
    })()

    return () => {
      isCancelled = true
    }
  }, [hintImageUrl])

  const hasServerHint = hintImageUrl !== undefined && hintImageUrl !== null
  const hasLocalLines = previousRoundLines.length > 0

  return (
    <Group>
      <Rect
        x={0}
        y={hintTargetArea.y}
        width={RELAY_STAGE_SIZE.width}
        height={hintTargetArea.height}
        fill="#fff8e4"
        opacity={0.72}
        shadowColor="#e5a82f"
        shadowBlur={18}
        shadowOpacity={0.16}
      />
      <HintPill
        x={46}
        y={hintTargetArea.y + 18}
        label="이전 사람의 그림 (하단 일부)"
      />
      <Group
        clipX={0}
        clipY={hintTargetArea.y}
        clipWidth={RELAY_STAGE_SIZE.width}
        clipHeight={hintTargetArea.height}
      >
        {hasServerHint && serverHintImage && (
          <KonvaImage
            image={serverHintImage}
            x={0}
            y={hintTargetArea.y}
            width={RELAY_STAGE_SIZE.width}
            height={hintTargetArea.height}
            opacity={0.62}
            listening={false}
          />
        )}

        {!hasServerHint && hasLocalLines && previousRoundLines.map((line) => {
          if (line.kind === 'fill') {
            if (line.imageDataUrl) {
              return (
                <RasterFillImage
                  key={`preview-${line.id}`}
                  imageDataUrl={line.imageDataUrl}
                  opacity={0.62}
                  yOffset={hintVerticalOffset}
                />
              )
            }

            return (
              <Line
                key={`preview-${line.id}`}
                points={line.points.flatMap((point) => [
                  point.x,
                  point.y + hintVerticalOffset,
                ])}
                fill={line.color}
                closed
                opacity={0.62}
                listening={false}
              />
            )
          }

          return (
            <Line
              key={`preview-${line.id}`}
              points={line.points.flatMap((point) => [
                point.x,
                point.y + hintVerticalOffset,
              ])}
              stroke={line.color}
              strokeWidth={line.strokeWidth}
              tension={0.45}
              lineCap="round"
              lineJoin="round"
              opacity={0.62}
            />
          )
        })}
      </Group>
      <DashedGuide verticalPosition={hintTargetArea.y + hintTargetArea.height} />
      <Text
        x={0}
        y={hintTargetArea.y + 76}
        width={RELAY_STAGE_SIZE.width}
        text="↓ 여기부터 이어 그리세요"
        align="center"
        fontFamily="Pretendard Variable"
        fontSize={16}
        fontStyle="bold"
        fill="#efc759"
        opacity={0.72}
      />
    </Group>
  )
}
