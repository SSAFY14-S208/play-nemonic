'use client'

import { DEFAULT_USER_NICKNAME } from '@/shared/constants'
import { useUserStore } from '@/shared/stores'

import NicknameGate from './views/RelayRoomView/sections/NicknameGate'
import { RelayRoomView } from './views'
import './relay-drawing.css'

export default function RelayRoomPage() {
  const nickname = useUserStore((state) => state.nickname)
  const needsNicknameSetup =
    !nickname || nickname.trim() === '' || nickname === DEFAULT_USER_NICKNAME

  if (needsNicknameSetup) return <NicknameGate />

  return <RelayRoomView />
}
