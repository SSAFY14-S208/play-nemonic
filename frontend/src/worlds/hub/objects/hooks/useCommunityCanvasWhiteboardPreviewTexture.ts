import { useEffect, useMemo, useRef } from 'react'
import { useThree } from '@react-three/fiber'
import * as THREE from 'three'
import type { CommunityCanvasWhiteboardPreviewMemo } from './useCommunityCanvasWhiteboardPreviewMemos'

const WHITEBOARD_PREVIEW_CANVAS_WIDTH = 512
const WHITEBOARD_PREVIEW_CANVAS_HEIGHT = 312
const WHITEBOARD_PREVIEW_PADDING = 18
const WHITEBOARD_PREVIEW_MEMO_SIZE = 64
const WHITEBOARD_PREVIEW_SAFE_PERCENT = 8
const WHITEBOARD_PREVIEW_MAX_STACK_Z_INDEX = 999
const WHITEBOARD_PREVIEW_SHOW_ALIGNMENT_BORDER = false

function getPreviewMemoStackOrder(memo: CommunityCanvasWhiteboardPreviewMemo) {
  return Math.min(Math.max(memo.zIndex, 0), WHITEBOARD_PREVIEW_MAX_STACK_Z_INDEX)
}

function getMemoCanvasPosition(memo: CommunityCanvasWhiteboardPreviewMemo) {
  const xPercent = Math.min(
    Math.max(memo.xPercent, WHITEBOARD_PREVIEW_SAFE_PERCENT),
    100 - WHITEBOARD_PREVIEW_SAFE_PERCENT,
  )
  const yPercent = Math.min(
    Math.max(memo.yPercent, WHITEBOARD_PREVIEW_SAFE_PERCENT),
    100 - WHITEBOARD_PREVIEW_SAFE_PERCENT,
  )

  return {
    x:
      WHITEBOARD_PREVIEW_PADDING +
      ((WHITEBOARD_PREVIEW_CANVAS_WIDTH - WHITEBOARD_PREVIEW_PADDING * 2) *
        xPercent) /
        100,
    y:
      WHITEBOARD_PREVIEW_PADDING +
      ((WHITEBOARD_PREVIEW_CANVAS_HEIGHT - WHITEBOARD_PREVIEW_PADDING * 2) *
        yPercent) /
        100,
  }
}

function roundRect(
  context: CanvasRenderingContext2D,
  x: number,
  y: number,
  width: number,
  height: number,
  radius: number,
) {
  context.beginPath()
  context.moveTo(x + radius, y)
  context.lineTo(x + width - radius, y)
  context.quadraticCurveTo(x + width, y, x + width, y + radius)
  context.lineTo(x + width, y + height - radius)
  context.quadraticCurveTo(x + width, y + height, x + width - radius, y + height)
  context.lineTo(x + radius, y + height)
  context.quadraticCurveTo(x, y + height, x, y + height - radius)
  context.lineTo(x, y + radius)
  context.quadraticCurveTo(x, y, x + radius, y)
  context.closePath()
}

function drawMemoPlaceholder(
  context: CanvasRenderingContext2D,
  memo: CommunityCanvasWhiteboardPreviewMemo,
  image: HTMLImageElement | null,
) {
  const halfSize = WHITEBOARD_PREVIEW_MEMO_SIZE / 2
  const { x, y } = getMemoCanvasPosition(memo)

  context.save()
  context.translate(x, y)
  context.rotate((memo.rotationDeg * Math.PI) / 180)

  context.shadowBlur = 10
  context.shadowColor = 'rgb(64 44 38 / 0.18)'
  context.shadowOffsetY = 7
  context.fillStyle = memo.color
  roundRect(
    context,
    -halfSize,
    -halfSize,
    WHITEBOARD_PREVIEW_MEMO_SIZE,
    WHITEBOARD_PREVIEW_MEMO_SIZE,
    5,
  )
  context.fill()

  context.shadowBlur = 0
  context.shadowColor = 'transparent'
  context.fillStyle = 'rgb(255 255 255 / 0.22)'
  roundRect(
    context,
    -halfSize + 9,
    -halfSize + 11,
    WHITEBOARD_PREVIEW_MEMO_SIZE - 18,
    WHITEBOARD_PREVIEW_MEMO_SIZE - 21,
    4,
  )
  context.fill()

  if (image) {
    const imageBoxSize = WHITEBOARD_PREVIEW_MEMO_SIZE - 22
    const imageRatio = image.naturalWidth / image.naturalHeight
    const drawWidth =
      imageRatio > 1 ? imageBoxSize : imageBoxSize * Math.max(imageRatio, 0.2)
    const drawHeight =
      imageRatio > 1 ? imageBoxSize / imageRatio : imageBoxSize

    context.drawImage(
      image,
      -drawWidth / 2,
      -drawHeight / 2 + 2,
      drawWidth,
      drawHeight,
    )
  } else {
    context.fillStyle = 'rgb(255 255 255 / 0.68)'
    roundRect(context, -18, -9, 36, 5, 3)
    context.fill()
    context.fillStyle = 'rgb(255 255 255 / 0.52)'
    roundRect(context, -18, 2, 28, 5, 3)
    context.fill()
    context.fillStyle = 'rgb(255 255 255 / 0.42)'
    roundRect(context, -18, 13, 20, 5, 3)
    context.fill()
  }

  context.restore()
}

function drawCallToAction(context: CanvasRenderingContext2D) {
  const width = 270
  const height = 42
  const x = (WHITEBOARD_PREVIEW_CANVAS_WIDTH - width) / 2
  const y = WHITEBOARD_PREVIEW_CANVAS_HEIGHT - height - 18

  context.save()
  context.fillStyle = 'rgb(255 255 255 / 0.82)'
  context.shadowBlur = 14
  context.shadowColor = 'rgb(76 54 92 / 0.16)'
  context.shadowOffsetY = 8
  roundRect(context, x, y, width, height, height / 2)
  context.fill()

  context.shadowBlur = 0
  context.shadowColor = 'transparent'
  context.shadowOffsetX = 0
  context.shadowOffsetY = 0
  context.fillStyle = '#66547f'
  context.font = '600 25px sans-serif'
  context.textAlign = 'center'
  context.textBaseline = 'middle'
  context.fillText(
    '커뮤니티 보드 가기  →',
    WHITEBOARD_PREVIEW_CANVAS_WIDTH / 2,
    y + height / 2 + 1,
  )
  context.restore()
}

function drawAlignmentBorder(context: CanvasRenderingContext2D) {
  if (!WHITEBOARD_PREVIEW_SHOW_ALIGNMENT_BORDER) return

  context.save()
  context.strokeStyle = 'rgb(255 82 82 / 0.9)'
  context.lineWidth = 5
  context.setLineDash([14, 10])
  context.strokeRect(
    3,
    3,
    WHITEBOARD_PREVIEW_CANVAS_WIDTH - 6,
    WHITEBOARD_PREVIEW_CANVAS_HEIGHT - 6,
  )

  const safeAreaX =
    (WHITEBOARD_PREVIEW_CANVAS_WIDTH * WHITEBOARD_PREVIEW_SAFE_PERCENT) / 100
  const safeAreaY =
    (WHITEBOARD_PREVIEW_CANVAS_HEIGHT * WHITEBOARD_PREVIEW_SAFE_PERCENT) / 100

  context.strokeStyle = 'rgb(79 209 255 / 0.9)'
  context.lineWidth = 4
  context.setLineDash([8, 7])
  context.strokeRect(
    safeAreaX,
    safeAreaY,
    WHITEBOARD_PREVIEW_CANVAS_WIDTH - safeAreaX * 2,
    WHITEBOARD_PREVIEW_CANVAS_HEIGHT - safeAreaY * 2,
  )
  context.restore()
}

function drawWhiteboardPreview(
  canvas: HTMLCanvasElement,
  isExpanded: boolean,
  previewMemos: CommunityCanvasWhiteboardPreviewMemo[],
  loadedImages: Map<string, HTMLImageElement>,
) {
  const context = canvas.getContext('2d')
  if (!context) return

  context.clearRect(0, 0, canvas.width, canvas.height)

  const sortedMemos = [...previewMemos].sort(
    (firstMemo, secondMemo) =>
      getPreviewMemoStackOrder(firstMemo) - getPreviewMemoStackOrder(secondMemo),
  )

  sortedMemos.forEach((memo) => {
    drawMemoPlaceholder(context, memo, loadedImages.get(memo.id) ?? null)
  })

  if (isExpanded) {
    drawCallToAction(context)
  }

  drawAlignmentBorder(context)
}

function canUseCanvasImage(imageUrl: string | null) {
  if (!imageUrl || typeof window === 'undefined') return false
  if (imageUrl.startsWith('data:') || imageUrl.startsWith('blob:')) return true
  if (imageUrl.startsWith('/')) return true

  try {
    return (
      new URL(imageUrl, window.location.href).origin === window.location.origin
    )
  } catch {
    return false
  }
}

function loadCanvasImage(imageUrl: string) {
  return new Promise<HTMLImageElement | null>((resolve) => {
    const image = new window.Image()

    image.crossOrigin = 'anonymous'
    image.onload = () => resolve(image)
    image.onerror = () => resolve(null)
    image.src = imageUrl
  })
}

export function useCommunityCanvasWhiteboardPreviewTexture({
  isExpanded,
  previewMemos,
}: {
  isExpanded: boolean
  previewMemos: CommunityCanvasWhiteboardPreviewMemo[]
}) {
  const invalidate = useThree((state) => state.invalidate)
  const textureRef = useRef<THREE.CanvasTexture>(null)
  const canvas = useMemo(() => {
    if (typeof document === 'undefined') return null

    const previewCanvas = document.createElement('canvas')
    previewCanvas.width = WHITEBOARD_PREVIEW_CANVAS_WIDTH
    previewCanvas.height = WHITEBOARD_PREVIEW_CANVAS_HEIGHT

    return previewCanvas
  }, [])

  useEffect(() => {
    if (!canvas) return

    let cancelled = false
    const loadedImages = new Map<string, HTMLImageElement>()

    drawWhiteboardPreview(canvas, isExpanded, previewMemos, loadedImages)
    if (textureRef.current) {
      textureRef.current.needsUpdate = true
    }
    invalidate()

    ;(async () => {
      const imageEntries = await Promise.all(
        previewMemos
          .filter((memo) => canUseCanvasImage(memo.imageUrl))
          .map(async (memo) => {
            const image = await loadCanvasImage(memo.imageUrl ?? '')

            return image ? ([memo.id, image] as const) : null
          }),
      )

      if (cancelled) return

      imageEntries.forEach((entry) => {
        if (!entry) return

        loadedImages.set(entry[0], entry[1])
      })
      drawWhiteboardPreview(canvas, isExpanded, previewMemos, loadedImages)
      if (textureRef.current) {
        textureRef.current.needsUpdate = true
      }
      invalidate()
    })()

    return () => {
      cancelled = true
    }
  }, [canvas, invalidate, isExpanded, previewMemos])

  return { canvas, textureRef }
}
