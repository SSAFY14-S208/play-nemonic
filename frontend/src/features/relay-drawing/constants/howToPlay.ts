import type { HowToPlayPanel } from '@/shared/components'

// 부트 페이지의 "게임 설명" 모달에서 캐러셀로 보여주는 만화 컷 데이터.
// 실제 만화 컷 일러스트는 추후 추가 예정 — 지금은 컷별 제목/설명만 정의한다.

export const RELAY_HOW_TO_PLAY_PANELS: HowToPlayPanel[] = [
  {
    id: 'gather',
    title: '2~6명이 모여요',
    description: '방 코드를 친구에게 공유해 한 방에 모입니다.',
  },
  {
    id: 'face',
    title: '1라운드 — 얼굴',
    description: '제한 시간 안에 캐릭터의 얼굴을 자유롭게 그려요.',
  },
  {
    id: 'pass',
    title: '캔버스가 다음 사람에게',
    description: '내가 그린 캔버스는 다음 친구에게 넘어가요.',
  },
  {
    id: 'hint',
    title: '하단 힌트만 보고 이어 그리기',
    description:
      '이전 그림의 아랫부분 힌트만 보이고, 그 위에 몸통·다리를 이어 그려요.',
  },
  {
    id: 'reveal',
    title: '우당탕 캐릭터 완성!',
    description: '3라운드가 끝나면 합쳐진 우당탕 캐릭터를 함께 감상해요.',
  },
]
