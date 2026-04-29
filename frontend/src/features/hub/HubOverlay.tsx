'use client'

import { useMemo, type CSSProperties } from 'react'
import {
  HUB_CONTENT_VIEWS,
  type HubContentKey,
  useHubViewStore,
} from '@/shared/stores'
import { cn } from '@/shared/libs'

const HUB_BUTTONS: Array<{ key: HubContentKey; label: string }> = [
  { key: 'community', label: '커뮤니티' },
  { key: 'fortune', label: '운세' },
  { key: 'relay', label: '릴레이' },
  { key: 'infinite', label: '무한' },
  { key: 'flipbook', label: '플립북' },
]

const PLATFORM_BUTTON_STYLES: Record<HubContentKey, CSSProperties> = {
  community: {
    '--hub-chip-bg': 'rgba(183, 235, 163, 0.72)',
    '--hub-chip-border': 'rgba(111, 168, 87, 0.34)',
    '--hub-chip-text': '#4d7a42',
  } as CSSProperties,
  fortune: {
    '--hub-chip-bg': 'rgba(204, 173, 238, 0.72)',
    '--hub-chip-border': 'rgba(128, 99, 178, 0.34)',
    '--hub-chip-text': '#6c5596',
  } as CSSProperties,
  relay: {
    '--hub-chip-bg': 'rgba(255, 157, 168, 0.74)',
    '--hub-chip-border': 'rgba(204, 96, 108, 0.34)',
    '--hub-chip-text': '#98525b',
  } as CSSProperties,
  infinite: {
    '--hub-chip-bg': 'rgba(185, 224, 246, 0.78)',
    '--hub-chip-border': 'rgba(93, 154, 190, 0.34)',
    '--hub-chip-text': '#527c94',
  } as CSSProperties,
  flipbook: {
    '--hub-chip-bg': 'rgba(255, 225, 143, 0.76)',
    '--hub-chip-border': 'rgba(204, 154, 53, 0.34)',
    '--hub-chip-text': '#8a6b31',
  } as CSSProperties,
}

export default function HubOverlay() {
  const selectedContentKey = useHubViewStore((state) => state.selectedContentKey)
  const currentCopy = useHubViewStore((state) => state.currentCopy)
  const selectContent = useHubViewStore((state) => state.selectContent)
  const copyKey = `${currentCopy.eyebrow}-${currentCopy.title}-${currentCopy.description}`

  const renderedButtons = useMemo(
    () =>
      HUB_BUTTONS.map(({ key, label }) => {
        const platform = HUB_CONTENT_VIEWS[key].platform
        const isActive = selectedContentKey === key

        return (
          <button
            key={key}
            type="button"
            aria-pressed={isActive}
            data-platform={platform}
            style={PLATFORM_BUTTON_STYLES[key]}
            onClick={() => selectContent(key)}
            className={cn(
              'relative min-h-[2.78rem] min-w-[4.95rem] cursor-pointer whitespace-nowrap rounded-full border px-[1.02rem] pb-[0.68rem] pt-[0.64rem]',
              'text-[0.9rem] font-medium tracking-normal backdrop-blur-xl transition duration-200 ease-out',
              'border-[var(--hub-chip-border)] text-[var(--hub-chip-text)] shadow-[inset_0_1px_0_rgba(255,255,255,0.58),0_10px_22px_rgba(9,7,30,0.22)]',
              'bg-[linear-gradient(180deg,rgba(255,255,255,0.68),var(--hub-chip-bg))]',
              'hover:-translate-y-0.5 hover:saturate-[1.04] focus-visible:outline focus-visible:outline-3 focus-visible:outline-offset-4 focus-visible:outline-white',
              'max-[800px]:min-h-[2.55rem] max-[800px]:min-w-[4.25rem] max-[800px]:px-[0.74rem] max-[800px]:pb-[0.62rem] max-[800px]:pt-[0.58rem] max-[800px]:text-[0.8rem]',
              isActive &&
                '-translate-y-0.5 scale-[1.025] border-[rgba(83,62,54,0.28)] text-[#fff6ed] saturate-[1.08] shadow-[inset_0_1px_0_rgba(255,255,255,0.76),0_10px_22px_rgba(103,78,68,0.12),0_0_0_4px_rgba(255,255,255,0.56)]',
            )}
          >
            {label}
          </button>
        )
      }),
    [selectContent, selectedContentKey],
  )

  return (
    <>
      <header
        className={cn(
          'pointer-events-none absolute left-1/2 top-[clamp(1.1rem,4vh,2.6rem)] z-30 w-[min(46rem,calc(100%_-_2rem))] -translate-x-1/2 text-center',
          'drop-shadow-[0_14px_24px_rgba(7,6,24,0.3)]',
          'max-[800px]:top-[1.2rem] max-[800px]:w-[min(34rem,calc(100%_-_1.2rem))]',
        )}
      >
        <div
          key={copyKey}
          className="hub-copy-swap grid justify-items-center gap-[0.62rem] max-[800px]:gap-[0.45rem]"
        >
          <p className="m-0 inline-flex min-h-[1.9rem] max-w-full items-center whitespace-nowrap rounded-full border border-[rgba(255,236,218,0.22)] bg-[linear-gradient(180deg,rgba(255,255,255,0.18),rgba(255,245,229,0.1))] px-4 py-[0.38rem] text-[clamp(0.76rem,1.1vw,0.88rem)] font-normal tracking-normal text-[rgba(255,238,221,0.78)] shadow-[inset_0_1px_0_rgba(255,255,255,0.26),0_8px_18px_rgba(9,7,30,0.18)] backdrop-blur-[14px] before:mr-[0.52rem] before:h-px before:w-[0.7rem] before:rounded-full before:bg-[rgba(255,238,221,0.34)] before:content-[''] after:ml-[0.52rem] after:h-px after:w-[0.7rem] after:rounded-full after:bg-[rgba(255,238,221,0.34)] after:content-[''] max-[800px]:min-h-[1.86rem] max-[800px]:px-[0.92rem] max-[800px]:py-[0.36rem] max-[800px]:text-[0.75rem]">
            {currentCopy.eyebrow}
          </p>
          <h1 className="m-0 text-[clamp(3.05rem,6vw,5.9rem)] font-normal leading-[0.96] tracking-normal text-[#fff6ed] [font-family:'Jua',var(--font-pretendard)] [text-shadow:0_2px_0_rgba(90,65,112,0.44),0_14px_28px_rgba(2,2,18,0.34)] max-[800px]:text-[clamp(2.6rem,14vw,4.2rem)]">
            {currentCopy.title}
          </h1>
          <p className="m-0 max-w-[35rem] justify-self-center break-keep text-[clamp(0.98rem,1.28vw,1.08rem)] font-light leading-[1.68] text-[rgba(255,245,230,0.82)] max-[800px]:max-w-[25rem] max-[800px]:text-[0.94rem] max-[800px]:leading-[1.6]">
            {currentCopy.description}
          </p>
        </div>
      </header>

      <footer className="absolute bottom-[clamp(0.5rem,2vh,1rem)] left-1/2 z-30 grid w-[min(54rem,calc(100%_-_2rem))] -translate-x-1/2 max-[800px]:bottom-[0.45rem] max-[800px]:w-[calc(100%_-_1.2rem)]">
        <div className="flex flex-wrap items-center justify-center gap-[clamp(0.52rem,1vw,0.78rem)] max-[800px]:gap-[0.55rem]">
          {renderedButtons}
        </div>
      </footer>
    </>
  )
}
