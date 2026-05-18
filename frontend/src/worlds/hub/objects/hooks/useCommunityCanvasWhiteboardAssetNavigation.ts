import { useLayoutEffect, useMemo, useRef, useState } from 'react'
import { useRouter } from 'next/navigation'
import { useThree } from '@react-three/fiber'
import * as THREE from 'three'
import { useHubRoomStore } from '@/shared/stores'
import { HUB_COMMUNITY_CANVAS_PATH } from '../../constants'
import { isCommunityCanvasWhiteboardMesh } from './useRoomModel'

const COMMUNITY_BOARD_FOCUS_KEY = 'communityBoard'

function setDocumentCursor(cursor: string) {
  if (typeof document === 'undefined') return

  document.body.style.cursor = cursor
}

function getCanvasPointer(
  event: PointerEvent | MouseEvent,
  canvasElement: HTMLCanvasElement,
  pointer: THREE.Vector2,
) {
  const canvasBounds = canvasElement.getBoundingClientRect()

  pointer.x = ((event.clientX - canvasBounds.left) / canvasBounds.width) * 2 - 1
  pointer.y = -((event.clientY - canvasBounds.top) / canvasBounds.height) * 2 + 1
}

export function useCommunityCanvasWhiteboardAssetNavigation(scene: THREE.Object3D) {
  const router = useRouter()
  const { camera, gl, invalidate } = useThree()
  const focusKey = useHubRoomStore((state) => state.focusKey)
  const setFocus = useHubRoomStore((state) => state.setFocus)
  const [isWhiteboardHovered, setIsWhiteboardHovered] = useState(false)
  const isHoveringWhiteboardRef = useRef(false)
  const raycaster = useMemo(() => new THREE.Raycaster(), [])
  const pointer = useMemo(() => new THREE.Vector2(), [])

  useLayoutEffect(() => {
    const canvasElement = gl.domElement
    const whiteboardMeshes: THREE.Mesh[] = []

    scene.traverse((child) => {
      if (!isCommunityCanvasWhiteboardMesh(child)) return

      whiteboardMeshes.push(child)
    })

    if (!whiteboardMeshes.length) return

    const getWhiteboardHit = (event: PointerEvent | MouseEvent) => {
      scene.updateWorldMatrix(true, true)
      getCanvasPointer(event, canvasElement, pointer)
      raycaster.setFromCamera(pointer, camera)

      return raycaster.intersectObjects(whiteboardMeshes, false)[0] ?? null
    }

    const setWhiteboardHovered = (isHovered: boolean) => {
      if (isHoveringWhiteboardRef.current === isHovered) return

      isHoveringWhiteboardRef.current = isHovered
      setDocumentCursor(isHovered ? 'pointer' : '')
      setIsWhiteboardHovered(isHovered)
      invalidate()
    }

    const handlePointerMove = (event: PointerEvent) => {
      const whiteboardHit = getWhiteboardHit(event)

      if (whiteboardHit) {
        setWhiteboardHovered(true)
        return
      }

      setWhiteboardHovered(false)
    }

    const handleClick = (event: MouseEvent) => {
      const whiteboardHit = getWhiteboardHit(event)
      if (!whiteboardHit) return

      event.preventDefault()
      event.stopPropagation()
      setWhiteboardHovered(false)

      if (focusKey !== COMMUNITY_BOARD_FOCUS_KEY) {
        setFocus(COMMUNITY_BOARD_FOCUS_KEY)
        invalidate()
        return
      }

      router.push(HUB_COMMUNITY_CANVAS_PATH)
    }

    const handlePointerLeave = () => {
      setWhiteboardHovered(false)
    }

    canvasElement.addEventListener('pointermove', handlePointerMove, true)
    canvasElement.addEventListener('pointerleave', handlePointerLeave)
    canvasElement.addEventListener('click', handleClick, true)

    return () => {
      isHoveringWhiteboardRef.current = false
      setDocumentCursor('')
      canvasElement.removeEventListener('pointermove', handlePointerMove, true)
      canvasElement.removeEventListener('pointerleave', handlePointerLeave)
      canvasElement.removeEventListener('click', handleClick, true)
    }
  }, [
    camera,
    focusKey,
    gl,
    invalidate,
    pointer,
    raycaster,
    router,
    scene,
    setFocus,
  ])

  return isWhiteboardHovered
}
