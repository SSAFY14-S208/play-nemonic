import { memo } from 'react'
import Lottie from 'lottie-react'
import pinkHouseAnimation from '@/shared/assets/lotties/pinkHouse.json'

function HubLoadingHouseLottie() {
  return (
    <Lottie
      animationData={pinkHouseAnimation}
      aria-hidden
      autoplay
      className="pointer-events-none h-28 w-28 [transform:translateZ(0)] [will-change:transform]"
      loop
    />
  )
}

export default memo(HubLoadingHouseLottie)
