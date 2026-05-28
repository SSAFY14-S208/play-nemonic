'use client'

import { useCallback } from 'react'

import { RELAY_ROUND_RULES, RELAY_STAGE_SIZE } from '@/features/relay-drawing/constants'
import { useRelayDrawingStore } from '@/features/relay-drawing/stores'
import { renderLinesToRasterCanvas } from '@/features/relay-drawing/utils/canvas-rendering'

function canvasToPngBlob(canvas: HTMLCanvasElement) {
  return new Promise<Blob | null>((resolve) => {
    canvas.toBlob((blob) => resolve(blob), 'image/png')
  })
}

export function useRelayDrawingCapture() {
  const captureCanvasBlob = useCallback(async (): Promise<Blob | null> => {
    const { activeRoundKey, roundLines } = useRelayDrawingStore.getState()
    const roundRule = RELAY_ROUND_RULES[activeRoundKey]
    const lines = roundLines[activeRoundKey]

    const rasterCanvas = await renderLinesToRasterCanvas(
      lines,
      roundRule.canvasHeight,
    )
    if (!rasterCanvas) return null

    const drawArea = roundRule.drawArea
    const submissionCanvas = document.createElement('canvas')
    submissionCanvas.width = RELAY_STAGE_SIZE.width
    submissionCanvas.height = drawArea.height

    const submissionContext = submissionCanvas.getContext('2d')
    if (!submissionContext) return null

    submissionContext.drawImage(
      rasterCanvas,
      0,
      drawArea.y,
      RELAY_STAGE_SIZE.width,
      drawArea.height,
      0,
      0,
      RELAY_STAGE_SIZE.width,
      drawArea.height,
    )

    return canvasToPngBlob(submissionCanvas)
  }, [])

  const captureHintBlob = useCallback(async (): Promise<Blob | null> => {
    const { activeRoundKey, roundLines } = useRelayDrawingStore.getState()
    const roundRule = RELAY_ROUND_RULES[activeRoundKey]
    const outgoingHintArea = roundRule.outgoingHintArea
    if (!outgoingHintArea) return null

    const lines = roundLines[activeRoundKey]
    const fullCanvas = await renderLinesToRasterCanvas(
      lines,
      roundRule.canvasHeight,
    )
    if (!fullCanvas) return null

    const hintCanvas = document.createElement('canvas')
    hintCanvas.width = RELAY_STAGE_SIZE.width
    hintCanvas.height = outgoingHintArea.height

    const hintContext = hintCanvas.getContext('2d')
    if (!hintContext) return null

    hintContext.drawImage(
      fullCanvas,
      0,
      outgoingHintArea.y,
      RELAY_STAGE_SIZE.width,
      outgoingHintArea.height,
      0,
      0,
      RELAY_STAGE_SIZE.width,
      outgoingHintArea.height,
    )

    return canvasToPngBlob(hintCanvas)
  }, [])

  return { captureCanvasBlob, captureHintBlob }
}
