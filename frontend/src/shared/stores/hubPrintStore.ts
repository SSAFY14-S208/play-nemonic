import { create } from 'zustand'
import {
  HUB_NOTE_OUTPUT_POSITION,
  HUB_NOTE_OUTPUT_ROTATION,
} from '@/shared/constants'
import type {
  NoteSurface,
  PrintedNote,
  PrintRequest,
  PrintStatus,
} from '@/shared/types'
import { trackHubStoreUpdate } from '@/shared/utils'

interface HubPrintStore {
  attachNote: (
    noteId: string,
    surface: NoteSurface,
    position: [number, number, number],
    rotation: [number, number, number],
  ) => void
  completePrint: (requestId: string) => void
  currentRequest: PrintRequest | null
  dismissPrintStatus: () => void
  draggingNoteId: string | null
  notes: PrintedNote[]
  printStatus: PrintStatus
  requestPrint: (imageDataUrl: string | null, text?: string) => void
  setDraggingNoteId: (noteId: string | null) => void
  startPrinting: (requestId: string) => void
}

export const useHubPrintStore = create<HubPrintStore>((set, get) => ({
  currentRequest: null,
  draggingNoteId: null,
  notes: [],
  printStatus: 'idle',
  attachNote: (noteId, surface, position, rotation) => {
    trackHubStoreUpdate('hubPrintStore', 'attachNote')
    set((state) => ({
      draggingNoteId:
        state.draggingNoteId === noteId ? null : state.draggingNoteId,
      notes: state.notes.map((note) =>
        note.id === noteId
          ? {
              ...note,
              position,
              rotation,
              surface,
            }
          : note,
      ),
    }))
  },
  completePrint: (requestId) => {
    const request = get().currentRequest

    if (!request || request.id !== requestId) return

    trackHubStoreUpdate('hubPrintStore', 'completePrint')

    const newNote: PrintedNote = {
      createdAt: Date.now(),
      id: `printed-note-${request.id}`,
      imageDataUrl: request.imageDataUrl,
      position: HUB_NOTE_OUTPUT_POSITION,
      rotation: HUB_NOTE_OUTPUT_ROTATION,
      surface: 'floating',
      text: request.text,
    }

    set((state) => ({
      currentRequest: null,
      notes: [newNote, ...state.notes],
      printStatus: 'complete',
    }))
  },
  dismissPrintStatus: () => {
    trackHubStoreUpdate('hubPrintStore', 'dismissPrintStatus')
    set({ printStatus: 'idle' })
  },
  requestPrint: (imageDataUrl, text = 'NEMONIC printed memo') => {
    trackHubStoreUpdate('hubPrintStore', 'requestPrint')
    set({
      currentRequest: {
        id: `${Date.now()}`,
        imageDataUrl,
        requestedAt: Date.now(),
        text,
      },
      printStatus: 'requested',
    })
  },
  setDraggingNoteId: (noteId) => {
    trackHubStoreUpdate('hubPrintStore', 'setDraggingNoteId')
    set({ draggingNoteId: noteId })
  },
  startPrinting: (requestId) => {
    const request = get().currentRequest

    if (!request || request.id !== requestId) return

    trackHubStoreUpdate('hubPrintStore', 'startPrinting')
    set({ printStatus: 'printing' })
  },
}))
