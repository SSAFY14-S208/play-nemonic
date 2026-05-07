import Image from 'next/image'
import {
  phoneDrawingBackLineBottom,
  phoneDrawingBackLineTop,
} from '@/shared/assets'

export function DrawingBackIcon() {
  return (
    <span className="relative block h-full w-full">
      <Image
        src={phoneDrawingBackLineTop}
        alt=""
        aria-hidden
        className="absolute left-[10%] top-1/2 h-[22%] w-[86%] origin-left -translate-y-1/2 rotate-[-45deg]"
      />
      <Image
        src={phoneDrawingBackLineBottom}
        alt=""
        aria-hidden
        className="absolute left-[10%] top-1/2 h-[22%] w-[86%] origin-left -translate-y-1/2 rotate-45"
      />
    </span>
  )
}
