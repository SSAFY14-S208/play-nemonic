'use client'

import { useCallback, useEffect, useRef } from 'react'
import { HTTPError } from 'ky'
import { getRelayRoomAssignmentMe, postRelayRoomSubmission } from '@/shared/apis'
import { completeFunnelStep } from '@/shared/libs'
import { useUserStore } from '@/shared/stores'

import { PART_TO_ROUND_KEY } from '@/features/relay-drawing/constants'
import { useRelayDrawingStore } from '@/features/relay-drawing/stores'
import { relayToast } from '@/features/relay-drawing/utils'

import { useRelayDrawingCapture } from './useRelayDrawingCapture'

interface UseRelayDrawingGameReturn {
  submitDrawing: () => Promise<void>
  isSubmitting: boolean
  isSubmitted: boolean
  submittedCount: number
  totalCount: number
  hintImageUrl: string | null
}

/**
 * 드로잉 게임 진행 오케스트레이션 훅.
 *
 * 책임:
 *   1. PLAYING 상태 진입 / PART_STARTED 수신 시 getRelayRoomAssignmentMe를 호출해
 *      canvasIndex, part, hint, deadline을 store에 반영한다.
 *   2. 사용자가 "저장하고 다음" 클릭 또는 타이머 만료 시 캔버스를 캡처해
 *      postRelayRoomSubmission으로 제출한다.
 *   3. 제출 후 대기 상태(isSubmitted)를 관리한다 — 다음 PART_STARTED가 오면
 *      setAssignment로 자동 리셋.
 *
 * 책임 아님:
 *   - WS 이벤트 수신 (useRelayRoom이 담당)
 *   - 타이머 카운트다운 (useRelayTimer가 담당)
 *   - 캔버스 드로잉 입력 (useRelayCanvas가 담당)
 */
export function useRelayDrawingGame(): UseRelayDrawingGameReturn {
  const roomCode = useRelayDrawingStore((state) => state.roomCode)
  const roomStatus = useRelayDrawingStore((state) => state.roomStatus)
  const isSubmitting = useRelayDrawingStore((state) => state.isSubmitting)
  const isSubmitted = useRelayDrawingStore((state) => state.isSubmitted)
  const submittedCount = useRelayDrawingStore((state) => state.submittedCount)
  const totalCount = useRelayDrawingStore((state) => state.totalCount)
  const hintImageUrl = useRelayDrawingStore((state) => state.hintImageUrl)
  const partFetchTrigger = useRelayDrawingStore((state) => state.partFetchTrigger)
  const pendingAutoSubmitTrigger = useRelayDrawingStore(
    (state) => state.pendingAutoSubmitTrigger,
  )

  const setAssignment = useRelayDrawingStore((state) => state.setAssignment)

  // 배정 fetch 중복 방지 — PART_STARTED 이벤트가 여러 번 오거나
  // GAME_STARTED + 초기 마운트가 동시에 트리거될 때 한 번만 호출.
  const fetchingPartRef = useRef<string | null>(null)

  // PLAYING 상태 + roomCode가 있으면 배정을 가져온다.
  // partFetchTrigger는 WS 이벤트(GAME_STARTED/PART_STARTED) 또는 REST hydrate 시에만
  // 증가하는 전용 카운터다. partDeadlineAt을 의존성으로 쓰면 setAssignment 내부 set이
  // 재트리거를 유발하므로, 트리거와 데이터 세팅을 분리한다.
  useEffect(() => {
    if (roomStatus !== 'PLAYING' || !roomCode) return

    const fetchKey = `${roomCode}-${partFetchTrigger}`

    // 이미 같은 트리거에 대해 fetch 중이면 건너뛴다.
    if (fetchingPartRef.current === fetchKey) return
    fetchingPartRef.current = fetchKey

    let cancelled = false

    void (async () => {
      // 서버가 GAME_STARTED 이벤트 전송 후 배정 생성을 비동기로 처리할 수 있다.
      // 첫 시도 실패 시 재시도한다. 총 ~11초 윈도우.
      const RETRY_DELAYS = [1000, 2000, 3000, 5000]
      const MAX_ATTEMPTS = 1 + RETRY_DELAYS.length

      for (let attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
        if (cancelled) return

        try {
          const assignment = await getRelayRoomAssignmentMe(roomCode)
          if (cancelled) return
          // 이미 제출한 배정이면(새로고침 후 복귀 등) submitted 상태로 둔다.
          if (assignment.assignmentStatus !== 'PENDING') {
            setAssignment(assignment)
            const roundKey = PART_TO_ROUND_KEY[assignment.part]
            useRelayDrawingStore.getState().markSubmitted(roundKey)
            return
          }
          setAssignment(assignment)
          return
        } catch {
          // 배정 조회 실패 — 서버가 아직 배정을 생성하지 않았을 수 있다.
          if (attempt < RETRY_DELAYS.length) {
            await new Promise((resolve) => setTimeout(resolve, RETRY_DELAYS[attempt]))
          }
        }
      }
    })()

    return () => {
      cancelled = true
      // 컴포넌트 언마운트 또는 의존성 변경 시 ref를 리셋해서,
      // 같은 trigger 값으로 재마운트될 때도 fetch가 실행되도록 한다.
      fetchingPartRef.current = null
    }
  }, [roomStatus, roomCode, partFetchTrigger, setAssignment])
  const { captureCanvasBlob, captureHintBlob } = useRelayDrawingCapture()

  const submitDrawing = useCallback(async () => {
    const store = useRelayDrawingStore.getState()

    if (store.isSubmitting || store.isSubmitted) return
    if (!store.roomCode || store.canvasIndex === null || !store.currentPart) return

    // 제출 시점의 라운드를 캡처 — in-flight 도중 라운드가 전환되어도 올바른 라운드가 마킹된다.
    const submittingRoundKey = store.activeRoundKey

    store.setIsSubmitting(true)

    try {
      const [drawingImage, hintImage] = await Promise.all([
        captureCanvasBlob(),
        captureHintBlob(),
      ])

      if (!drawingImage) {
        store.setIsSubmitting(false)
        relayToast.error('캔버스를 캡처하지 못했어요')
        return
      }

      const response = await postRelayRoomSubmission({
        roomCode: store.roomCode,
        canvasIndex: store.canvasIndex,
        part: store.currentPart,
        drawingImage,
        hintImage: hintImage ?? undefined,
      })

      useRelayDrawingStore.getState().markSubmitted(submittingRoundKey)
      // REST 응답으로 즉시 진행도 반영 — WS PART_SUBMITTED를 기다리지 않고 대기 UI에 카운트 표시.
      useRelayDrawingStore.getState().updateSubmissionProgress(
        response.submittedCount,
        response.totalCount,
      )
      // 본인 UUID도 즉시 제출자 목록에 추가 — 우측 친구 패널의 "완료" 표시가
      // WS round-trip 없이 바로 반영되도록.
      const currentUserUuid = useUserStore.getState().userUuid
      if (currentUserUuid) {
        useRelayDrawingStore.getState().addSubmittedUserUuid(currentUserUuid)
      }
      completeFunnelStep('drawing', 4, {
        content_type: 'relay',
        room_id: store.roomCode,
      })
    } catch (error) {
      // 409 Conflict = 서버가 이미 auto-submit 처리했거나 데드라인 만료.
      // 클라이언트는 "제출 완료"로 간주하고 대기 상태로 전환한다.
      if (error instanceof HTTPError && error.response.status === 409) {
        useRelayDrawingStore.getState().markSubmitted(submittingRoundKey)
        const currentUserUuid = useUserStore.getState().userUuid
        if (currentUserUuid) {
          useRelayDrawingStore.getState().addSubmittedUserUuid(currentUserUuid)
        }
        return
      }

      // 그 외 실패 — submitting 플래그를 내려 재시도 가능하게 한다.
      useRelayDrawingStore.getState().setIsSubmitting(false)
      relayToast.error('제출에 실패했어요. 다시 시도해 주세요.')
    }
  }, [captureCanvasBlob, captureHintBlob])

  // PART_TIME_UP 자동 제출 트리거.
  // useRelayRoom의 PART_TIME_UP 핸들러가 본인이 미제출자 목록에 있으면
  // pendingAutoSubmitTrigger를 increment한다. 이 effect가 그걸 감지해
  // submitDrawing을 호출 — 클라이언트의 deadline 폴링을 대체하는 단일 진입점.
  // submitDrawing 내부에 isSubmitted/isSubmitting/canvasIndex 가드가 이미 있어
  // 여기서 추가 가드 없이 호출만 한다.
  useEffect(() => {
    if (pendingAutoSubmitTrigger === 0) return
    void submitDrawing()
  }, [pendingAutoSubmitTrigger, submitDrawing])

  return {
    submitDrawing,
    isSubmitting,
    isSubmitted,
    submittedCount,
    totalCount,
    hintImageUrl,
  }
}
