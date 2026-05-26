'use client'

import Konva from 'konva'
import { useEffect, useRef } from 'react'
import { Group, Rect, Text } from 'react-konva'

import { RELAY_STAGE_SIZE, type RelayRoundArea } from '../../../../constants'
import DashedGuide from './DashedGuide'
import HintPill from './HintPill'

interface OutgoingHintProps {
  outgoingHintArea: RelayRoundArea
  /** 반짝이 + 안내 텍스트 노출 여부. 사용자가 한 번도 그리지 않았고 제출 전인
   *  상태에서만 true. 드래그가 한 번이라도 발생하거나 제출(수동/자동)되면 false. */
  isAttentionVisible: boolean
}

export default function OutgoingHint({
  outgoingHintArea,
  isAttentionVisible,
}: OutgoingHintProps) {
  const shimmerRectRef = useRef<Konva.Rect>(null)

  // 반짝이 펄스 — Konva.Animation으로 노드 attribute를 직접 갱신해 React 리렌더 없이
  // 매 프레임 opacity/shadowBlur를 sine 곡선으로 변화시킨다. 효과가 꺼지면 즉시 stop.
  useEffect(() => {
    if (!isAttentionVisible) return
    const rectNode = shimmerRectRef.current
    if (!rectNode) return
    const layer = rectNode.getLayer()
    if (!layer) return

    const pulseAnimation = new Konva.Animation((frame) => {
      if (!frame) return
      // 1.6초 주기 sine — 또렷하게 빛났다가 사그라드는 펄스
      const pulse = (Math.sin(frame.time * 0.004) + 1) / 2
      rectNode.opacity(0.32 + pulse * 0.62)
      rectNode.shadowBlur(14 + pulse * 32)
      rectNode.shadowOpacity(0.25 + pulse * 0.45)
    }, layer)
    pulseAnimation.start()

    return () => {
      pulseAnimation.stop()
    }
  }, [isAttentionVisible])

  return (
    <Group>
      <Rect
        x={0}
        y={outgoingHintArea.y}
        width={RELAY_STAGE_SIZE.width}
        height={outgoingHintArea.height}
        fill="#fff1bf"
        opacity={0.36}
        shadowColor="#e5a82f"
        shadowBlur={18}
        shadowOpacity={0.12}
      />
      {isAttentionVisible && (
        <Rect
          ref={shimmerRectRef}
          x={0}
          y={outgoingHintArea.y}
          width={RELAY_STAGE_SIZE.width}
          height={outgoingHintArea.height}
          fill="#fff2b8"
          opacity={0.6}
          shadowColor="#f6a623"
          shadowBlur={30}
          shadowOpacity={0.5}
          listening={false}
        />
      )}
      <DashedGuide verticalPosition={outgoingHintArea.y} />
      {isAttentionVisible && (
        <Text
          x={0}
          y={outgoingHintArea.y + outgoingHintArea.height / 2 - 18}
          width={RELAY_STAGE_SIZE.width}
          text="이곳을 그려야 다음 사람이 힌트를 받아요!"
          align="center"
          fontFamily="Paperlogy"
          fontSize={16}
          fontStyle="bold"
          fill="#b3771a"
          listening={false}
        />
      )}
      <HintPill
        x={46}
        y={outgoingHintArea.y + outgoingHintArea.height - 48}
        label="다음 사람에게 보이는 구간"
      />
    </Group>
  )
}
