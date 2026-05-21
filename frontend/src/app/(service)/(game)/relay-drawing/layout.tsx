'use client'

import { RelayHowToPlayModalHost, useRelayBgm } from '@/features/relay-drawing'

// relay-drawing 라우트 그룹 공통 layout — 부스(/relay-drawing)와 룸 페이지
// (/relay-drawing/[roomCode]) 사이를 이동할 때 layout은 unmount되지 않으므로,
// BGM 인스턴스를 layout에 mount하면 라우트 전환 중에도 음원이 끊기지 않는다.
//
// 우상단 컨트롤 버튼(설명/음소거)은 화면별 레이아웃 요구가 달라 각 뷰에서 직접
// 렌더한다. 로비는 헤더의 headerRightSlot에, 그 외 뷰(부스/드로잉/결과/대기)는
// floating 오버레이로 배치한다.
//
// 게임 설명 모달은 버튼이 여러 인스턴스로 마운트돼도 단일 모달만 떠야 하므로
// 호스트를 layout에 한 번만 마운트하고, 버튼은 store를 통해 open만 호출한다.
export default function RelayDrawingRouteLayout({
  children,
}: Readonly<{
  children: React.ReactNode
}>) {
  useRelayBgm()

  return (
    <>
      {children}
      <RelayHowToPlayModalHost />
    </>
  )
}
