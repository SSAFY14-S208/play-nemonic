'use client'

import { create } from 'zustand'

// 게임 설명 모달 open 상태. GameLobbyLayout이 모바일/데스크탑 헤더를 둘 다
// 마운트하기 때문에, 버튼별로 useState를 두면 동일한 모달이 2개 떠버린다.
// 모든 RelayHowToPlayButton 인스턴스와 모달 호스트가 이 스토어를 공유해
// 단일 모달 상태로 모은다.
interface RelayHowToPlayState {
  isOpen: boolean
  open: () => void
  close: () => void
  setOpen: (open: boolean) => void
}

export const useRelayHowToPlayStore = create<RelayHowToPlayState>((set) => ({
  isOpen: false,
  open: () => set({ isOpen: true }),
  close: () => set({ isOpen: false }),
  setOpen: (open) => set({ isOpen: open }),
}))
