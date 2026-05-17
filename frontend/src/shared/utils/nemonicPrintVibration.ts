// 네모닉 인쇄 출력 애니메이션에 동기화되는 진동(haptic) 컨트롤러.
// 플립북 결과 화면, 허브 3D 프린터, 커뮤니티 메모 인쇄 reveal 등 여러 인쇄
// 애니메이션이 공유 사용한다. 디바이스 진동기는 단일 자원이므로 모듈 전역
// 싱글턴 타이머로 동시 제어 — 한 곳에서 start하면 이전 타이머는 자동 정리된다.
//
// 패턴 [40, 20, 40, 20, 40]: 40ms 진동 → 20ms 정지 → 반복. 한 사이클 160ms.
// setInterval로 매 사이클마다 vibrate를 재호출해 인쇄 중 연속적인 진동을 유지.

const PRINT_VIBRATION_PATTERN: ReadonlyArray<number> = [40, 20, 40, 20, 40]
const PRINT_VIBRATION_CYCLE_MS = PRINT_VIBRATION_PATTERN.reduce(
  (totalMs, segmentMs) => totalMs + segmentMs,
  0,
)

let activeIntervalId: number | null = null

function isVibrationSupported(): boolean {
  return (
    typeof navigator !== 'undefined' && typeof navigator.vibrate === 'function'
  )
}

// 연속 진동 시작. 이미 활성 진동이 있으면 자동으로 정리 후 새로 시작한다.
// 반환되는 함수를 호출해 직접 정지하거나, stopNemonicPrintVibration()을 사용.
export function startNemonicPrintVibration(): () => void {
  stopNemonicPrintVibration()
  if (!isVibrationSupported()) return stopNemonicPrintVibration

  navigator.vibrate(PRINT_VIBRATION_PATTERN as number[])
  activeIntervalId = window.setInterval(() => {
    navigator.vibrate(PRINT_VIBRATION_PATTERN as number[])
  }, PRINT_VIBRATION_CYCLE_MS)

  return stopNemonicPrintVibration
}

// 연속 진동 정지. start가 안 된 상태에서 호출해도 안전(no-op).
export function stopNemonicPrintVibration(): void {
  if (activeIntervalId !== null) {
    window.clearInterval(activeIntervalId)
    activeIntervalId = null
  }
  if (isVibrationSupported()) {
    navigator.vibrate(0)
  }
}
