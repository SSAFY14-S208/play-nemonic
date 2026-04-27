'use client'
import dynamic from 'next/dynamic'

const WorldCanvas = dynamic(() => import('./WorldCanvas'), { ssr: false })

export default function WorldLoader() {
  return <WorldCanvas />
}
