import { create } from 'zustand'
import { createJSONStorage, persist } from 'zustand/middleware'

interface UserState {
  userUuid: string | null
  nickname: string | null
  setUser: (userUuid: string, nickname: string | null) => void
  clear: () => void
}

export const useUserStore = create<UserState>()(
  persist(
    (set) => ({
      userUuid: null,
      nickname: null,
      setUser: (userUuid, nickname) => set({ userUuid, nickname }),
      clear: () => set({ userUuid: null, nickname: null }),
    }),
    {
      name: 'nemonic-user',
      storage: createJSONStorage(() => localStorage),
      partialize: (state) => ({
        userUuid: state.userUuid,
        nickname: state.nickname,
      }),
    },
  ),
)
