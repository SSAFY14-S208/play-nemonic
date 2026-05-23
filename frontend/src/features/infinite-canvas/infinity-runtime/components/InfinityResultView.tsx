'use client'

import Image from 'next/image'
import { useRouter } from 'next/navigation'

import { Button } from '@/shared/components'

import { useInfinityCanvasStore } from '..'

function formatDuration(seconds: number): string {
  const minutes = Math.floor(seconds / 60)
  const secs = seconds % 60
  if (minutes === 0) return `${secs}초`
  return `${minutes}분 ${secs}초`
}

export function InfinityResultView() {
  const router = useRouter()
  const { myAnimal, myColor, participants, sessionStats, resultImageUrl, resetSession } =
    useInfinityCanvasStore()

  const today = new Date().toLocaleDateString('ko-KR', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
  })

  const allParticipants = [
    { animal: myAnimal, color: myColor },
    ...participants,
  ]

  const handleNewCanvas = () => {
    resetSession()
    router.push('/infinite-canvas')
  }

  return (
    <div className="min-h-screen bg-canvas-background flex items-center justify-center px-8 py-12">
      <div className="grid grid-cols-2 gap-12 w-full max-w-5xl items-center">
        {/* Left — polaroid card */}
        <div className="relative flex flex-col items-center">
          <div className="relative w-full rounded-2xl shadow-lg p-6 flex flex-col gap-4"
            style={{ backgroundColor: '#fef9e7' }}>
            {/* Header */}
            <div className="flex flex-col gap-1">
              <p className="caption-m text-canvas-muted">{today}</p>
              <p className="body-r text-canvas-ink">
                {allParticipants.map((p) => p.animal.emoji).join(' ')}{' '}
                {allParticipants.length}명이 함께
              </p>
            </div>

            {/* Canvas image */}
            <div
              className="w-full rounded-xl overflow-hidden border border-canvas-border bg-white"
              style={{ aspectRatio: '4/3' }}
            >
              {resultImageUrl ? (
                <Image
                  src={resultImageUrl}
                  alt="함께 그린 캔버스"
                  width={600}
                  height={450}
                  className="w-full h-full object-contain"
                />
              ) : (
                <div className="w-full h-full flex items-center justify-center text-canvas-muted caption-m">
                  캔버스 이미지 없음
                </div>
              )}
            </div>

            {/* Caption */}
            <p className="h3-b text-canvas-ink text-center">함께라 더 즐거워!</p>

            {/* Floating badges */}
            <span className="absolute -top-3 -right-3 text-2xl">⭐</span>
            <span className="absolute -bottom-3 -left-3 text-2xl">💗</span>
          </div>
        </div>

        {/* Right — info panel */}
        <div className="flex flex-col gap-8">
          {/* Teammates */}
          <div className="rounded-2xl bg-canvas-panel border border-canvas-border p-6">
            <p className="caption-b text-canvas-muted mb-4 tracking-widest">
              TEAMMATES
            </p>
            <ul className="flex flex-col gap-3">
              {allParticipants.map((participant, index) => (
                <li key={index} className="flex items-center gap-3">
                  <span className="text-2xl">{participant.animal.emoji}</span>
                  <span className="body-b text-canvas-ink">
                    {participant.animal.name}
                  </span>
                  <span
                    className="ml-auto w-4 h-4 rounded-full flex-shrink-0"
                    style={{ backgroundColor: participant.color }}
                  />
                </li>
              ))}
            </ul>
          </div>

          {/* Session stats */}
          <div className="rounded-2xl bg-canvas-panel border border-canvas-border p-6">
            <p className="caption-b text-canvas-muted mb-4 tracking-widest">
              SESSION
            </p>
            <div className="grid grid-cols-3 gap-4 text-center">
              <div className="flex flex-col gap-1">
                <p className="h2-b text-canvas-accent">
                  {formatDuration(sessionStats.durationSec)}
                </p>
                <p className="caption-m text-canvas-muted">그린 시간</p>
              </div>
              <div className="flex flex-col gap-1">
                <p className="h2-b text-canvas-accent">
                  {sessionStats.strokeCount}
                </p>
                <p className="caption-m text-canvas-muted">총 획수</p>
              </div>
              <div className="flex flex-col gap-1">
                <p className="h2-b text-canvas-accent">
                  {sessionStats.colorCount || allParticipants.length}
                </p>
                <p className="caption-m text-canvas-muted">사용 색상</p>
              </div>
            </div>
          </div>

          {/* Action buttons */}
          <div className="flex flex-col gap-3">
            <Button size="lg" color="neutral" variant="outlined" className="w-full">
              보관함에 담기
            </Button>
            <Button size="lg" color="blue" className="w-full">
              광장에 전시하기
            </Button>
          </div>

          {/* New canvas link */}
          <button
            onClick={handleNewCanvas}
            className="body-r text-canvas-muted hover:text-canvas-accent transition-colors text-center"
          >
            🎨 새 캔버스 시작하기
          </button>
        </div>
      </div>
    </div>
  )
}
