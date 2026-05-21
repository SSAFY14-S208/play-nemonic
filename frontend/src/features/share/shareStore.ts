'use client'

import { create } from 'zustand'
import { ApiError, postArtifactShare } from '@/shared/apis'
import type { ShareCreateResponse } from '@/shared/types'

type ShareStatus = 'idle' | 'loading' | 'success' | 'error'

interface ShareStore {
  shareInfo: ShareCreateResponse | null
  shareStatus: ShareStatus
  shareError: string | null

  createShare: (artifactId: string) => Promise<void>
  clearShare: () => void
}

function toErrorMessage(error: unknown, fallback: string): string {
  if (error instanceof ApiError) return error.message || fallback
  if (error instanceof Error) return error.message || fallback
  return fallback
}

export const useShareStore = create<ShareStore>((set) => ({
  shareInfo: null,
  shareStatus: 'idle',
  shareError: null,

  createShare: async (artifactId) => {
    set({ shareStatus: 'loading', shareError: null })
    try {
      const response = await postArtifactShare(artifactId)
      set({ shareInfo: response, shareStatus: 'success' })
    } catch (error) {
      set({
        shareInfo: null,
        shareStatus: 'error',
        shareError: toErrorMessage(error, '공유 정보를 만들 수 없어요.'),
      })
    }
  },

  clearShare: () =>
    set({ shareInfo: null, shareStatus: 'idle', shareError: null }),
}))
