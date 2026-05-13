// 라운드(파트) 기하 정보 — 캔버스 좌표/힌트 영역/최종 합성 오프셋.
// 모든 파트는 848×720 캔버스를 사용한다. 이전 파트의 힌트 이미지는 캔버스
// drawArea 안의 상단 OVERLAP_HEIGHT 영역에 반투명 오버레이로 표시되며,
// 사용자는 해당 영역 위에도 자유롭게 그릴 수 있다.
// 백엔드가 최종 합성 시 인접 파트를 OVERLAP_HEIGHT만큼 포개어 쌓는다.

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
  // Konva 캔버스 전체 높이. 모든 파트가 720으로 동일하다.
  // 폭은 항상 RELAY_STAGE_SIZE.width.
  canvasHeight: number
  // 사용자가 실제로 그릴 수 있는 영역. 항상 { y: 0, height: 720 }.
  drawArea: RelayRoundArea
  // 결과 합성에서 이 라운드 라인을 클립할 영역. drawArea와 동일하게 둔다.
  exportArea: RelayRoundArea
  // 최종 1920 합성 캔버스에서 이 라운드의 drawArea가 시작하는 y좌표.
  // 인접 파트는 OVERLAP_HEIGHT만큼 포개어진다.
  finalOffsetY: number
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

// 인접 파트 간 포개어지는 높이 (px).
// FACE/BODY 제출 시 하단 OVERLAP_HEIGHT를 hintImage로 추출하고,
// BODY/LEGS 화면에서 이전 파트의 hintImage를 상단 오버레이로 표시한다.
export const OVERLAP_HEIGHT = 120

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
    helperText: '위쪽 힌트를 참고해 몸통을 이어 그리고, 아래 구간을 다음 힌트로 남겨요',
    canvasHeight: 720,
    drawArea: { y: 0, height: 720 },
    exportArea: { y: 0, height: 720 },
    finalOffsetY: 600,
    outgoingHintArea: { y: 600, height: 120 },
  },
  legs: {
    label: '다리',
    helperText: '위쪽 힌트를 참고해 다리를 이어 그려 캐릭터를 완성해요',
    canvasHeight: 720,
    drawArea: { y: 0, height: 720 },
    exportArea: { y: 0, height: 720 },
    finalOffsetY: 1200,
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
