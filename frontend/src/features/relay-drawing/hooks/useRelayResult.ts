'use client'

import { useMemo } from 'react'

import { useUserStore } from '@/shared/stores'
import type { RelayPart } from '@/shared/types'
import { buildMinioUrl, parseServerInstant } from '@/shared/utils'

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
  return {
    ...line,
    id: `${roundKey}-${line.id}`,
    points: line.points.map((point) => ({
      x: point.x,
      y: point.y + roundRule.finalOffsetY,
    })),
  }
}

function formatDateLabel(isoString: string): string {
  // 서버 LocalDateTime을 UTC로 강제 해석한 뒤, 사용자 로컬 timezone으로 자정
  // 경계 오차 없이 표시한다.
  const date = parseServerInstant(isoString)
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}.${month}.${day}`
}

// ── 훅 ────────────────────────────────────────────────────────────────

export function useRelayResult() {
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
          tagLabel: `${avatar} ${partItem.drawerNickname} · ${roundRule.label}`,
          tagClassName: SEGMENT_TAG_CLASSNAMES[roundKey],
        }
      })

      return {
        reveals: dynamicReveals,
        segments: dynamicSegments,
        resultImageUrl: activeResultItem.contentUrl
          ? buildMinioUrl(activeResultItem.contentUrl)
          : null,
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

    // Fallback
    compositeDrawingPayload,
    roundLines,
  }
}
