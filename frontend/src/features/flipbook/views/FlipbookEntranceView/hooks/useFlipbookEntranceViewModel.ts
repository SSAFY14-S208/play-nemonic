'use client'

import { useEffect, useRef, useState } from 'react'

import { FLIPBOOK_SOUND_PATHS } from '@/features/flipbook/constants'
import { useFlipbookBgm } from '@/features/flipbook/hooks'
import {
  FLIPBOOK_ENTRANCE_FRAME_SOURCES,
  FLIPBOOK_ENTRANCE_FRAMES,
  type FlipbookEntranceActionKey,
} from '../constants'
import { useFlipbookEntranceIntro } from './useFlipbookEntranceIntro'
import { useFlipbookEntrancePreload } from './useFlipbookEntrancePreload'
import { useFlipbookEntranceWheelFrames } from './useFlipbookEntranceWheelFrames'

interface UseFlipbookEntranceViewModelParams {
  isBusy: boolean
  isRoomCodeErrorVisible: boolean
  onCreateRoom: () => void
  onEnterRoom: () => void
}

export function useFlipbookEntranceViewModel({
  isBusy,
  isRoomCodeErrorVisible,
  onCreateRoom,
  onEnterRoom,
}: UseFlipbookEntranceViewModelParams) {
  const roomCodeInputRef = useRef<HTMLInputElement>(null)
  const scrollZoneRef = useRef<HTMLDivElement>(null)
  const [isEntranceMounted, setIsEntranceMounted] = useState(false)
  const [isRoomCodeModalOpen, setIsRoomCodeModalOpen] = useState(false)
  const [isHowToPlayModalOpen, setIsHowToPlayModalOpen] = useState(false)
  const { isActionVisible, isIntroComplete, wasIntroSkipped } = useFlipbookEntranceIntro()
  const { audioRef, isBgmMuted, toggleFlipbookBgmMuted } = useFlipbookBgm({
    shouldStart: isEntranceMounted && isIntroComplete,
  })
  const shouldInstantCompleteIntro = isIntroComplete && wasIntroSkipped
  const { activeFrameIndex, handleScroll, scrollSpacerHeight } = useFlipbookEntranceWheelFrames(
    FLIPBOOK_ENTRANCE_FRAMES.length,
    isIntroComplete,
  )
  useFlipbookEntrancePreload(FLIPBOOK_ENTRANCE_FRAME_SOURCES)

  const actionHandlers: Record<FlipbookEntranceActionKey, () => void> = {
    'create-room': onCreateRoom,
    'enter-room': () => setIsRoomCodeModalOpen(true),
  }

  const closeRoomCodeModal = () => {
    if (!isBusy) {
      setIsRoomCodeModalOpen(false)
    }
  }

  const openHowToPlayModal = () => setIsHowToPlayModalOpen(true)
  const openRoomCodeModal = () => setIsRoomCodeModalOpen(true)
  const submitRoomCode = () => {
    onEnterRoom()
  }

  useEffect(() => {
    let cancelled = false

    ;(async () => {
      await Promise.resolve()

      if (!cancelled) {
        setIsEntranceMounted(true)
      }
    })()

    return () => {
      cancelled = true
    }
  }, [])

  useEffect(() => {
    if (!isRoomCodeModalOpen) return

    const focusInputFrame = window.requestAnimationFrame(() => {
      roomCodeInputRef.current?.focus()
    })

    return () => {
      window.cancelAnimationFrame(focusInputFrame)
    }
  }, [isRoomCodeModalOpen])

  return {
    activeFrameIndex,
    actionHandlers,
    audioRef,
    closeRoomCodeModal,
    entranceBgmSource: FLIPBOOK_SOUND_PATHS.entranceBgm,
    handleScroll,
    isActionVisible,
    isBgmMuted,
    isEntranceMounted,
    isHowToPlayModalOpen,
    isIntroComplete,
    isRoomCodeModalOpen,
    openHowToPlayModal,
    openRoomCodeModal,
    roomCodeInputRef,
    scrollSpacerHeight,
    scrollZoneRef,
    setIsHowToPlayModalOpen,
    shouldInstantCompleteIntro,
    shouldShowEntranceError: isRoomCodeErrorVisible && !isRoomCodeModalOpen,
    submitRoomCode,
    toggleFlipbookBgmMuted,
  }
}

export type FlipbookEntranceViewModel = ReturnType<typeof useFlipbookEntranceViewModel>
