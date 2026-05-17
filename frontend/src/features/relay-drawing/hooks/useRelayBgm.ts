'use client'

import { useEffect, useRef } from 'react'

import { useRelayBgmStore } from '../stores'

const BGM_SRC = '/sounds/relay-drawing/lobby-bgm.mp3'
const BGM_VOLUME = 0.45

// 릴레이 드로잉 BGM 컨트롤러. relay-drawing 레이아웃에 1회 마운트되어 부스 →
// 로비 → 게임 → 결과 라우트 전환을 모두 관통한다.
//
// 동작:
// - mount 시 단일 HTMLAudioElement 생성, loop=true.
// - useRelayBgmStore.isMuted 변화에 따라 play()/pause() 전환.
// - autoplay 차단 환경(브라우저가 사용자 제스처 없이 재생 거부)을 대비해
//   play() 거부를 silently catch한다. 사용자가 BGM 토글을 누르면 그 클릭이
//   user gesture가 되어 다시 play()가 성공한다.
// - unmount 시 pause + src 정리해 라우트 이탈 시 음원 누수 방지.
export function useRelayBgm() {
  const isMuted = useRelayBgmStore((state) => state.isMuted)
  const audioRef = useRef<HTMLAudioElement | null>(null)

  // Audio 인스턴스 라이프사이클 — 마운트 시 1회 생성, 언마운트 시 정리.
  useEffect(() => {
    if (typeof window === 'undefined') return

    const audio = new Audio(BGM_SRC)
    audio.loop = true
    audio.volume = BGM_VOLUME
    audio.preload = 'auto'
    audioRef.current = audio

    return () => {
      audio.pause()
      audio.src = ''
      audioRef.current = null
    }
  }, [])

  // mute 상태 동기화. play()는 autoplay 정책에 따라 reject될 수 있으므로 catch.
  // 차단되면 다음 사용자 클릭/키 입력(전역 1회 listener)에서 자동 재시도해
  // 사용자가 BGM 토글을 직접 누르지 않아도 자연스럽게 재생이 시작된다.
  useEffect(() => {
    const audio = audioRef.current
    if (!audio) return
    if (isMuted) {
      audio.pause()
      return
    }

    let isCancelled = false

    const tryPlay = () => {
      if (isCancelled) return
      void audio.play().catch(() => undefined)
    }

    tryPlay()

    const retryOnUserGesture = () => {
      tryPlay()
    }
    window.addEventListener('pointerdown', retryOnUserGesture, { once: true })
    window.addEventListener('keydown', retryOnUserGesture, { once: true })

    return () => {
      isCancelled = true
      window.removeEventListener('pointerdown', retryOnUserGesture)
      window.removeEventListener('keydown', retryOnUserGesture)
    }
  }, [isMuted])
}
