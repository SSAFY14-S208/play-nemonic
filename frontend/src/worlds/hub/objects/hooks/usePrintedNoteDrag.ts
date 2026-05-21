import { useCallback, useEffect, useMemo, useRef } from 'react'
import { useFrame, useLoader, useThree } from '@react-three/fiber'
import type { ThreeEvent } from '@react-three/fiber'
import * as THREE from 'three'
import {
  HUB_PEGBOARD_SURFACE,
  HUB_WORKSPACE_SURFACE,
} from '@/shared/constants'
import { useHubPrintStore } from '@/shared/stores'
import type { NoteSurface, PrintedNote } from '@/shared/types'
import { trackHubInvalidate } from '@/shared/utils'

const FALLBACK_NOTE_TEXTURE =
  'data:image/svg+xml;utf8,' +
  encodeURIComponent(`
    <svg xmlns="http://www.w3.org/2000/svg" width="512" height="384" viewBox="0 0 512 384">
      <rect width="512" height="384" rx="32" fill="#fff3a8"/>
      <text x="50%" y="47%" dominant-baseline="middle" text-anchor="middle"
        font-family="sans-serif" font-size="42" font-weight="700" fill="#51456f">NEMONIC</text>
      <text x="50%" y="61%" dominant-baseline="middle" text-anchor="middle"
        font-family="sans-serif" font-size="24" fill="#766b89">printed memo</text>
    </svg>
  `)

function setDocumentCursor(cursor: string) {
  if (typeof document === 'undefined') return
  document.body.style.cursor = cursor
}

function clamp(value: number, minimum: number, maximum: number) {
  return Math.min(Math.max(value, minimum), maximum)
}

function snapToGrid(value: number, gridSize: number, origin: number) {
  return origin + Math.round((value - origin) / gridSize) * gridSize
}

function isInsidePegboard(point: THREE.Vector3) {
  return (
    point.x >= HUB_PEGBOARD_SURFACE.minimumX &&
    point.x <= HUB_PEGBOARD_SURFACE.maximumX &&
    point.y >= HUB_PEGBOARD_SURFACE.minimumY &&
    point.y <= HUB_PEGBOARD_SURFACE.maximumY
  )
}

function getWorkspacePose(point: THREE.Vector3, rotationZ: number) {
  return {
    position: [
      clamp(
        point.x,
        HUB_WORKSPACE_SURFACE.minimumX,
        HUB_WORKSPACE_SURFACE.maximumX,
      ),
      HUB_WORKSPACE_SURFACE.y + HUB_WORKSPACE_SURFACE.zOffset,
      clamp(
        point.z,
        HUB_WORKSPACE_SURFACE.minimumZ,
        HUB_WORKSPACE_SURFACE.maximumZ,
      ),
    ] satisfies [number, number, number],
    rotation: [-Math.PI / 2, 0, rotationZ] satisfies [number, number, number],
  }
}

function getPegboardPose(point: THREE.Vector3, rotationZ: number) {
  return {
    position: [
      clamp(
        point.x,
        HUB_PEGBOARD_SURFACE.minimumX,
        HUB_PEGBOARD_SURFACE.maximumX,
      ),
      clamp(
        point.y,
        HUB_PEGBOARD_SURFACE.minimumY,
        HUB_PEGBOARD_SURFACE.maximumY,
      ),
      HUB_PEGBOARD_SURFACE.z + HUB_PEGBOARD_SURFACE.zOffset,
    ] satisfies [number, number, number],
    rotation: [0, 0, rotationZ] satisfies [number, number, number],
  }
}

function getSnappedWorkspacePose(position: THREE.Vector3, rotationZ: number) {
  const snappedX = snapToGrid(
    clamp(
      position.x,
      HUB_WORKSPACE_SURFACE.minimumX,
      HUB_WORKSPACE_SURFACE.maximumX,
    ),
    HUB_WORKSPACE_SURFACE.snapSize,
    HUB_WORKSPACE_SURFACE.minimumX,
  )
  const snappedZ = snapToGrid(
    clamp(
      position.z,
      HUB_WORKSPACE_SURFACE.minimumZ,
      HUB_WORKSPACE_SURFACE.maximumZ,
    ),
    HUB_WORKSPACE_SURFACE.snapSize,
    HUB_WORKSPACE_SURFACE.minimumZ,
  )

  return {
    position: [
      clamp(
        snappedX,
        HUB_WORKSPACE_SURFACE.minimumX,
        HUB_WORKSPACE_SURFACE.maximumX,
      ),
      HUB_WORKSPACE_SURFACE.y + HUB_WORKSPACE_SURFACE.zOffset,
      clamp(
        snappedZ,
        HUB_WORKSPACE_SURFACE.minimumZ,
        HUB_WORKSPACE_SURFACE.maximumZ,
      ),
    ] satisfies [number, number, number],
    rotation: [-Math.PI / 2, 0, rotationZ] satisfies [number, number, number],
  }
}

function getSnappedPegboardPose(position: THREE.Vector3, rotationZ: number) {
  const snappedX = snapToGrid(
    clamp(
      position.x,
      HUB_PEGBOARD_SURFACE.minimumX,
      HUB_PEGBOARD_SURFACE.maximumX,
    ),
    HUB_PEGBOARD_SURFACE.snapSize,
    HUB_PEGBOARD_SURFACE.minimumX,
  )
  const snappedY = snapToGrid(
    clamp(
      position.y,
      HUB_PEGBOARD_SURFACE.minimumY,
      HUB_PEGBOARD_SURFACE.maximumY,
    ),
    HUB_PEGBOARD_SURFACE.snapSize,
    HUB_PEGBOARD_SURFACE.minimumY,
  )

  return {
    position: [
      clamp(
        snappedX,
        HUB_PEGBOARD_SURFACE.minimumX,
        HUB_PEGBOARD_SURFACE.maximumX,
      ),
      clamp(
        snappedY,
        HUB_PEGBOARD_SURFACE.minimumY,
        HUB_PEGBOARD_SURFACE.maximumY,
      ),
      HUB_PEGBOARD_SURFACE.z + HUB_PEGBOARD_SURFACE.zOffset,
    ] satisfies [number, number, number],
    rotation: [0, 0, rotationZ] satisfies [number, number, number],
  }
}

export function usePrintedNoteDrag(note: PrintedNote) {
  const meshRef = useRef<THREE.Mesh>(null)
  const dropSurfaceRef = useRef<NoteSurface>(
    note.surface === 'pegboard' ? 'pegboard' : 'workspace',
  )
  const workspacePlane = useMemo(
    () =>
      new THREE.Plane(
        new THREE.Vector3(0, 1, 0),
        -(HUB_WORKSPACE_SURFACE.y + HUB_WORKSPACE_SURFACE.zOffset),
      ),
    [],
  )
  const pegboardPlane = useMemo(
    () =>
      new THREE.Plane(
        new THREE.Vector3(0, 0, 1),
        -(HUB_PEGBOARD_SURFACE.z + HUB_PEGBOARD_SURFACE.zOffset),
      ),
    [],
  )
  const workspaceHitPoint = useMemo(() => new THREE.Vector3(), [])
  const pegboardHitPoint = useMemo(() => new THREE.Vector3(), [])
  const loadedNoteTexture = useLoader(
    THREE.TextureLoader,
    note.imageDataUrl ?? FALLBACK_NOTE_TEXTURE,
  )
  const noteTexture = useMemo(() => {
    const clonedTexture = loadedNoteTexture.clone()
    clonedTexture.colorSpace = THREE.SRGBColorSpace
    clonedTexture.needsUpdate = true

    return clonedTexture
  }, [loadedNoteTexture])
  const { camera, invalidate, pointer, raycaster } = useThree()
  const attachNote = useHubPrintStore((state) => state.attachNote)
  const isDragging = useHubPrintStore(
    (state) => state.draggingNoteId === note.id,
  )
  const setDraggingNoteId = useHubPrintStore((state) => state.setDraggingNoteId)
  const rotationZ = note.rotation[2] ?? -0.06

  useEffect(() => {
    invalidate()

    return () => {
      noteTexture.dispose()
    }
  }, [invalidate, noteTexture])

  useFrame(() => {
    const mesh = meshRef.current

    if (!isDragging || !mesh) return

    raycaster.setFromCamera(pointer, camera)

    const pegboardHit = raycaster.ray.intersectPlane(
      pegboardPlane,
      pegboardHitPoint,
    )

    if (pegboardHit && isInsidePegboard(pegboardHit)) {
      const pegboardPose = getPegboardPose(pegboardHit, rotationZ)

      dropSurfaceRef.current = 'pegboard'
      mesh.position.set(...pegboardPose.position)
      mesh.rotation.set(...pegboardPose.rotation)
      trackHubInvalidate('printedNote.dragFrame.pegboard')
      invalidate()
      return
    }

    const workspaceHit = raycaster.ray.intersectPlane(
      workspacePlane,
      workspaceHitPoint,
    )

    if (!workspaceHit) return

    const workspacePose = getWorkspacePose(workspaceHit, rotationZ)

    dropSurfaceRef.current = 'workspace'
    mesh.position.set(...workspacePose.position)
    mesh.rotation.set(...workspacePose.rotation)
    trackHubInvalidate('printedNote.dragFrame.workspace')
    invalidate()
  })

  const handlePointerDown = useCallback(
    (event: ThreeEvent<PointerEvent>) => {
      event.stopPropagation()
      ;(event.target as HTMLElement).setPointerCapture(event.pointerId)
      setDocumentCursor('grabbing')
      setDraggingNoteId(note.id)
      trackHubInvalidate('printedNote.pointerDown')
      invalidate()
    },
    [invalidate, note.id, setDraggingNoteId],
  )

  const handlePointerEnter = useCallback(() => {
    setDocumentCursor(isDragging ? 'grabbing' : 'grab')
  }, [isDragging])

  const handlePointerLeave = useCallback(() => {
    if (!isDragging) {
      setDocumentCursor('')
    }
  }, [isDragging])

  const handlePointerUp = useCallback(
    (event: ThreeEvent<PointerEvent>) => {
      event.stopPropagation()
      ;(event.target as HTMLElement).releasePointerCapture(event.pointerId)
      setDocumentCursor('')

      const currentPosition = meshRef.current?.position ?? new THREE.Vector3()
      const snappedPose =
        dropSurfaceRef.current === 'pegboard'
          ? getSnappedPegboardPose(currentPosition, rotationZ)
          : getSnappedWorkspacePose(currentPosition, rotationZ)

      attachNote(
        note.id,
        dropSurfaceRef.current,
        snappedPose.position,
        snappedPose.rotation,
      )
      trackHubInvalidate('printedNote.pointerUp')
      invalidate()
    },
    [attachNote, invalidate, note.id, rotationZ],
  )

  return {
    handlePointerDown,
    handlePointerEnter,
    handlePointerLeave,
    handlePointerUp,
    meshRef,
    noteTexture,
  }
}
