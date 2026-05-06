// 라운드(파트) 기하 정보 — 캔버스 좌표/힌트 영역/최종 합성 오프셋.
// 가이드 §18의 512×512 PNG 규격 자체와는 별개로, 이 프로젝트는 클라 캔버스를
// 848×720으로 그려 후처리 시 백엔드 규격에 맞춘다는 정책으로 둔다.

export type RelayRoundKey = 'face' | 'body' | 'legs'

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
  drawArea: RelayRoundArea
  exportArea: RelayRoundArea
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
    drawArea: { y: 0, height: 720 },
    exportArea: { y: 0, height: 720 },
    finalOffsetY: 0,
    outgoingHintArea: { y: 600, height: 120 },
  },
  body: {
    label: '몸통',
    helperText: '위쪽 힌트 선을 보고 몸통을 이어 그리고, 아래 구간을 다음 힌트로 남겨요',
    drawArea: { y: 0, height: 720 },
    exportArea: { y: 0, height: 720 },
    finalOffsetY: 600,
    incomingHintSourceRoundKey: 'face',
    incomingHintSourceArea: { y: 600, height: 120 },
    incomingHintTargetArea: { y: 0, height: 120 },
    outgoingHintArea: { y: 600, height: 120 },
  },
  legs: {
    label: '다리',
    helperText: '위쪽 힌트 선을 보고 다리를 이어 그려 캐릭터를 완성해요',
    drawArea: { y: 0, height: 720 },
    exportArea: { y: 0, height: 720 },
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
