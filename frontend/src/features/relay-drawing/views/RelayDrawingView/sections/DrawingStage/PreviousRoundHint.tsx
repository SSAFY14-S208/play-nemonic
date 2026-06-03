'use client'

import { useEffect, useState } from 'react'
import { Group, Image as KonvaImage } from 'react-konva'
import { RELAY_STAGE_SIZE } from '@/features/relay-drawing/constants'
import DashedGuide from './DashedGuide'
import HintPill from './HintPill'

interface PreviousRoundHintProps {
  overlayHeight: number
  /** 서버에서 내려준 힌트 이미지 URL — 이전 파트의 하단 overlapHeight를 잘라낸 PNG. */
  hintImageUrl?: string | null
}

export default function PreviousRoundHint({
  overlayHeight,
  hintImageUrl,
}: PreviousRoundHintProps) {
  const [serverHintImage, setServerHintImage] = useState<HTMLImageElement | null>(null)

  useEffect(() => {
    let isCancelled = false

    ;(async () => {
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

  return (
    <Group>
      {hintImageUrl && serverHintImage && (
        <KonvaImage
          image={serverHintImage}
          x={0}
          y={0}
          width={RELAY_STAGE_SIZE.width}
          height={overlayHeight}
          opacity={0.35}
          listening={false}
        />
      )}
      <DashedGuide verticalPosition={overlayHeight} />
      <HintPill
        x={46}
        y={overlayHeight - 48}
        label="이전 사람의 그림 (참고용)"
      />
    </Group>
  )
}
