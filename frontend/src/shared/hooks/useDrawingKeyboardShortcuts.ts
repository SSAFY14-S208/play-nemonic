'use client'

import { useEffect } from 'react'

interface UseDrawingKeyboardShortcutsOptions {
  enabled?: boolean
  onUndo: () => void
  onRedo: () => void
}

function isTextEditingTarget(target: EventTarget | null) {
  if (!(target instanceof HTMLElement)) return false

  return (
    target.tagName === 'INPUT' ||
    target.tagName === 'TEXTAREA' ||
    target.isContentEditable
  )
}

export function useDrawingKeyboardShortcuts({
  enabled = true,
  onUndo,
  onRedo,
}: UseDrawingKeyboardShortcutsOptions) {
  useEffect(() => {
    if (!enabled) return

    const handleKeyDown = (event: KeyboardEvent) => {
      if (!(event.ctrlKey || event.metaKey)) return
      if (isTextEditingTarget(event.target)) return

      const pressedKey = event.key.toLowerCase()

      if (pressedKey === 'z' && !event.shiftKey) {
        event.preventDefault()
        onUndo()
        return
      }

      if ((pressedKey === 'z' && event.shiftKey) || pressedKey === 'y') {
        event.preventDefault()
        onRedo()
      }
    }

    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [enabled, onRedo, onUndo])
}
