'use client'

import { useUserBootstrap } from '@/shared/hooks'

// 루트 레이아웃에 1회 마운트되어 익명 UUID 부트스트랩을 트리거한다.
export function UserBootstrap() {
  useUserBootstrap()
  return null
}
