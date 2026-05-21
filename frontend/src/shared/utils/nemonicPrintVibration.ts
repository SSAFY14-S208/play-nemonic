// 네모닉 인쇄 출력 애니메이션에 동기화되는 진동(haptic) 컨트롤러.
// 플립북 결과 화면, 허브 3D 프린터, 커뮤니티 메모 인쇄 reveal, 운세 인쇄 등
// 여러 인쇄 애니메이션이 공유 사용한다.
//
// 구현 메모:
// - navigator.vibrate에는 충분히 긴 단일 패턴을 한 번에 전달한다(짧은 패턴을
//   setInterval로 반복 호출하면 Chrome Android에서 timer drift / 호출 간
//   cancellation으로 진동이 끊기거나 묵살되는 사례가 있어 권장되지 않음).
// - 인쇄 애니메이션이 단일 패턴 길이보다 더 길어질 가능성에 대비해, 패턴이
//   끝나기 직전에 한 번 더 vibrate을 호출하도록 setInterval로 re-trigger.
// - 디바이스 진동기는 단일 자원이라 모듈 전역 싱글턴 상태로 동시 호출 안전 처리.

// 랜딩 씬 useNemonicPrinterInteraction(HAPTIC.PRINT_START)에서 검증된 80/30 ms
// 강도를 사용. 40ms는 일부 디바이스에서 너무 약해 느끼기 어려운 보고가 있어
// 명확한 떨림이 전달되도록 80ms로 맞춤.
const VIBRATE_SEGMENT_MS = 80
const PAUSE_SEGMENT_MS = 30
const SEGMENT_PAIR_MS = VIBRATE_SEGMENT_MS + PAUSE_SEGMENT_MS // 110ms
// 45개의 (진동+휴식) 쌍 = 45 × 110ms = 4950ms 단일 패턴.
const SEGMENT_PAIR_COUNT = 45
const PRINT_VIBRATION_PATTERN: number[] = (() => {
  const segments: number[] = []
  for (let pairIndex = 0; pairIndex < SEGMENT_PAIR_COUNT; pairIndex++) {
    segments.push(VIBRATE_SEGMENT_MS, PAUSE_SEGMENT_MS)
  }
  return segments
})()
const PATTERN_TOTAL_MS = SEGMENT_PAIR_COUNT * SEGMENT_PAIR_MS // 4800ms
// 패턴이 끝나기 200ms 전에 re-trigger해서 끊김 없이 이어 붙임.
const RE_TRIGGER_INTERVAL_MS = PATTERN_TOTAL_MS - 200

let activeIntervalId: number | null = null

function isVibrationSupported(): boolean {
  return (
    typeof navigator !== 'undefined' && typeof navigator.vibrate === 'function'
  )
}

// 연속 진동 시작. 이미 활성 진동이 있으면 그대로 두고 새로 시작하지 않는다(멱등).
// Chrome Android는 vibrate 호출마다 "transient user activation"을 요구하므로,
// 이 함수는 반드시 사용자 클릭 핸들러의 동기 구간에서 한 번 호출되어야 한다.
// useEffect 등 async 컨텍스트에서 (사용자 제스처 task가 지난 뒤) 다시 호출되면
// 그 vibrate은 묵살되므로, 이미 활성된 진동을 중단하지 않고 그대로 둔다.
//
// 반환되는 함수를 호출해 직접 정지하거나, stopNemonicPrintVibration()을 사용.
export function startNemonicPrintVibration(): () => void {
  // 이미 진행 중이면 그대로 둠 — 재호출로 인한 vibrate(0) → vibrate(pattern) 시퀀스가
  // 두 번째 호출에서 user activation 부재로 묵살되어 진동이 영구 정지되는 것을 방지.
  if (activeIntervalId !== null) {
    return stopNemonicPrintVibration
  }
  if (!isVibrationSupported()) return stopNemonicPrintVibration

  navigator.vibrate(PRINT_VIBRATION_PATTERN)
  activeIntervalId = window.setInterval(() => {
    navigator.vibrate(PRINT_VIBRATION_PATTERN)
  }, RE_TRIGGER_INTERVAL_MS)

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
