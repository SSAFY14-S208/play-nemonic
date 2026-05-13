import { create } from 'zustand'
import { createJSONStorage, persist } from 'zustand/middleware'

const IDLE_TIMEOUT_MS = 30 * 60 * 1000 // 30분

interface LogSessionState {
  sessionId: string | null
  lastActivityAt: number

  getOrCreateSessionId: () => string
  touchActivity: () => void
  rotateIfIdle: () => boolean // idle이면 회전 후 true 반환
}

function generateSessionId(): string {
  return crypto.randomUUID()
}

export const useLogSessionStore = create<LogSessionState>()(
  persist(
    (set, get) => ({
      sessionId: null,
      lastActivityAt: Date.now(),

      getOrCreateSessionId: () => {
        const state = get()
        if (state.sessionId) return state.sessionId
        const newId = generateSessionId()
        set({ sessionId: newId, lastActivityAt: Date.now() })
        return newId
      },

      touchActivity: () => {
        set({ lastActivityAt: Date.now() })
      },

      rotateIfIdle: () => {
        const state = get()
        const elapsed = Date.now() - state.lastActivityAt
        if (elapsed < IDLE_TIMEOUT_MS) return false
        const newId = generateSessionId()
        set({ sessionId: newId, lastActivityAt: Date.now() })
        return true
      },
    }),
    {
      name: 'nemonic-log-session',
      storage: createJSONStorage(() => sessionStorage),
      partialize: (state) => ({
        sessionId: state.sessionId,
        lastActivityAt: state.lastActivityAt,
      }),
    },
  ),
)
