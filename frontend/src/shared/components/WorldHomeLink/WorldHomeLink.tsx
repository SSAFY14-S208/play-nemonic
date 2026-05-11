'use client'

import { useCallback, useState } from 'react'
import Image from 'next/image'
import { usePathname, useRouter } from 'next/navigation'
import { motion } from 'motion/react'
import LeaveConfirmModal from './LeaveConfirmModal'

const WORLD_PATH = '/hub'

/** 클릭 시 진행 내용을 잃을 수 있어 떠나기 전 확인 모달을 띄우는 게임 라우트 */
const GAME_ROUTE_PREFIXES = ['/flipbook', '/relay-drawing', '/community-canvas']

/** 월드 홈 링크 자체를 표시하지 않는 라우트 (이동할 곳이 자기 자신이거나 인트로 화면) */
const HIDDEN_ROUTES = new Set([WORLD_PATH, '/'])

export default function WorldHomeLink() {
  const router = useRouter()
  const pathname = usePathname()
  const [confirmOpen, setConfirmOpen] = useState(false)

  const isInGame = GAME_ROUTE_PREFIXES.some((prefix) => pathname.startsWith(prefix))

  const handleClick = useCallback(() => {
    if (isInGame) {
      setConfirmOpen(true)
      return
    }
    router.push(WORLD_PATH)
  }, [isInGame, router])

  const handleConfirm = useCallback(() => {
    setConfirmOpen(false)
    router.push(WORLD_PATH)
  }, [router])

  const handleCancel = useCallback(() => {
    setConfirmOpen(false)
  }, [])

  if (HIDDEN_ROUTES.has(pathname)) {
    return null
  }

  return (
    <>
      <motion.button
        type="button"
        onClick={handleClick}
        aria-label="네모닉 월드로 돌아가기"
        className="fixed left-4 top-4 z-[var(--z-sticky)] flex items-center justify-center p-1 sm:left-6 sm:top-6"
        initial={{ opacity: 0, y: -8 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ type: 'spring', stiffness: 320, damping: 24 }}
        whileHover={{ scale: 1.05, y: -2 }}
        whileTap={{ scale: 0.96 }}
      >
        <Image
          src="/images/nemonic-world-logo.png"
          alt="네모닉 월드"
          width={140}
          height={88}
          priority
          draggable={false}
          className="h-auto w-[7.5rem] drop-shadow-[0_8px_18px_rgba(0,0,0,0.18)] sm:w-[8.75rem]"
        />
      </motion.button>
      <LeaveConfirmModal
        open={confirmOpen}
        onCancel={handleCancel}
        onConfirm={handleConfirm}
      />
    </>
  )
}
