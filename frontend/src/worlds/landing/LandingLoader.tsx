'use client'
import dynamic from 'next/dynamic'

const LandingCanvas = dynamic(() => import('./LandingCanvas'), { ssr: false })
const LandingWelcomeOverlay = dynamic(
  () => import('./LandingWelcomeOverlay'),
  { ssr: false },
)

export default function LandingLoader() {
  return (
    <>
      <LandingCanvas />
      <LandingWelcomeOverlay />
    </>
  )
}
