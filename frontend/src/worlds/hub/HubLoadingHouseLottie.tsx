import { memo } from 'react'
import Lottie from 'lottie-react'
import pinkHouseAnimation from '@/shared/assets/lotties/pinkHouse.json'

function HubLoadingHouseLottie() {
  return (
    <Lottie
      animationData={pinkHouseAnimation}
      aria-hidden
      autoplay
      className="pointer-events-none h-28 w-28"
      loop
      rendererSettings={{ progressiveLoad: true }}
    />
  )
}

export default memo(HubLoadingHouseLottie)
