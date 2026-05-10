'use client'

import { useEffect, useState } from 'react'

// 폰 디자인 사이즈. 자식 요소들이 이 크기를 전제로 px/rem이 박혀 있어
// 폰 frame을 가변으로 두면 비율이 깨진다. 디자인 사이즈는 고정하고
// viewport에 맞춰 wrapper만 transform: scale로 비례 축소시킨다.
const PHONE_DESIGN_WIDTH = 393
const PHONE_DESIGN_HEIGHT = 815
const VIEWPORT_PADDING_X = 24 // 1.5rem (좌우 여백)
const VIEWPORT_PADDING_Y = 32 // 2rem (상하 여백)

export function usePhoneScale() {
  const [scale, setScale] = useState(1)
  useEffect(() => {
    const recompute = () => {
      const availW = window.innerWidth - VIEWPORT_PADDING_X
      const availH = window.innerHeight - VIEWPORT_PADDING_Y
      setScale(
        Math.min(1, availW / PHONE_DESIGN_WIDTH, availH / PHONE_DESIGN_HEIGHT),
      )
    }
    recompute()
    window.addEventListener('resize', recompute)
    return () => window.removeEventListener('resize', recompute)
  }, [])
  return scale
}
