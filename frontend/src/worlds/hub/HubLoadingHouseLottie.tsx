import { memo, useCallback, useRef } from 'react'
import Lottie, {
  type LottieRefCurrentProps,
} from 'lottie-react'
import pinkHouseAnimation from '@/shared/assets/lotties/pinkHouse.json'

const HUB_LOADING_HOUSE_FRAME = 42

function HubLoadingHouseLottie() {
  const lottieRef = useRef<LottieRefCurrentProps | null>(null)

  const pinFirstFrame = useCallback(() => {
    lottieRef.current?.setSubframe(false)
    lottieRef.current?.goToAndStop(HUB_LOADING_HOUSE_FRAME, true)
  }, [])

  return (
    <Lottie
      animationData={pinkHouseAnimation}
      aria-hidden
      autoplay={false}
      className="pointer-events-none h-28 w-28"
      lottieRef={lottieRef}
      loop={false}
      onDOMLoaded={pinFirstFrame}
      rendererSettings={{ progressiveLoad: true }}
    />
  )
}

export default memo(HubLoadingHouseLottie)
