import Image from 'next/image'

import { cn } from '@/shared/libs'
import type { RelayRoomResultItemResponse } from '@/shared/types'
import { buildMinioUrl } from '@/shared/utils'

interface ResultAlbumsPanelProps {
  resultItems: RelayRoomResultItemResponse[]
  activeResultIndex: number
  onSelectResult: (index: number) => void
}

// 우측 사이드 — 릴레이 결과 앨범 미리보기 그리드.
// 각 resultItem은 canvasIndex 단위 합성 결과이며, 썸네일을 누르면 해당 결과로 전환한다.
export default function ResultAlbumsPanel({
  resultItems,
  activeResultIndex,
  onSelectResult,
}: ResultAlbumsPanelProps) {
  if (resultItems.length === 0) return null

  return (
    <section className="rounded-[18px] border border-relay-line bg-relay-paper p-5">
      <p className="caption-b text-relay-accent-strong">앨범 둘러보기</p>
      <div className="mt-3 grid grid-cols-3 gap-2">
        {resultItems.map((resultItem, index) => {
          const faceDrawer = resultItem.parts.find(
            (partItem) => partItem.part === 'FACE',
          )
          const displayName = faceDrawer?.drawerNickname ?? `캔버스 ${index + 1}`

          return (
            <button
              key={resultItem.canvasIndex}
              type="button"
              onClick={() => onSelectResult(index)}
              className={cn(
                'caption-b grid min-h-22 place-items-center overflow-hidden rounded-xl border-[1.5px] border-relay-line bg-relay-credit-row text-relay-accent-strong',
                index === activeResultIndex &&
                  'border-relay-accent-strong bg-relay-active',
              )}
            >
              {resultItem.thumbnailUrl ? (
                <Image
                  src={buildMinioUrl(resultItem.thumbnailUrl)}
                  alt={`${displayName} 님의 릴레이 결과`}
                  width={120}
                  height={88}
                  className="size-full object-cover"
                />
              ) : (
                <span className="grid justify-items-center gap-1">
                  <span className="text-[22px]">🖼️</span>
                  <span>{displayName}</span>
                </span>
              )}
            </button>
          )
        })}
      </div>
    </section>
  )
}
