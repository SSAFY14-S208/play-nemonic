'use client'

import { useEffect } from 'react'

import { Button } from '@/shared/components'
import { GameHeroSectionLayout } from '@/shared/layouts'
import { cn } from '@/shared/libs'

import { INFINITY_ANIMALS, INFINITY_COLORS } from '../constants'
import { useInfinityCanvasStore } from '../infinityCanvasStore'

export function InfinityLobbyView() {
  const { myAnimal, myColor, participants, setMyAnimal, setMyColor, setPhase } =
    useInfinityCanvasStore()

  // Randomize animal on first mount
  useEffect(() => {
    ;(async () => {
      const randomAnimal =
        INFINITY_ANIMALS[Math.floor(Math.random() * INFINITY_ANIMALS.length)]
      setMyAnimal(randomAnimal)
    })()
  }, [setMyAnimal])

  const handleReroll = () => {
    const otherAnimals = INFINITY_ANIMALS.filter((a) => a.key !== myAnimal.key)
    const picked =
      otherAnimals[Math.floor(Math.random() * otherAnimals.length)]
    setMyAnimal(picked)
  }

  return (
    <GameHeroSectionLayout
      content={
        <>
          {/* Badge */}
          <span className="inline-flex w-fit items-center gap-1.5 rounded-full bg-canvas-badge px-3 py-1 caption-m text-canvas-muted">
            🎨 무한 캔버스 대기실
          </span>

          {/* Animal avatar card */}
          <div className="flex flex-col items-start gap-4 rounded-2xl bg-canvas-panel border border-canvas-border p-6">
            <p className="body-b text-canvas-ink">내 아바타</p>
            <div className="flex items-center gap-4">
              <span className="text-6xl">{myAnimal.emoji}</span>
              <div className="flex flex-col gap-1">
                <p className="h3-b text-canvas-ink">{myAnimal.name}</p>
                <p className="caption-m text-canvas-muted">
                  닉네임이 자동으로 생성됩니다
                </p>
              </div>
            </div>
            <button
              onClick={handleReroll}
              className="inline-flex items-center gap-1.5 rounded-full bg-canvas-active px-3 py-1.5 caption-b text-canvas-accent hover:bg-canvas-accent/10 transition-colors"
            >
              🔄 다른 동물로 바꾸기
            </button>
          </div>

          {/* Start button */}
          <Button
            size="lg"
            color="blue"
            className="w-full"
            onClick={() => setPhase('stage')}
          >
            {myAnimal.name}로 시작하기 →
          </Button>
        </>
      }
      visual={
        <div className="flex flex-col gap-6 rounded-2xl bg-canvas-panel border border-canvas-border p-6 w-full max-w-sm">
          {/* Color picker */}
          <div>
            <p className="body-b text-canvas-ink mb-3">내 색 고르기</p>
            <div className="grid grid-cols-3 gap-3">
              {INFINITY_COLORS.map((swatch) => (
                <button
                  key={swatch}
                  onClick={() => setMyColor(swatch)}
                  className={cn(
                    'w-14 h-14 rounded-full transition-transform hover:scale-105',
                    myColor === swatch &&
                      'ring-4 ring-offset-2 ring-canvas-accent scale-110',
                  )}
                  style={{ backgroundColor: swatch }}
                />
              ))}
            </div>
          </div>

          <div className="h-px bg-canvas-border" />

          {/* Participants */}
          <div>
            <p className="body-b text-canvas-ink mb-3">
              지금 그리고 있는 친구들 ({participants.length + 1}명)
            </p>
            <ul className="flex flex-col gap-2">
              {/* Me */}
              <li className="flex items-center gap-3 rounded-xl bg-canvas-active px-3 py-2">
                <span className="text-xl">{myAnimal.emoji}</span>
                <span className="body-r text-canvas-ink">{myAnimal.name}</span>
                <span
                  className="ml-auto w-3 h-3 rounded-full flex-shrink-0"
                  style={{ backgroundColor: myColor }}
                />
              </li>
              {/* Others */}
              {participants.map((participant, index) => (
                <li
                  key={index}
                  className="flex items-center gap-3 rounded-xl px-3 py-2"
                >
                  <span className="text-xl">{participant.animal.emoji}</span>
                  <span className="body-r text-canvas-muted">
                    {participant.animal.name}
                  </span>
                  <span
                    className="ml-auto w-3 h-3 rounded-full flex-shrink-0"
                    style={{ backgroundColor: participant.color }}
                  />
                </li>
              ))}
            </ul>
          </div>
        </div>
      }
    />
  )
}
