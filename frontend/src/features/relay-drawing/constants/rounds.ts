// 라운드(파트) 기하 정보 — 캔버스 좌표/힌트 영역/최종 합성 오프셋.
// 가이드 §18의 512×512 PNG 규격 자체와는 별개로, 이 프로젝트는 클라 캔버스를
// 848×720으로 그려 후처리 시 백엔드 규격에 맞춘다는 정책으로 둔다.

import type { RelayPart } from '@/shared/types'

export type RelayRoundKey = 'face' | 'body' | 'legs'

// 서버 RelayPart → 클라이언트 RelayRoundKey 매핑.
// 자동 제출 게이트(라운드 시그니처 검사)와 setAssignment에서 공통으로 사용한다.
export const PART_TO_ROUND_KEY: Record<RelayPart, RelayRoundKey> = {
  FACE: 'face',
  BODY: 'body',
  LEGS: 'legs',
}

export interface RelayRound {
  key: RelayRoundKey
  label: string
  status: 'done' | 'active' | 'pending'
}

export interface RelayRoundArea {
  y: number
  height: number
}

export interface RelayRoundRule {
  label: string
  helperText: string
  // Konva 캔버스 전체 높이. body/legs는 상단에 incoming hint zone 120px가 추가되어
  // 사용자의 drawing area(720)와 분리된다 → 720+120=840. face는 incoming hint가
  // 없어 720 그대로. 폭은 항상 RELAY_STAGE_SIZE.width.
  canvasHeight: number
  // 사용자가 실제로 그릴 수 있는 영역. 항상 720 높이 (제출 blob도 이 영역만).
  drawArea: RelayRoundArea
  // 결과 합성에서 이 라운드 라인을 클립할 영역. drawArea와 동일하게 둔다.
  exportArea: RelayRoundArea
  // 최종 1920 합성 캔버스에서 이 라운드의 drawArea가 시작하는 y좌표.
  // moveLineToFinalPosition은 (point.y - drawArea.y) + finalOffsetY 로 변환한다.
  finalOffsetY: number
  incomingHintSourceRoundKey?: RelayRoundKey
  incomingHintSourceArea?: RelayRoundArea
  incomingHintTargetArea?: RelayRoundArea
  outgoingHintArea?: RelayRoundArea
}

export const RELAY_ROUNDS: RelayRound[] = [
  { key: 'face', label: '얼굴', status: 'active' },
  { key: 'body', label: '몸통', status: 'pending' },
  { key: 'legs', label: '다리', status: 'pending' },
]

export const RELAY_ROUND_ORDER: RelayRoundKey[] = ['face', 'body', 'legs']

export const RELAY_STAGE_SIZE = {
  width: 848,
  height: 720,
}

export const RELAY_FINAL_STAGE_SIZE = {
  width: 848,
  height: 1920,
}

export const RELAY_ROUND_RULES: Record<RelayRoundKey, RelayRoundRule> = {
  face: {
    label: '얼굴',
    helperText: '얼굴을 그리고, 아래 점선 구간만 다음 사람에게 힌트로 넘겨요',
    canvasHeight: 720,
    drawArea: { y: 0, height: 720 },
    exportArea: { y: 0, height: 720 },
    finalOffsetY: 0,
    outgoingHintArea: { y: 600, height: 120 },
  },
  body: {
    label: '몸통',
    helperText: '위쪽 힌트 선을 보고 몸통을 이어 그리고, 아래 구간을 다음 힌트로 남겨요',
    // 상단 120 = incoming hint zone(이전 사람의 결과 페이드), 그 아래 720이 drawing area.
    canvasHeight: 840,
    drawArea: { y: 120, height: 720 },
    exportArea: { y: 120, height: 720 },
    finalOffsetY: 600,
    incomingHintSourceRoundKey: 'face',
    incomingHintSourceArea: { y: 600, height: 120 },
    incomingHintTargetArea: { y: 0, height: 120 },
    // outgoingHint는 drawing area의 하단 120 — canvas y=720..840.
    outgoingHintArea: { y: 720, height: 120 },
  },
  legs: {
    label: '다리',
    helperText: '위쪽 힌트 선을 보고 다리를 이어 그려 캐릭터를 완성해요',
    canvasHeight: 840,
    drawArea: { y: 120, height: 720 },
    exportArea: { y: 120, height: 720 },
    finalOffsetY: 1200,
    incomingHintSourceRoundKey: 'body',
    incomingHintSourceArea: { y: 600, height: 120 },
    incomingHintTargetArea: { y: 0, height: 120 },
  },
}

export const RELAY_ROUND_SEGMENTS = Object.fromEntries(
  Object.entries(RELAY_ROUND_RULES).map(([roundKey, roundRule]) => [
    roundKey,
    {
      label: roundRule.label,
      helperText: roundRule.helperText,
      y: roundRule.drawArea.y,
      height: roundRule.drawArea.height,
    },
  ]),
) as Record<
  RelayRoundKey,
  {
    label: string
    helperText: string
    y: number
    height: number
  }
>

export const RELAY_PREVIEW_LINES = {
  faceCenterX: RELAY_STAGE_SIZE.width / 2,
  bodyTopY: 210,
}
