'use client'

import { useCallback, useRef, useState } from 'react'
import { useRouter } from 'next/navigation'
import type Konva from 'konva'
import type { KonvaEventObject } from 'konva/lib/Node'
import { ApiError } from '@/shared/apis'
import {
  writeCommunityCanvasHandoffDraft, writeNemonicRoomPrintDraft, } from '@/shared/utils'
import { PHONE_BRUSH_SIZES, PHONE_DRAWING_COLORS, PHONE_DRAWING_PAPER_COLOR } from '../constants'
import { usePhoneStore, type PhoneDrawingToolKey, type PhoneDrawLine } from '..'

import { dataUrlToBlob, uploadDrawingArtifact } from '../utils'

function createPhoneLineId() {
  if (typeof window !== 'undefined' && window.crypto?.randomUUID) {
    return window.crypto.randomUUID()
  }

  return `phone-line-${Date.now()}`
}

function getStagePointerPosition(event: KonvaEventObject<MouseEvent | TouchEvent>) {
  return event.target.getStage()?.getPointerPosition() ?? null
}

function isNemonicRoomPath() {
  if (typeof window === 'undefined') return false

  return (
    window.location.pathname === '/nemonic' ||
    window.location.pathname.startsWith('/nemonic/')
  )
}

export function usePhoneDrawing() {
  const router = useRouter()
  const stageRef = useRef<Konva.Stage>(null)
  const isDrawingRef = useRef(false)
  const [activeTool, setActiveTool] = useState<PhoneDrawingToolKey>('pen')
  const [brushSizes, setBrushSizes] = useState<
    Record<PhoneDrawingToolKey, number>
  >({
    eraser: PHONE_BRUSH_SIZES[1],
    pen: PHONE_BRUSH_SIZES[1],
  })
  const [selectedColor, setSelectedColor] = useState(PHONE_DRAWING_COLORS[0])
  const [lines, setLines] = useState<PhoneDrawLine[]>([])
  const [redoLines, setRedoLines] = useState<PhoneDrawLine[]>([])

  const isSaving = usePhoneStore((state) => state.isSavingDrawing)
  const setSavingDrawing = usePhoneStore((state) => state.setSavingDrawing)
  const addDrawingArtifact = usePhoneStore((state) => state.addDrawingArtifact)
  const setToast = usePhoneStore((state) => state.setToast)

  const brushSize = brushSizes[activeTool]
  const hasDrawing = lines.length > 0

  const getDrawingImageDataUrl = useCallback(
    () => stageRef.current?.toDataURL({ pixelRatio: 2 }) ?? null,
    [],
  )

  const setBrushSize = useCallback(
    (nextBrushSize: number) => {
      setBrushSizes((currentBrushSizes) => ({
        ...currentBrushSizes,
        [activeTool]: nextBrushSize,
      }))
    },
    [activeTool],
  )

  const selectColor = useCallback((nextColor: string) => {
    setSelectedColor(nextColor)
    setActiveTool('pen')
  }, [])

  const startDrawing = useCallback(
    (event: KonvaEventObject<MouseEvent | TouchEvent>) => {
      const pointerPosition = getStagePointerPosition(event)
      if (!pointerPosition) return

      isDrawingRef.current = true
      setRedoLines([])
      setLines((currentLines) => [
        ...currentLines,
        {
          id: createPhoneLineId(),
          color:
            activeTool === 'eraser' ? PHONE_DRAWING_PAPER_COLOR : selectedColor,
          points: [pointerPosition.x, pointerPosition.y],
          strokeWidth: activeTool === 'eraser' ? brushSize * 1.75 : brushSize,
          tool: activeTool,
        },
      ])
    },
    [activeTool, brushSize, selectedColor],
  )

  const draw = useCallback((event: KonvaEventObject<MouseEvent | TouchEvent>) => {
    if (!isDrawingRef.current) return

    const pointerPosition = getStagePointerPosition(event)
    if (!pointerPosition) return

    setLines((currentLines) => {
      const lastLine = currentLines.at(-1)
      if (!lastLine) return currentLines

      return [
        ...currentLines.slice(0, -1),
        {
          ...lastLine,
          points: [...lastLine.points, pointerPosition.x, pointerPosition.y],
        },
      ]
    })
  }, [])

  const endDrawing = useCallback(() => {
    isDrawingRef.current = false
  }, [])

  const clearDrawing = useCallback(() => {
    setLines([])
    setRedoLines([])
  }, [])

  const undoDrawing = useCallback(() => {
    setLines((currentLines) => {
      const removedLine = currentLines.at(-1)
      if (!removedLine) return currentLines

      setRedoLines((currentRedoLines) => [removedLine, ...currentRedoLines])
      return currentLines.slice(0, -1)
    })
  }, [])

  const redoDrawing = useCallback(() => {
    setRedoLines((currentRedoLines) => {
      const restoredLine = currentRedoLines[0]
      if (!restoredLine) return currentRedoLines

      setLines((currentLines) => [...currentLines, restoredLine])
      return currentRedoLines.slice(1)
    })
  }, [])

  const createArtifact = useCallback(
    async (action: 'save' | 'print') => {
      if (isSaving) return

      if (!hasDrawing) {
        if (action === 'print') {
          setToast('커뮤니티에 붙일 그림이 없어요.')
        }
        return
      }

      const imageDataUrl = getDrawingImageDataUrl()
      if (!imageDataUrl) return

      setSavingDrawing(true)
      try {
        const blob = await dataUrlToBlob(imageDataUrl)
        const saveResponse = await uploadDrawingArtifact(blob)
        addDrawingArtifact({ saveResponse, imageDataUrl, action })
        clearDrawing()

        if (action === 'print') {
          if (isNemonicRoomPath()) {
            writeNemonicRoomPrintDraft({
              sourceKind: 'PHONE_DRAWING',
              title: '내가 그린 메모',
              imageUrl: imageDataUrl,
              thumbnailUrl: imageDataUrl,
              sourceGalleryId: saveResponse.galleryId,
              sourceContentKind: 'phone',
            })
            return
          }

          const printImageUrl = saveResponse.thumbnailUrl || imageDataUrl

          writeCommunityCanvasHandoffDraft({
            sourceKind: 'GALLERY',
            title: '내가 그린 메모',
            imageUrl: printImageUrl,
            thumbnailUrl: printImageUrl,
            sourceGalleryId: saveResponse.galleryId,
            sourceContentKind: 'phone',
          })
          router.push('/community-canvas')
        }
      } catch (error) {
        const message =
          error instanceof ApiError
            ? error.message
            : '저장에 실패했어요. 잠시 후 다시 시도해주세요.'
        setToast(message)
      } finally {
        setSavingDrawing(false)
      }
    },
    [
      addDrawingArtifact,
      clearDrawing,
      getDrawingImageDataUrl,
      hasDrawing,
      isSaving,
      router,
      setSavingDrawing,
      setToast,
    ],
  )

  return {
    activeTool,
    brushSize,
    clearDrawing,
    createArtifact,
    draw,
    endDrawing,
    getDrawingImageDataUrl,
    hasDrawing,
    isSaving,
    lines,
    redoDrawing,
    redoLines,
    selectColor,
    selectedColor,
    setActiveTool,
    setBrushSize,
    stageRef,
    startDrawing,
    undoDrawing,
  }
}
