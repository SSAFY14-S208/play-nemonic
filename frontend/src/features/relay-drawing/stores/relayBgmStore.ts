'use client'

import { create } from 'zustand'
import { persist } from 'zustand/middleware'

// 릴레이 드로잉 BGM mute 설정. 부스/로비/게임/결과 라우트 전환 후에도, 그리고
// 다음 방문에서도 동일한 설정을 유지하도록 localStorage에 persist한다.
// 기본값은 unmuted — 입장 BGM이 자연스럽게 깔리도록 하고, 원치 않는 사용자는
// 토글로 끄면 상태가 유지된다.
interface RelayBgmState {
  isMuted: boolean
  setMuted: (muted: boolean) => void
  toggleMuted: () => void
}

export const useRelayBgmStore = create<RelayBgmState>()(
  persist(
    (set, get) => ({
      isMuted: false,
      setMuted: (muted) => set({ isMuted: muted }),
      toggleMuted: () => set({ isMuted: !get().isMuted }),
    }),
    { name: 'relay-bgm-pref' },
  ),
)
