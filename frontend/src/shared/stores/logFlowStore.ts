import { create } from 'zustand'

interface LogFlowState {
  flowId: string | null
  funnelName: string | null

  startFlow: (funnelName: string) => string // 새 flowId 반환
  endFlow: () => void
}

export const useLogFlowStore = create<LogFlowState>()((set) => ({
  flowId: null,
  funnelName: null,

  startFlow: (funnelName) => {
    const flowId = crypto.randomUUID()
    set({ flowId, funnelName })
    return flowId
  },

  endFlow: () => {
    set({ flowId: null, funnelName: null })
  },
}))
