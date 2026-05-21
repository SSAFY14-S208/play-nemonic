'use client'

import { useEffect } from 'react'

import {
  startNemonicPrintVibration,
  stopNemonicPrintVibration,
} from '@/shared/utils'

// 네모닉 인쇄 출력 애니메이션에 동기화되는 진동 React 훅. isActive가 true인
// 동안 디바이스 진동을 연속으로 유지하고, false로 바뀌거나 컴포넌트가 언마운트
// 되면 자동으로 정지한다.
//
// 디바이스 진동기는 단일 자원이라 같은 화면에서 두 번 호출되면 마지막 호출이
// 이긴다(start가 이전 타이머를 정리). 일반적인 사용은 인쇄 애니메이션 컴포넌트
// 당 한 번씩 호출.
export function useNemonicPrintVibration(isActive: boolean): void {
  useEffect(() => {
    if (!isActive) return
    startNemonicPrintVibration()
    return () => {
      stopNemonicPrintVibration()
    }
  }, [isActive])
}
