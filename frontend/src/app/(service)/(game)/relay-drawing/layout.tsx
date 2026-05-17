'use client'

import { RelayBgmToggle, useRelayBgm } from '@/features/relay-drawing'

// relay-drawing 라우트 그룹 공통 layout — 부스(/relay-drawing)와 룸 페이지
// (/relay-drawing/[roomCode]) 사이를 이동할 때 layout은 unmount되지 않으므로,
// BGM 인스턴스를 layout에 mount하면 라우트 전환 중에도 음원이 끊기지 않는다.
//
// useRelayBgm: Audio 인스턴스 라이프사이클 + mute 동기화
// RelayBgmToggle: 우상단 floating 토글 버튼 (모달/오버레이 위로 뜨도록 fixed)
export default function RelayDrawingRouteLayout({
  children,
}: Readonly<{
  children: React.ReactNode
}>) {
  useRelayBgm()

  return (
    <>
      {children}
      <RelayBgmToggle className="fixed right-4 top-4 z-[var(--z-sticky)]" />
    </>
  )
}
