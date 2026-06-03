'use client'

import { create } from 'zustand'
import type {
  FlipbookClientMessage,
  FlipbookConnectionStatus,
  FlipbookServerMessage,
  FlipbookSessionSnapshot,
} from '@/shared/types'

interface FlipbookRealtimeState {
  connectionStatus: FlipbookConnectionStatus
  roomId: string | null
  sessionSnapshot: FlipbookSessionSnapshot | null
  outboundMessages: FlipbookClientMessage[]
  lastServerMessage: FlipbookServerMessage | null
  lastErrorMessage: string | null
  setConnectionStatus: (connectionStatus: FlipbookConnectionStatus) => void
  setRoomId: (roomId: string | null) => void
  setSessionSnapshot: (sessionSnapshot: FlipbookSessionSnapshot) => void
  enqueueClientMessage: (message: FlipbookClientMessage) => void
  markClientMessageSent: (requestId: string) => void
  applyServerMessage: (message: FlipbookServerMessage) => void
  clearRealtimeQueue: () => void
}

export const useFlipbookRealtimeStore = create<FlipbookRealtimeState>((set) => ({
  connectionStatus: 'idle',
  roomId: null,
  sessionSnapshot: null,
  outboundMessages: [],
  lastServerMessage: null,
  lastErrorMessage: null,
  setConnectionStatus: (connectionStatus) => set({ connectionStatus }),
  setRoomId: (roomId) => set({ roomId }),
  setSessionSnapshot: (sessionSnapshot) => set({ sessionSnapshot }),
  enqueueClientMessage: (message) =>
    set((state) => ({
      outboundMessages: [...state.outboundMessages, message],
    })),
  markClientMessageSent: (requestId) =>
    set((state) => ({
      outboundMessages: state.outboundMessages.filter((message) => message.requestId !== requestId),
    })),
  applyServerMessage: (message) =>
    set((state) => {
      if (message.type === 'flipbook.snapshot') {
        return {
          lastServerMessage: message,
          sessionSnapshot: message.payload,
          roomId: message.payload.roomId,
          lastErrorMessage: null,
        }
      }

      if (message.type === 'flipbook.error') {
        return {
          lastServerMessage: message,
          lastErrorMessage: message.payload.message,
        }
      }

      if (message.type === 'flipbook.assignment.changed') {
        return {
          ...state,
          lastServerMessage: message,
          lastErrorMessage: null,
          sessionSnapshot: state.sessionSnapshot
            ? {
                ...state.sessionSnapshot,
                activeAssignment: message.payload,
                phase: 'drawing',
                serverSyncedAt: new Date().toISOString(),
              }
            : state.sessionSnapshot,
        }
      }

      if (message.type === 'flipbook.participants.changed') {
        return {
          ...state,
          lastServerMessage: message,
          lastErrorMessage: null,
          sessionSnapshot: state.sessionSnapshot
            ? {
                ...state.sessionSnapshot,
                participants: message.payload,
                serverSyncedAt: new Date().toISOString(),
              }
            : state.sessionSnapshot,
        }
      }

      if (message.type === 'flipbook.result.completed') {
        return {
          ...state,
          lastServerMessage: message,
          lastErrorMessage: null,
          sessionSnapshot: state.sessionSnapshot
            ? {
                ...state.sessionSnapshot,
                phase: 'result',
                result: message.payload,
                completedFrames: message.payload.frames,
                serverSyncedAt: new Date().toISOString(),
              }
            : state.sessionSnapshot,
        }
      }

      return {
        ...state,
        lastServerMessage: message,
        lastErrorMessage: null,
      }
    }),
  clearRealtimeQueue: () => set({ outboundMessages: [] }),
}))
