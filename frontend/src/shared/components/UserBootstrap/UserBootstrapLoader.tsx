'use client'

import dynamic from 'next/dynamic'

// dynamic + ssr:false로 클라이언트에서만 평가되도록 격리.
// zustand persist 미들웨어가 prerender 단계에서 평가되어 깨지는 문제를 차단한다.
const UserBootstrap = dynamic(
  () => import('./UserBootstrap').then((mod) => ({ default: mod.UserBootstrap })),
  { ssr: false },
)

export function UserBootstrapLoader() {
  return <UserBootstrap />
}
