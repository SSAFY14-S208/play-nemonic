'use client'

import { create } from 'zustand'

/**
 * 다른 feature가 PhoneLauncher의 floating 버튼을 숨기거나, phone 열기를
 * 요청할 수 있도록 하는 가교 store.
 *
 * PhoneLauncher(features/phone)는 마운트 시 자신의 openPhone 콜백을
 * 여기에 등록한다. 다른 feature(예: relay-drawing)는 이 store의
 * requestOpen()만 호출하면 되므로 features/phone에 직접 의존하지 않는다.
 */
interface PhoneLauncherStore {
  /** true면 floating 런처 버튼을 숨긴다 (PhoneModal은 유지) */
  isLauncherHidden: boolean
  setLauncherHidden: (hidden: boolean) => void

  /** PhoneLauncher가 마운트 시 등록하는 실제 phone 열기 함수 */
  _openPhoneHandler: (() => void) | null
  registerOpenPhone: (handler: () => void) => void

  /** 외부에서 phone 열기를 요청할 때 호출 */
  requestOpen: () => void
}

export const usePhoneLauncherStore = create<PhoneLauncherStore>((set, get) => ({
  isLauncherHidden: false,
  setLauncherHidden: (hidden) => set({ isLauncherHidden: hidden }),

  _openPhoneHandler: null,
  registerOpenPhone: (handler) => set({ _openPhoneHandler: handler }),

  requestOpen: () => {
    const handler = get()._openPhoneHandler
    handler?.()
  },
}))
