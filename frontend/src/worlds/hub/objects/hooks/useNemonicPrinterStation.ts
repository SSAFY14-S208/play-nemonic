import { useEffect, useRef, type MutableRefObject } from 'react'
import { useFrame, useThree } from '@react-three/fiber'
import * as THREE from 'three'
import { useNemonicPrintVibration } from '@/shared/hooks'
import { useHubPrintStore } from '@/shared/stores'
import { trackHubInvalidate } from '@/shared/utils'

const FALLBACK_PRINT_ANIMATION_NAMES = [
  'print_head_up',
  'print_button_click',
  'label_up',
] as const
const PRINT_DURATION_MS = 2920

function playPrintSound() {
  if (typeof window === 'undefined') return

  void new Audio('/sounds/print_label.mp3').play().catch(() => undefined)
}

function playFallbackPrintAnimation(
  actions: Record<string, THREE.AnimationAction | null>,
) {
  FALLBACK_PRINT_ANIMATION_NAMES.forEach((animationName) => {
    const action = actions[animationName]
    if (!action) return

    action.setLoop(THREE.LoopOnce, 1)
    action.clampWhenFinished = true
    action.timeScale = 1
    action.reset().play()
  })
}

export function useNemonicPrinterStation(
  actionsRef: MutableRefObject<Record<string, THREE.AnimationAction | null>>,
) {
  const activeRequestIdRef = useRef<string | null>(null)
  const printStartedAtRef = useRef<number | null>(null)
  const currentRequest = useHubPrintStore((state) => state.currentRequest)
  const printStatus = useHubPrintStore((state) => state.printStatus)
  const startPrinting = useHubPrintStore((state) => state.startPrinting)
  const invalidate = useThree((state) => state.invalidate)

  // 3D 프린터 인쇄 애니메이션 진행 중에만 디바이스 진동. printStatus가 'printing'
  // 일 때 활성, 'complete'/'idle'로 바뀌면 자동 정지.
  useNemonicPrintVibration(printStatus === 'printing')

  useEffect(() => {
    if (!currentRequest || printStatus !== 'requested') return

    activeRequestIdRef.current = currentRequest.id
    printStartedAtRef.current = performance.now()
    playPrintSound()
    playFallbackPrintAnimation(actionsRef.current)
    startPrinting(currentRequest.id)
    trackHubInvalidate('printer.printStart')
    invalidate()
  }, [actionsRef, currentRequest, invalidate, printStatus, startPrinting])

  useFrame(() => {
    const activeRequestId = activeRequestIdRef.current
    const printStartedAt = printStartedAtRef.current

    if (!activeRequestId || printStartedAt === null) return

    const printProgress = Math.min(
      (performance.now() - printStartedAt) / PRINT_DURATION_MS,
      1,
    )

    if (printProgress < 1) {
      trackHubInvalidate('printer.printFrame')
      invalidate()
      return
    }

    useHubPrintStore.getState().completePrint(activeRequestId)
    activeRequestIdRef.current = null
    printStartedAtRef.current = null
    trackHubInvalidate('printer.printComplete')
    invalidate()
  })
}
