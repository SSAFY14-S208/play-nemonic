'use client'
import dynamic from 'next/dynamic'

const HubCanvas = dynamic(() => import('./HubCanvas'), { ssr: false })

export default function HubLoader() {
  return <HubCanvas />
}
