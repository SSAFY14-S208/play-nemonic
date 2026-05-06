'use client'

import { useCallback, useRef } from 'react'
import type { KonvaEventObject } from 'konva/lib/Node'
import { RELAY_ROUND_RULES } from '../constants'
import { useRelayDrawingStore } from '../relayDrawingStore'
import { createBucketFillLine, isPointInsideArea } from '../utils'

export function useRelayCanvas() {
  const isDrawing = useRef(false)

  const beginDrawing = useCallback(
    (event: KonvaEventObject<MouseEvent | TouchEvent>) => {
      const stage = event.target.getStage()
      const pointerPosition = stage?.getPointerPosition()
      if (!pointerPosition) return

      const {
        activeRoundKey,
        selectedToolKey,
        selectedColor,
        strokeWidth,
        roundLines,
        commitLine,
      } = useRelayDrawingStore.getState()

      const drawArea = RELAY_ROUND_RULES[activeRoundKey].drawArea
      if (!isPointInsideArea(pointerPosition, drawArea)) return

      if (selectedToolKey === 'bucket') {
        void createBucketFillLine({
          activeRoundKey,
          fillColor: selectedColor,
          lines: roundLines[activeRoundKey],
          pointerPosition,
        }).then((fillLine) => {
          if (!fillLine) return
          useRelayDrawingStore.getState().commitLine(fillLine)
        })
        return
      }

      const stageColor = selectedToolKey === 'eraser' ? '#fffdf7' : selectedColor
      const activeStrokeWidth = selectedToolKey === 'marker' ? strokeWidth + 4 : strokeWidth

      isDrawing.current = true
      commitLine({
        id: `${activeRoundKey}-line-${Date.now()}-${roundLines[activeRoundKey].length}`,
        kind: 'stroke',
        color: stageColor,
        strokeWidth: activeStrokeWidth,
        points: [{ x: pointerPosition.x, y: pointerPosition.y }],
      })
    },
    [],
  )

  const continueDrawing = useCallback(
    (event: KonvaEventObject<MouseEvent | TouchEvent>) => {
      if (!isDrawing.current) return

      const stage = event.target.getStage()
      const pointerPosition = stage?.getPointerPosition()
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
