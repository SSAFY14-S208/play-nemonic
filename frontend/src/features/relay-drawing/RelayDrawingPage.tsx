'use client'

import { RelayBoothView } from './components'

// 라우트: /relay-drawing
// 부스(랜딩) 화면만 담당. 방 생성/입장 액션은 RelayBoothView 내부에서 처리하고,
// 성공 시 router.push(`/relay-drawing/${roomCode}`)로 RelayRoomPage로 넘어간다.
export default function RelayDrawingPage() {
  return (
    <div className="min-h-screen bg-relay-background text-relay-ink">
      <RelayBoothView />
    </div>
  )
}
