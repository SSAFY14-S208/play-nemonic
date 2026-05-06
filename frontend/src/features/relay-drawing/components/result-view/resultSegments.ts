// 결과 화면 세그먼트(얼굴/몸통/다리) 카드 + 태그 메타데이터.
// 현재의 participantName/avatar는 mock — 결과 wiring 시 `getRelayRoomResults`
// 응답의 `parts[].drawerNickname`으로 교체된다.

export interface RelayResultSegment {
  key: 'face' | 'body' | 'legs'
  avatar: string
  participantName: string
  roleLabel: string
  tagLabel: string
  tagClassName: string
}

export const RESULT_SEGMENTS: RelayResultSegment[] = [
  {
    key: 'face',
    avatar: '🐱',
    participantName: '고양이',
    roleLabel: '얼굴',
    tagLabel: '🐱 고양이 · 얼굴',
    tagClassName: 'bg-relay-active',
  },
  {
    key: 'body',
    avatar: '🦊',
    participantName: '여우 (나)',
    roleLabel: '몸통',
    tagLabel: '🦊 여우 · 몸통',
    tagClassName: 'bg-relay-segment-body',
  },
  {
    key: 'legs',
    avatar: '🐻',
    participantName: '곰돌이',
    roleLabel: '다리',
    tagLabel: '🐻 곰돌이 · 다리',
    tagClassName: 'bg-relay-segment-legs',
  },
]
