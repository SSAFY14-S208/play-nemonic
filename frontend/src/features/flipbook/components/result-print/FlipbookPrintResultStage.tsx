'use client'

import { useEffect, useMemo, useRef, useState } from 'react'
import Image from 'next/image'

import { useNemonicPrintVibration } from '@/shared/hooks'
import { cn } from '@/shared/libs'

import {
  PARTICIPANT_PANEL_IMAGE_SRC,
  PRINT_AFTER_RISE_PAUSE_RATIO,
  resultPrintStageStyles as styles,
} from './constants'
import { useFlipbookPrintReveal } from './hooks'
import { ParticipantListPanel } from './ParticipantListPanel'
import { FrameArtistBadge, SkipPlaybackButton } from './PlaybackControls'
import {
  AttachedPrintedPaper,
  DirectPlaybackPaper,
  FixedAttachedPaperShadow,
  ShadowedPrintedPaper,
  SlotPrintedPaper,
} from './PrintedPaper'
import {
  BoardLayer,
  FurnitureLayers,
  NemonicDeviceImage,
  StageBackground,
} from './StageLayers'
import type {
  FlipbookPrintParticipant,
  RenderFlipbookPrintPaper,
} from './types'
import './FlipbookPrintResultStage.css'

interface FlipbookPrintResultStageProps {
  participants: FlipbookPrintParticipant[]
  activeParticipantIndex?: number
  className?: string
  printDurationMs?: number
  holdDurationMs?: number
  renderPaper?: RenderFlipbookPrintPaper
  onSelectParticipant?: (participantIndex: number) => void
  // 참여자의 출력(print) 시퀀스가 끝나고 사용자가 결과(보통 GIF)를 보고 있는
  // 시점에 참여자당 1회 발사. 부모에서 자동 전환 타이머의 시작점으로 사용한다.
  // - gif-playback 프레임이 있는 경우: active frame이 gif-playback이 되는 즉시
  // - 없는 경우: 모든 print 프레임 출력이 완전히 끝난 시점
  onParticipantRevealComplete?: (participantIndex: number) => void
}

export default function FlipbookPrintResultStage({
  participants,
  activeParticipantIndex = 0,
  className,
  printDurationMs = 1700,
  holdDurationMs = 850,
  onParticipantRevealComplete,
  renderPaper,
  onSelectParticipant,
}: FlipbookPrintResultStageProps) {
  const [selectedParticipantIndex, setSelectedParticipantIndex] = useState(activeParticipantIndex)
  const normalizedSelectedParticipantIndex = Math.min(
    Math.max(0, selectedParticipantIndex),
    Math.max(0, participants.length - 1),
  )
  const selectedParticipant = participants[normalizedSelectedParticipantIndex] ?? null
  const printFrames = useMemo(
    () => selectedParticipant?.frames ?? [],
    [selectedParticipant],
  )
  const {
    activeFrameIndex,
    isPlaying,
    isComplete,
    printCycleKey,
    replay,
    showFrame,
  } = useFlipbookPrintReveal({
    frameCount: printFrames.length,
    printDurationMs,
    holdDurationMs,
  })
  const [pendingGifParticipantIndex, setPendingGifParticipantIndex] = useState<number | null>(null)
  const [printPhaseState, setPrintPhaseState] = useState<{
    activePrintKey: string | null
    phase: 'slot' | 'attach'
  }>({
    activePrintKey: null,
    phase: 'slot',
  })
  const expandTimerRef = useRef<number | null>(null)
  const activeFrame = printFrames[activeFrameIndex] ?? null
  const previousFrame = activeFrameIndex > 0 ? printFrames[activeFrameIndex - 1] : null
  const shouldPrintActiveFrame = activeFrame?.outputMode !== 'gif-playback'
  const activePrintKey = activeFrame && selectedParticipant
    ? `${selectedParticipant.id}-${activeFrame.id}-${printCycleKey}`
    : null
  const effectivePrintPhase =
    printPhaseState.activePrintKey === activePrintKey ? printPhaseState.phase : 'slot'
  const attachedBoardFrame =
    activeFrame && !shouldPrintActiveFrame
      ? activeFrame
      : activeFrame && effectivePrintPhase === 'attach'
        ? activeFrame
        : previousFrame
  // 인쇄 애니메이션 진행 중에만 디바이스 진동을 활성. NemonicDeviceImage의
  // isPrinting과 동일 조건을 유지.
  useNemonicPrintVibration(isPlaying && !isComplete && shouldPrintActiveFrame)
  const selectedParticipantHasGifPlayback = selectedParticipant?.frames.some(
    (frame) => frame.outputMode === 'gif-playback',
  ) ?? false

  useEffect(() => {
    let cancelled = false

    void (async () => {
      if (!cancelled) {
        setSelectedParticipantIndex(activeParticipantIndex)
      }
    })()

    return () => {
      cancelled = true
    }
  }, [activeParticipantIndex])

  // 참여자별 reveal 완료 콜백 — 자동 전환을 위해 부모(FlipbookPage)에 신호.
  // 같은 참여자에 대해 중복 발사되지 않도록 lastFiredRevealIndexRef로 가드한다.
  // 참여자 인덱스가 바뀌면 ref를 null로 리셋해 새 참여자에 대해 다시 발사 가능.
  const lastFiredRevealIndexRef = useRef<number | null>(null)
  useEffect(() => {
    lastFiredRevealIndexRef.current = null
  }, [normalizedSelectedParticipantIndex])

  useEffect(() => {
    if (!onParticipantRevealComplete) return
    // 두 가지 reveal 완료 조건 중 빠른 것을 사용:
    // 1) 활성 프레임이 gif-playback (출력 시퀀스가 GIF로 전환된 순간)
    // 2) gif-playback 프레임이 없는 결과의 경우 print 시퀀스 자체가 모두 끝난
    //    시점 (isComplete + !isPlaying)
    const isShowingGifPlaybackFrame =
      activeFrame !== null && activeFrame.outputMode === 'gif-playback'
    const isPrintSequenceFullyDone = isComplete && !isPlaying
    if (!isShowingGifPlaybackFrame && !isPrintSequenceFullyDone) return
    if (lastFiredRevealIndexRef.current === normalizedSelectedParticipantIndex) return
    lastFiredRevealIndexRef.current = normalizedSelectedParticipantIndex

    let cancelled = false
    void (async () => {
      if (!cancelled) onParticipantRevealComplete(normalizedSelectedParticipantIndex)
    })()
    return () => {
      cancelled = true
    }
  }, [
    activeFrame,
    isComplete,
    isPlaying,
    normalizedSelectedParticipantIndex,
    onParticipantRevealComplete,
  ])

  const selectParticipant = (participantIndex: number) => {
    setPendingGifParticipantIndex(null)
    setSelectedParticipantIndex(participantIndex)
    onSelectParticipant?.(participantIndex)
    replay()
  }

  const selectParticipantGif = (participantIndex: number) => {
    const targetParticipant = participants[participantIndex]
    const hasGifPlayback = targetParticipant?.frames.some(
      (frame) => frame.outputMode === 'gif-playback',
    )
    if (!hasGifPlayback) return

    setSelectedParticipantIndex(participantIndex)
    onSelectParticipant?.(participantIndex)
    setPendingGifParticipantIndex(participantIndex)
  }

  useEffect(() => {
    let cancelled = false

    void (async () => {
      if (pendingGifParticipantIndex !== normalizedSelectedParticipantIndex) return

      const gifFrameIndex = printFrames.findIndex(
        (frame) => frame.outputMode === 'gif-playback',
      )
      if (gifFrameIndex < 0) {
        if (!cancelled) {
          setPendingGifParticipantIndex(null)
        }
        return
      }

      if (!cancelled) {
        showFrame(gifFrameIndex)
        setPendingGifParticipantIndex(null)
      }
    })()

    return () => {
      cancelled = true
    }
  }, [normalizedSelectedParticipantIndex, pendingGifParticipantIndex, printFrames, showFrame])

  useEffect(() => {
    let cancelled = false

    void (async () => {
      if (expandTimerRef.current !== null) {
        window.clearTimeout(expandTimerRef.current)
        expandTimerRef.current = null
      }

      if (!cancelled) {
        setPrintPhaseState({
          activePrintKey,
          phase: 'slot',
        })
      }
    })()

    return () => {
      cancelled = true
    }
  }, [activePrintKey])

  useEffect(() => {
    return () => {
      if (expandTimerRef.current !== null) {
        window.clearTimeout(expandTimerRef.current)
      }
    }
  }, [])

  const scheduleAttachAfterPrint = () => {
    if (expandTimerRef.current !== null) {
      window.clearTimeout(expandTimerRef.current)
    }

    expandTimerRef.current = window.setTimeout(() => {
      setPrintPhaseState({
        activePrintKey,
        phase: 'attach',
      })
    }, printDurationMs * PRINT_AFTER_RISE_PAUSE_RATIO)
  }

  return (
    <section
      className={cn(
        styles.stage,
        className,
      )}
    >
      <div className={styles.scene}>
        <StageBackground />
        <FurnitureLayers />

        <BoardLayer>
          {activeFrameIndex > 0 && <FixedAttachedPaperShadow />}

          {previousFrame && selectedParticipant && (
            <ShadowedPrintedPaper
              frame={previousFrame}
              frameIndex={activeFrameIndex - 1}
              participant={selectedParticipant}
              renderPaper={renderPaper}
            />
          )}

          {activeFrame && selectedParticipant && shouldPrintActiveFrame && effectivePrintPhase === 'attach' && (
            <AttachedPrintedPaper
              key={`${activePrintKey}-attach`}
              frame={activeFrame}
              frameIndex={activeFrameIndex}
              participant={selectedParticipant}
              renderPaper={renderPaper}
            />
          )}

          {activeFrame && selectedParticipant && !shouldPrintActiveFrame && (
            <DirectPlaybackPaper
              key={`${selectedParticipant.id}-${activeFrame.id}-${printCycleKey}`}
              frame={activeFrame}
              frameIndex={activeFrameIndex}
              participant={selectedParticipant}
              renderPaper={renderPaper}
            />
          )}

          <SkipPlaybackButton
            disabled={!selectedParticipantHasGifPlayback}
            onClick={() => selectParticipantGif(normalizedSelectedParticipantIndex)}
          />

          {attachedBoardFrame && selectedParticipant && (
            <FrameArtistBadge
              frame={attachedBoardFrame}
              participant={selectedParticipant}
            />
          )}
        </BoardLayer>

        <NemonicDeviceImage isPrinting={isPlaying && !isComplete && shouldPrintActiveFrame}>
          {activeFrame && selectedParticipant && shouldPrintActiveFrame && effectivePrintPhase === 'slot' && (
            <SlotPrintedPaper
              key={`${activePrintKey}-slot`}
              frame={activeFrame}
              frameIndex={activeFrameIndex}
              participant={selectedParticipant}
              printDurationMs={printDurationMs}
              renderPaper={renderPaper}
              onPrintRiseComplete={scheduleAttachAfterPrint}
            />
          )}
        </NemonicDeviceImage>

        <aside className={styles.participantPanel}>
          <Image
            src={PARTICIPANT_PANEL_IMAGE_SRC}
            alt=""
            fill
            sizes="20vw"
            priority
            unoptimized
            className={cn(
              styles.participantPanelImage,
              'pointer-events-none absolute inset-0 z-0 size-full object-fill',
            )}
          />

          <ParticipantListPanel
            participants={participants}
            selectedParticipantIndex={normalizedSelectedParticipantIndex}
            onSelectParticipant={selectParticipant}
          />
        </aside>
      </div>
    </section>
  )
}
