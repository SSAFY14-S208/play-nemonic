'use client'

import { useMemo, useState } from 'react'

import { ApiError, postRelayRoomClose } from '@/shared/apis'
import { useUserStore } from '@/shared/stores'
import type { RelayPart } from '@/shared/types'

import {
  DRAWER_AVATARS,
  RELAY_RESULT_REVEALS,
  RELAY_ROUND_ORDER,
  RELAY_ROUND_RULES,
  SEGMENT_TAG_CLASSNAMES,
  type RelayResultReveal,
  type RelayResultSegment,
  type RelayRoundKey,
} from '../constants'
import { useRelayDrawingStore } from '../stores'
import type { RelayCompositeDrawingPayload, RelayDrawLine } from '../types'

// ── 유틸 ──────────────────────────────────────────────────────────────

function partToRoundKey(part: RelayPart): RelayRoundKey {
  return part.toLowerCase() as RelayRoundKey
}

function moveLineToFinalPosition(line: RelayDrawLine, roundKey: RelayRoundKey): RelayDrawLine {
  const roundRule = RELAY_ROUND_RULES[roundKey]
  // 라인은 캔버스 좌표계 기준이라 drawArea가 y=120에서 시작하면 y=120이 라운드의
  // "내 영역 시작점"이다. 최종 1920 합성에서 그 시작점이 finalOffsetY 위치로 가도록
  // (point.y - drawArea.y) + finalOffsetY 변환을 한다.
  const adjustedOffsetY = roundRule.finalOffsetY - roundRule.drawArea.y
  return {
    ...line,
    id: `${roundKey}-${line.id}`,
    points: line.points.map((point) => ({
      x: point.x,
      y: point.y + adjustedOffsetY,
    })),
  }
}

function formatDateLabel(isoString: string): string {
  // 백엔드가 timezone-aware ISO-8601 문자열을 보내므로 그대로 파싱.
  const date = new Date(isoString)
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}.${month}.${day}`
}

// ── 훅 ────────────────────────────────────────────────────────────────

export function useRelayResult() {
  const roomCode = useRelayDrawingStore((state) => state.roomCode)
  const hostUserUuid = useRelayDrawingStore((state) => state.hostUserUuid)
  const resultRevealStep = useRelayDrawingStore((state) => state.resultRevealStep)
  const resultItems = useRelayDrawingStore((state) => state.resultItems)
  const activeResultIndex = useRelayDrawingStore((state) => state.activeResultIndex)
  const setActiveResultIndex = useRelayDrawingStore((state) => state.setActiveResultIndex)
  const roundLines = useRelayDrawingStore((state) => state.roundLines)
  const completedAt = useRelayDrawingStore((state) => state.completedAt)
  const goToNextResultReveal = useRelayDrawingStore((state) => state.goToNextResultReveal)
  const goToPreviousResultReveal = useRelayDrawingStore(
    (state) => state.goToPreviousResultReveal,
  )
  const participants = useRelayDrawingStore((state) => state.participants)

  const currentUserUuid = useUserStore((state) => state.userUuid)
  const isHost = currentUserUuid !== null && currentUserUuid === hostUserUuid

  // 호스트 전용 방 종료 — 가이드 §21.
  // 성공 시 ROOM_CLOSED WS 이벤트가 도착해 dismissalReason이 세팅되고,
  // RelayDismissalModal이 자동으로 안내한다. 여기서는 store를 직접 건드리지 않는다.
  const [isClosingRoom, setIsClosingRoom] = useState(false)
  const [closeRoomError, setCloseRoomError] = useState<string | null>(null)

  const closeRoom = () => {
    if (!roomCode || !isHost || isClosingRoom) return
    setCloseRoomError(null)
    setIsClosingRoom(true)
    void (async () => {
      try {
        await postRelayRoomClose(roomCode)
      } catch (caughtError) {
        const message =
          caughtError instanceof ApiError
            ? caughtError.message
            : '방 종료에 실패했어요'
        setCloseRoomError(message)
      } finally {
        setIsClosingRoom(false)
      }
    })()
  }

  const activeResultItem = resultItems[activeResultIndex] ?? null
  const hasServerResults = resultItems.length > 0 && activeResultItem !== null

  // 서버 결과 데이터로 동적 reveals/segments 생성. 데이터 없으면 mock fallback.
  const { reveals, segments, resultImageUrl, ownerNickname, ownerAvatar, completedAtLabel } =
    useMemo(() => {
      if (!activeResultItem) {
        return {
          reveals: RELAY_RESULT_REVEALS,
          segments: [] as RelayResultSegment[],
          resultImageUrl: null as string | null,
          ownerNickname: '',
          ownerAvatar: DRAWER_AVATARS[0],
          completedAtLabel: '',
        }
      }

      const parts = activeResultItem.parts
      const faceDrawer = parts.find((partItem) => partItem.part === 'FACE')
      const ownerName = faceDrawer?.drawerNickname ?? '???'

      // reveal 배열: face → body → legs → final
      const dynamicReveals: RelayResultReveal[] = parts.map((partItem, index) => {
        const roundKey = partToRoundKey(partItem.part)
        const roundRule = RELAY_ROUND_RULES[roundKey]
        const avatar = DRAWER_AVATARS[index % DRAWER_AVATARS.length]
        const isMe = partItem.drawerUserUuid === currentUserUuid
        const displayName = isMe
          ? `${partItem.drawerNickname} (나)`
          : partItem.drawerNickname
        const isFirst = index === 0
        const isLast = index === parts.length - 1

        return {
          key: roundKey,
          order: index + 1,
          roleLabel: roundRule.label,
          participantName: partItem.drawerNickname,
          participantDisplayName: displayName,
          avatar,
          titleSuffix: isFirst
            ? '가 시작했어요'
            : isLast
              ? '가 마무리했어요'
              : '가 이어 그렸어요',
          spotlightLabel: '방금 그린 사람',
          nextLabel: isLast ? '결과 보기 ▶' : '다음 ▶',
        }
      })

      // final reveal — 캔버스 소유자(얼굴 담당)를 대표로 쓴다.
      const faceAvatarEmoji = DRAWER_AVATARS[0]
      dynamicReveals.push({
        key: 'final',
        order: parts.length + 1,
        roleLabel: '완성',
        participantName: ownerName,
        participantDisplayName:
          faceDrawer?.drawerUserUuid === currentUserUuid
            ? `${ownerName} (나)`
            : ownerName,
        avatar: faceAvatarEmoji,
        titleSuffix: '님의 캐릭터',
        spotlightLabel: '합쳐진 캐릭터',
        nextLabel: '완성',
      })

      // segments: 얼굴/몸통/다리 카드 메타 (final은 없음)
      const dynamicSegments: RelayResultSegment[] = parts.map((partItem, index) => {
        const roundKey = partToRoundKey(partItem.part)
        const roundRule = RELAY_ROUND_RULES[roundKey]
        // avatar 필드는 데이터 모델 호환을 위해 유지하지만 결과 화면에서 더 이상
        // 시각적으로 노출되지 않는다. 표시 라벨/태그에서 emoji prefix를 제거했다.
        const avatar = DRAWER_AVATARS[index % DRAWER_AVATARS.length]
        const isMe = partItem.drawerUserUuid === currentUserUuid
        const displayName = isMe
          ? `${partItem.drawerNickname} (나)`
          : partItem.drawerNickname

        return {
          key: roundKey,
          avatar,
          participantName: displayName,
          roleLabel: roundRule.label,
          tagLabel: `${partItem.drawerNickname} · ${roundRule.label}`,
          tagClassName: SEGMENT_TAG_CLASSNAMES[roundKey],
        }
      })

      return {
        reveals: dynamicReveals,
        segments: dynamicSegments,
        resultImageUrl: activeResultItem.contentUrl ?? null,
        ownerNickname: ownerName,
        ownerAvatar: faceAvatarEmoji,
        completedAtLabel: formatDateLabel(activeResultItem.createdAt),
      }
    }, [activeResultItem, currentUserUuid])

  // reveal 내비게이션 — 동적 reveals 배열 기준.
  const activeReveal =
    reveals.find((reveal) => reveal.key === resultRevealStep) ?? reveals[0]
  const activeRevealIndex = reveals.findIndex(
    (reveal) => reveal.key === activeReveal.key,
  )
  const canShowPreviousResultReveal = activeRevealIndex > 0
  const canShowNextResultReveal = activeRevealIndex < reveals.length - 1
  const isFinalReveal = activeReveal.key === 'final'

  // 로컬 드로잉 라인 합성 — 서버 이미지가 없을 때의 SVG fallback.
  const compositeDrawingPayload = useMemo<RelayCompositeDrawingPayload>(
    () => ({
      rounds: roundLines,
      mergedLines: RELAY_ROUND_ORDER.flatMap((roundKey) =>
        roundLines[roundKey].map((line) => moveLineToFinalPosition(line, roundKey)),
      ),
      completedAt,
    }),
    [completedAt, roundLines],
  )

  return {
    // Reveal navigation
    resultRevealStep,
    reveals,
    activeReveal,
    activeRevealIndex,
    isFinalReveal,
    canShowPreviousResultReveal,
    canShowNextResultReveal,
    goToNextResultReveal,
    goToPreviousResultReveal,

    // Result data
    hasServerResults,
    resultItems,
    activeResultIndex,
    setActiveResultIndex,
    resultImageUrl,
    segments,
    participantCount: participants.length,
    ownerNickname,
    ownerAvatar,
    completedAtLabel,

    // Host actions
    isHost,
    isClosingRoom,
    closeRoomError,
    closeRoom,

    // Fallback
    compositeDrawingPayload,
    roundLines,
  }
}
