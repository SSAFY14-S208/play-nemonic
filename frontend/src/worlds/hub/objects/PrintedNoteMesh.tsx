import { memo } from 'react'
import * as THREE from 'three'
import type { PrintedNote } from '@/shared/types'
import { usePrintedNoteDrag } from './hooks'

function PrintedNoteMesh({ note }: { note: PrintedNote }) {
  const {
    handlePointerDown,
    handlePointerEnter,
    handlePointerLeave,
    handlePointerUp,
    meshRef,
    noteTexture,
  } = usePrintedNoteDrag(note)

  return (
    <mesh
      ref={meshRef}
      position={note.position}
      rotation={note.rotation}
      onPointerDown={handlePointerDown}
      onPointerEnter={handlePointerEnter}
      onPointerLeave={handlePointerLeave}
      onPointerUp={handlePointerUp}
    >
      <planeGeometry args={[0.46, 0.34]} />
      <meshStandardMaterial
        map={noteTexture}
        polygonOffset
        polygonOffsetFactor={-1}
        roughness={0.62}
        side={THREE.DoubleSide}
      />
    </mesh>
  )
}

export default memo(PrintedNoteMesh)
