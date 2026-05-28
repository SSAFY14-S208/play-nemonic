'use client'

import type { RelayRoomResultItemResponse } from '@/shared/types'
import { cn } from '@/shared/libs'

import RelayButton from '@/features/relay-drawing/components/RelayButton'
import { RELAY_RESULT_ACTIONS } from '@/features/relay-drawing/constants'

interface ResultRightPanelProps {
  resultItems: RelayRoomResultItemResponse[]
  activeResultIndex: number
  onSelectResult: (index: number) => void
  onReturnToLobby: () => void
  onCommunityPost: () => void
  canPostCommunity: boolean
  onShareExternal: () => void
  isSharingExternal: boolean
  canShareExternal: boolean
  // 현재 활성 작품을 디바이스에 다운로드. RELAY_RESULT_ACTIONS의 첫 번째 항목
  // ("보관함에", Download 아이콘)에 연결된다.
  onDownloadArtifact: () => void
  isDownloading: boolean
  canDownload: boolean
}

// 결과 화면 우측 1컬럼 패널 — 얼굴 작성자(앨범 소유자) 닉네임 버튼 + 액션 버튼.
// 좌측 캔버스 인터렉션은 활성 앨범의 합성 결과를 보여주며, 이 패널의 닉네임 버튼을
// 누르면 부모의 setActiveResultIndex가 호출돼 좌측 시퀀스가 처음부터 다시 재생된다.
export default function ResultRightPanel({
  resultItems,
  activeResultIndex,
  onSelectResult,
  onReturnToLobby,
  onCommunityPost,
  canPostCommunity,
  onShareExternal,
  isSharingExternal,
  canShareExternal,
  onDownloadArtifact,
  isDownloading,
  canDownload,
}: ResultRightPanelProps) {
  return (
    <section className="flex flex-col gap-4">
      <div className="flex flex-col gap-2">
        <p className="caption-b text-relay-accent-strong">캐릭터 둘러보기</p>
        <div className="flex flex-col gap-2">
          {resultItems.map((resultItem, index) => {
            const faceDrawer = resultItem.parts.find(
              (partItem) => partItem.part === 'FACE',
            )
            const displayName =
              faceDrawer?.drawerNickname ?? `캔버스 ${index + 1}`
            const isActive = index === activeResultIndex
            return (
              <button
                key={resultItem.canvasIndex}
                type="button"
                onClick={() => onSelectResult(index)}
                className={cn(
                  'body-b flex min-h-12 cursor-pointer items-center justify-between rounded-[14px] border-[1.5px] border-relay-line bg-relay-paper px-4 py-2 text-left text-relay-ink transition-all hover:-translate-y-0.5 hover:brightness-95',
                  isActive &&
                    'border-relay-accent-strong bg-relay-active shadow-[0_4px_10px_rgba(212,156,31,0.18)]',
                )}
              >
                <span>{displayName}</span>
                <span className="caption-b text-relay-accent-strong">
                  {isActive ? '보는 중' : '보기'}
                </span>
              </button>
            )
          })}
        </div>
      </div>

      <div className="flex flex-col gap-2">
        {RELAY_RESULT_ACTIONS.map(({ id, label, Icon }, index) => {
          const isDownloadAction = id === 'download'
          const isCommunityPostAction = id === 'community-post'
          const isExternalShareAction = id === 'external-share'
          const handleClick =
            id === 'download'
              ? onDownloadArtifact
              : id === 'community-post'
                ? onCommunityPost
                : onShareExternal
          const isDisabled =
            (isDownloadAction && (!canDownload || isDownloading)) ||
            (isCommunityPostAction && !canPostCommunity) ||
            (isExternalShareAction && !canShareExternal)
          const buttonLabel =
            isDownloadAction && isDownloading
              ? '저장 중'
              : isExternalShareAction && isSharingExternal
                ? '공유 중'
                : label

          return (
            <RelayButton
              key={id}
              variant={index === 0 ? 'secondary' : 'primary'}
              size="md"
              onClick={handleClick}
              disabled={isDisabled}
              className={cn(
                'w-full gap-2 rounded-[14px] border-[1.5px]',
                index === 0
                  ? 'border-relay-line'
                  : 'border-relay-accent shadow-[0_4px_10px_rgba(212,156,31,0.18)]',
              )}
            >
              <Icon className="size-4" aria-hidden />
              {buttonLabel}
            </RelayButton>
          )
        })}
        <RelayButton
          onClick={onReturnToLobby}
          size="md"
          className="w-full rounded-[14px] border-[1.5px] border-relay-accent shadow-[0_4px_10px_rgba(212,156,31,0.18)]"
        >
          로비로 돌아가기
        </RelayButton>
      </div>
    </section>
  )
}
