'use client'

import { useRef } from 'react'
import type Konva from 'konva'

const ZOOM_FACTOR = 1.1
const MIN_SCALE = 0.1
const MAX_SCALE = 5

function clamp(value: number, min: number, max: number): number {
  return Math.max(min, Math.min(max, value))
}

export function useInfinityViewport(
  stageRef: React.RefObject<Konva.Stage | null>,
) {
  const scaleRef = useRef<number>(1)
  const stagePosRef = useRef<{ x: number; y: number }>({ x: 0, y: 0 })
  const isSpacePanningRef = useRef<boolean>(false)
  const isToolPanningRef = useRef<boolean>(false)
  const isPointerPanningRef = useRef<boolean>(false)
  const isSpaceDownRef = useRef<boolean>(false)
  const isInitialViewportCenteredRef = useRef<boolean>(false)

  // RAF throttle 게이트 — wheel이 frame 안에 N번 와도 batchDraw는 1번만 호출.
  // 트랙패드/매직마우스의 wheel 폭증(초당 100~200회)으로 메인 스레드가 막히는 걸 방지.
  const rafScheduledRef = useRef<boolean>(false)

  // Konva Stage에 현재 ref 값을 일괄 적용 + 모든 Layer redraw.
  // batchDraw가 배경 Layer의 sceneFunc(도트 그리드)도 viewport에 맞게 재실행시킨다.
  const applyViewport = () => {
    const stage = stageRef.current
    if (!stage) return
    stage.scale({ x: scaleRef.current, y: scaleRef.current })
    stage.position(stagePosRef.current)
    stage.batchDraw()
  }

  const scheduleApplyViewport = () => {
    if (rafScheduledRef.current) return
    rafScheduledRef.current = true
    requestAnimationFrame(() => {
      rafScheduledRef.current = false
      applyViewport()
    })
  }

  const centerInitialViewport = (width: number, height: number) => {
    if (isInitialViewportCenteredRef.current) return
    if (width <= 0 || height <= 0) return
    isInitialViewportCenteredRef.current = true
    stagePosRef.current = {
      x: width / 2,
      y: height / 2,
    }
    applyViewport()
  }

  // wheel 핸들러: 계산과 ref 갱신은 매번 즉시(누적 정확도 + 포인터 위치 정확도 보장),
  // 무거운 batchDraw만 RAF로 묶어서 frame당 1번으로 cap.
  const onStageWheel = (konvaEvent: Konva.KonvaEventObject<WheelEvent>) => {
    konvaEvent.evt.preventDefault()
    const stage = stageRef.current
    if (!stage) return
    // 매 wheel 시점의 실제 포인터 위치를 사용 — 트랙패드 사용 중 마우스가 움직여도
    // 그 시점 좌표를 기준으로 줌이 누적되므로 사용자 의도대로 정확히 동작.
    const pointer = stage.getPointerPosition()
    if (!pointer) return

    const oldScale = scaleRef.current
    const zoomDir = konvaEvent.evt.deltaY < 0 ? 1 : -1
    const newScale = clamp(
      oldScale * Math.pow(ZOOM_FACTOR, zoomDir),
      MIN_SCALE,
      MAX_SCALE,
    )

    const mousePointTo = {
      x: (pointer.x - stagePosRef.current.x) / oldScale,
      y: (pointer.y - stagePosRef.current.y) / oldScale,
    }
    scaleRef.current = newScale
    stagePosRef.current = {
      x: pointer.x - mousePointTo.x * newScale,
      y: pointer.y - mousePointTo.y * newScale,
    }

    scheduleApplyViewport()
  }

  const applyPanningState = () => {
    const stage = stageRef.current
    if (!stage) return
    const isPanning =
      isSpacePanningRef.current ||
      isToolPanningRef.current ||
      isPointerPanningRef.current
    stage.draggable(isPanning)
    stage.container().style.cursor = isPanning ? 'grab' : ''
  }

  // Space 키 패닝 토글 — Konva Stage의 draggable + container 커서를 직접 갱신.
  // React state를 거치지 않으므로 키 입력 시 리렌더가 발생하지 않는다.
  const setSpacePanning = (panning: boolean) => {
    isSpacePanningRef.current = panning
    applyPanningState()
  }

  const setToolPanning = (panning: boolean) => {
    isToolPanningRef.current = panning
    applyPanningState()
  }

  const setPointerPanning = (panning: boolean) => {
    isPointerPanningRef.current = panning
    applyPanningState()
  }

  // Stage가 드래그 종료된 결과를 ref에만 동기화 (state setter 호출 없음).
  const onStageDragEnd = () => {
    const stage = stageRef.current
    if (!stage) return
    stagePosRef.current = { x: stage.x(), y: stage.y() }
  }

  return {
    scaleRef,
    stagePosRef,
    isSpaceDownRef,
    isSpacePanningRef,
    onStageWheel,
    setSpacePanning,
    setToolPanning,
    setPointerPanning,
    onStageDragEnd,
    centerInitialViewport,
  } as const
}
