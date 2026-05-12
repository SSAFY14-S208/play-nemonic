'use client'

import { useCallback, useState } from 'react'
import type {
  DrawingArea,
  DrawingBoardSize,
  DrawingLine,
  DrawingPointerEvent,
  DrawingToolKey,
} from '@/shared/types'
import { createBucketFillLine, isPointInsideDrawingArea } from '@/shared/utils'

interface UseDrawingBoardOptions {
  boardSize: DrawingBoardSize
  drawArea?: DrawingArea
  backgroundColor?: string
  defaultColor: string
  defaultStrokeWidth?: number
}

const MAX_RECENT_COLOR_COUNT = 5

export function useDrawingBoard({
  boardSize,
  drawArea,
  backgroundColor = '#fffdf7',
  defaultColor,
  defaultStrokeWidth = 4,
}: UseDrawingBoardOptions) {
  const [selectedToolKey, setSelectedToolKey] = useState<DrawingToolKey>('pencil')
  const [selectedColor, setSelectedColor] = useState(defaultColor)
  const [selectedOpacity, setSelectedOpacity] = useState(1)
  const [strokeWidth, setStrokeWidth] = useState(defaultStrokeWidth)
  const [lines, setLines] = useState<DrawingLine[]>([])
  const [redoLines, setRedoLines] = useState<DrawingLine[]>([])
  const [recentColors, setRecentColors] = useState<string[]>([])
  const [isDrawing, setIsDrawing] = useState(false)

  const isEraserSelected = selectedToolKey === 'eraser'
  const drawingColor = selectedColor
  const activeStrokeWidth = selectedToolKey === 'marker' ? strokeWidth + 4 : strokeWidth

  const clearDrawing = useCallback(() => {
    setRedoLines((currentRedoLines) => [...lines, ...currentRedoLines])
    setLines([])
  }, [lines])

  const undoDrawing = useCallback(() => {
    setLines((currentLines) => {
      const latestLine = currentLines[currentLines.length - 1]
      if (!latestLine) return currentLines
      setRedoLines((currentRedoLines) => [latestLine, ...currentRedoLines])
      return currentLines.slice(0, -1)
    })
  }, [])

  const redoDrawing = useCallback(() => {
    setRedoLines((currentRedoLines) => {
      const nextLine = currentRedoLines[0]
      if (!nextLine) return currentRedoLines
      setLines((currentLines) => [...currentLines, nextLine])
      return currentRedoLines.slice(1)
    })
  }, [])

  const replaceLines = useCallback((nextLines: DrawingLine[]) => {
    setLines(nextLines)
    setRedoLines([])
    setIsDrawing(false)
  }, [])

  const addRecentColor = useCallback((color: string) => {
    setRecentColors((currentRecentColors) => {
      const uniqueRecentColors = currentRecentColors.filter(
        (recentColor) => recentColor !== color,
      )

      return [color, ...uniqueRecentColors].slice(0, MAX_RECENT_COLOR_COUNT)
    })
  }, [])

  const beginDrawing = useCallback(
    (event: DrawingPointerEvent) => {
      const stage = event.target.getStage()
      const pointerPosition = stage?.getPointerPosition()
      if (!pointerPosition) return
      if (!isPointInsideDrawingArea(pointerPosition, boardSize, drawArea)) return

      if (selectedToolKey === 'bucket') {
        void createBucketFillLine({
          backgroundColor,
          boardSize,
          fillColor: selectedColor,
          fillOpacity: selectedOpacity,
          lines,
          pointerPosition,
        }).then((fillLine) => {
          if (!fillLine) return
          setRedoLines([])
          setLines((currentLines) => [...currentLines, fillLine])
          addRecentColor(selectedColor)
        })
        return
      }

      setIsDrawing(true)
      setRedoLines([])
      if (!isEraserSelected) {
        addRecentColor(drawingColor)
      }
      setLines((currentLines) => [
        ...currentLines,
        {
          id: `line-${Date.now()}-${currentLines.length}`,
          kind: 'stroke',
          color: drawingColor,
          strokeWidth: activeStrokeWidth,
          opacity: isEraserSelected ? 1 : selectedOpacity,
          compositeOperation: isEraserSelected ? 'destination-out' : 'source-over',
          points: [{ x: pointerPosition.x, y: pointerPosition.y }],
        },
      ])
    },
    [
      activeStrokeWidth,
      backgroundColor,
      boardSize,
      drawArea,
      drawingColor,
      addRecentColor,
      isEraserSelected,
      lines,
      selectedColor,
      selectedOpacity,
      selectedToolKey,
    ],
  )

  const continueDrawing = useCallback(
    (event: DrawingPointerEvent) => {
      if (!isDrawing) return

      const stage = event.target.getStage()
      const pointerPosition = stage?.getPointerPosition()
      if (!pointerPosition) return

      if (!isPointInsideDrawingArea(pointerPosition, boardSize, drawArea)) {
        setIsDrawing(false)
        return
      }

      setLines((currentLines) => {
        const latestLine = currentLines[currentLines.length - 1]
        if (!latestLine) return currentLines

        const updatedLine: DrawingLine = {
          ...latestLine,
          points: [...latestLine.points, { x: pointerPosition.x, y: pointerPosition.y }],
        }

        return [...currentLines.slice(0, -1), updatedLine]
      })
    },
    [boardSize, drawArea, isDrawing],
  )

  const endDrawing = useCallback(() => {
    setIsDrawing(false)
  }, [])

  return {
    selectedToolKey,
    selectedColor,
    selectedOpacity,
    strokeWidth,
    recentColors,
    lines,
    canUndoDrawing: lines.length > 0,
    canRedoDrawing: redoLines.length > 0,
    replaceLines,
    setLines,
    setSelectedToolKey,
    setSelectedColor,
    setSelectedOpacity,
    setStrokeWidth,
    clearDrawing,
    undoDrawing,
    redoDrawing,
    beginDrawing,
    continueDrawing,
    endDrawing,
  }
}
