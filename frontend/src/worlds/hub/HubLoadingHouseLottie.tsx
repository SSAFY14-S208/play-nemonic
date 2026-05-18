import { memo } from 'react'
import Image from 'next/image'

// Static Play! Nemonic brand logo — replaces the Lottie animation that
// previously played here. Lottie redraws SVG every frame on the main thread,
// which competed with the bar fill rAF/GLB parsing and made the loading
// screen feel laggy.
function HubLoadingHouseLottie() {
  return (
    <Image
      src="/images/play-nemonic-logo.png"
      alt="Play! Nemonic"
      width={1672}
      height={941}
      priority
      draggable={false}
      className="pointer-events-none h-28 w-auto"
    />
  )
}

export default memo(HubLoadingHouseLottie)
