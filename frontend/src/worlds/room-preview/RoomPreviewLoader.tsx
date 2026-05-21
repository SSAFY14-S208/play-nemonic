'use client'

import dynamic from 'next/dynamic'

const RoomPreviewCanvas = dynamic(() => import('./RoomPreviewCanvas'), {
  ssr: false,
})

export default function RoomPreviewLoader() {
  return <RoomPreviewCanvas />
}
