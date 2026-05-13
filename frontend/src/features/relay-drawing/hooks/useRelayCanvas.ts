'use client'

import { useCallback, useRef } from 'react'
import type { KonvaEventObject } from 'konva/lib/Node'
import { createBucketFillLine } from '@/shared/utils'
import { RELAY_ROUND_RULES, RELAY_STAGE_SIZE } from '../constants'
import { useRelayDrawingStore } from '../stores'
import { isPointInsideArea } from '../utils'

export function useRelayCanvas() {
  const isDrawing = useRef(false)

  const beginDrawing = useCallback(
    (event: KonvaEventObject<MouseEvent | TouchEvent>) => {
      const stage = event.target.getStage()
      // getPointerPosition()은 Stage scaleX/scaleY를 적용하지 않은 캔버스-CSS-픽셀
      // 좌표를 반환한다. responsive sizing으로 Stage에 scale을 걸어둔 상황에서는
      // 그대로 쓰면 line이 저장된 좌표가 다시 scale로 곱해져 포인터와 다른 위치에
      // 그려진다. getRelativePointerPosition()이 Stage 자체의 transform 역변환을
      // 자동으로 해줘서 children 좌표계의 포인트를 돌려준다.
      const pointerPosition = stage?.getRelativePointerPosition()
      if (!pointerPosition) return

      const {
        activeRoundKey,
        selectedToolKey,
        selectedColor,
        selectedOpacity,
        strokeWidth,
        roundLines,
        addRecentColor,
        commitLine,
      } = useRelayDrawingStore.getState()

      const drawArea = RELAY_ROUND_RULES[activeRoundKey].drawArea
      if (!isPointInsideArea(pointerPosition, drawArea)) return

      if (selectedToolKey === 'bucket') {
        void createBucketFillLine({
          backgroundColor: '#fffdf7',
          boardSize: RELAY_STAGE_SIZE,
          fillColor: selectedColor,
          fillOpacity: selectedOpacity,
          idPrefix: `${activeRoundKey}-fill`,
          lines: roundLines[activeRoundKey],
          pointerPosition,
        }).then((fillLine) => {
          if (!fillLine) return
          const currentStore = useRelayDrawingStore.getState()
          if (currentStore.activeRoundKey !== activeRoundKey) return
          currentStore.commitLine(fillLine)
          currentStore.addRecentColor(selectedColor)
        })
        return
      }

      const stageColor = selectedToolKey === 'eraser' ? '#fffdf7' : selectedColor
      const activeStrokeWidth = strokeWidth
      const compositeOperation =
        selectedToolKey === 'eraser' ? 'destination-out' : 'source-over'

      isDrawing.current = true
      if (selectedToolKey !== 'eraser') {
        addRecentColor(selectedColor)
      }
      commitLine({
        id: `${activeRoundKey}-line-${Date.now()}-${roundLines[activeRoundKey].length}`,
        kind: 'stroke',
        color: stageColor,
        strokeWidth: activeStrokeWidth,
        opacity: selectedToolKey === 'eraser' ? 1 : selectedOpacity,
        compositeOperation,
        points: [{ x: pointerPosition.x, y: pointerPosition.y }],
      })
    },
    [],
  )

  const continueDrawing = useCallback(
    (event: KonvaEventObject<MouseEvent | TouchEvent>) => {
      if (!isDrawing.current) return

      const stage = event.target.getStage()
      // getRelativePointerPosition: Stage scale이 걸린 상황에서도 children 좌표계
      // 의 포인트를 돌려준다 (beginDrawing 주석 참조).
      const pointerPosition = stage?.getRelativePointerPosition()
      if (!pointerPosition) return

      const { activeRoundKey, appendPointToLastLine } = useRelayDrawingStore.getState()
      const drawArea = RELAY_ROUND_RULES[activeRoundKey].drawArea

      if (!isPointInsideArea(pointerPosition, drawArea)) {
        isDrawing.current = false
        return
      }

      appendPointToLastLine({ x: pointerPosition.x, y: pointerPosition.y })
    },
    [],
  )

  const endDrawing = useCallback(() => {
    isDrawing.current = false
  }, [])

  return { beginDrawing, continueDrawing, endDrawing }
}
