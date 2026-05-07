'use client'

import { useRouter } from 'next/navigation'

import { RELAY_RESULT_ACTIONS } from '../constants'
import { useRelayResult } from '../hooks'
import { useRelayDrawingStore } from '../stores'
import { cn } from '@/shared/libs'

import {
  ResultAlbumsPanel,
  ResultCanvas,
  ResultCreditsPanel,
  ResultProgressStrip,
  ResultStageHeader,
  ResultStepNav,
} from './result-view'

// roomStatus === 'FINISHED' 일 때 RelayRoomPage가 렌더한다.
// 결과 단계는 useRelayResult 훅이 들고 있는 reveal 인덱스로 분기:
//   face → body → legs → final 순서로 단계별 작성자/캔버스를 보여준다.
//
// sub-components는 ./result-view/ 폴더에 분리. 이 파일은 데이터 플로우와
// 레이아웃 조립만 담당한다.
export default function RelayResultView() {
  const router = useRouter()
  const clearRoom = useRelayDrawingStore((state) => state.clearRoom)
  const {
    // Reveal navigation
    reveals,
    activeReveal,
    activeRevealIndex,
    isFinalReveal,
    canShowPreviousResultReveal,
    canShowNextResultReveal,
    goToNextResultReveal,
    goToPreviousResultReveal,

    // Result data
    resultItems,
    activeResultIndex,
    setActiveResultIndex,
    resultImageUrl,
    segments,
    participantCount,
    ownerNickname,
    ownerAvatar,
    completedAtLabel,

    // Fallback
    roundLines,
  } = useRelayResult()

  // "새 릴레이 만들기" — 현재 룸 정리 후 부스로 이동.
  // 호스트면 백엔드 close까지 하는 게 맞지만 wiring 단계에서 분기 처리.
  const handleStartNew = () => {
    clearRoom()
    router.push('/relay-drawing')
  }

  return (
    <section className="min-h-screen bg-relay-background px-6 py-10 text-relay-ink lg:px-12 lg:py-14">
      <div className="mx-auto w-full max-w-[1312px]">
        <ResultProgressStrip
          activeReveal={activeReveal}
          activeRevealIndex={activeRevealIndex}
          reveals={reveals}
          completedAtLabel={completedAtLabel}
          ownerNickname={ownerNickname}
          ownerAvatar={ownerAvatar}
        />

        <div className="mt-5 grid gap-8 lg:grid-cols-[minmax(0,880px)_400px] lg:items-start">
          <section
            className={cn(
              'overflow-hidden rounded-[18px] bg-relay-paper px-5 py-6 shadow-[0_14px_28px_rgba(212,156,31,0.12)] md:px-7',
              isFinalReveal ? 'min-h-[687px]' : 'min-h-[716px]',
            )}
          >
            {!isFinalReveal && <ResultStageHeader activeReveal={activeReveal} />}

            <ResultCanvas
              activeReveal={activeReveal}
              roundLines={roundLines}
              resultImageUrl={resultImageUrl}
              segments={segments}
            />

            {!isFinalReveal && (
              <ResultStepNav
                activeReveal={activeReveal}
                activeRevealIndex={activeRevealIndex}
                revealCount={reveals.length}
                canShowPreviousResultReveal={canShowPreviousResultReveal}
                canShowNextResultReveal={canShowNextResultReveal}
                onShowPreviousResultReveal={goToPreviousResultReveal}
                onShowNextResultReveal={goToNextResultReveal}
              />
            )}
          </section>

          <aside className="flex min-h-[716px] flex-col gap-4">
            <ResultCreditsPanel
              activeReveal={activeReveal}
              activeRevealIndex={activeRevealIndex}
              isFinalReveal={isFinalReveal}
              segments={segments}
              participantCount={participantCount}
              ownerNickname={ownerNickname}
              ownerAvatar={ownerAvatar}
            />
            <ResultAlbumsPanel
              resultItems={resultItems}
              activeResultIndex={activeResultIndex}
              onSelectResult={setActiveResultIndex}
            />
            <div className="hidden flex-1 lg:block" />

            {isFinalReveal && (
              <>
                <div className="grid min-h-[60px] gap-3 sm:grid-cols-2">
                  {RELAY_RESULT_ACTIONS.map(({ label, Icon }, index) => (
                    <button
                      key={label}
                      type="button"
                      className={cn(
                        'body-b inline-flex items-center justify-center gap-2 rounded-[14px] border-[1.5px] px-5',
                        index === 0 &&
                          'border-relay-line bg-relay-paper text-relay-ink',
                        index !== 0 &&
                          'border-relay-accent bg-relay-accent text-relay-ink shadow-[0_4px_10px_rgba(212,156,31,0.18)]',
                      )}
                    >
                      <Icon className="size-4" aria-hidden />
                      {label}
                    </button>
                  ))}
                </div>

                <button
                  type="button"
                  onClick={handleStartNew}
                  className="caption-b self-center text-relay-muted"
                >
                  새 릴레이 만들기
                </button>
              </>
            )}
          </aside>
        </div>
      </div>
    </section>
  )
}
