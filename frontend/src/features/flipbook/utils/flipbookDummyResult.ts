import type { FlipbookResultItemResponse } from '@/shared/types'

interface DummyFlipbookOwner {
  name: string
  accentColor: string
  scenes: string[]
}

const DUMMY_FLIPBOOK_OWNERS: DummyFlipbookOwner[] = [
  {
    name: '망고',
    accentColor: '#f58c97',
    scenes: ['씨앗을 발견', '물뿌리개 등장', '꽃이 활짝'],
  },
  {
    name: '라임',
    accentColor: '#7ec6ad',
    scenes: ['종이배 출발', '파도 통과', '별빛 항해'],
  },
  {
    name: '포도',
    accentColor: '#96a8ee',
    scenes: ['연필 로켓', '구름 돌파', '달에 도착'],
  },
]

const DUMMY_FRAME_WIDTH = 680
const DUMMY_FRAME_HEIGHT = 520

export function createFlipbookDummyResultItems(): FlipbookResultItemResponse[] {
  const createdAt = new Date().toISOString()

  return DUMMY_FLIPBOOK_OWNERS.map((owner, ownerIndex) => {
    const frames = owner.scenes.map((sceneTitle, frameIndex) => ({
      frameIndex,
      imageUrl: createDummyFrameImageUrl({
        accentColor: owner.accentColor,
        frameIndex,
        ownerIndex,
        ownerName: owner.name,
        sceneTitle,
      }),
      drawnByUserUuid: `dummy-user-${(ownerIndex + frameIndex) % DUMMY_FLIPBOOK_OWNERS.length}`,
      drawnByNickname: DUMMY_FLIPBOOK_OWNERS[
        (ownerIndex + frameIndex) % DUMMY_FLIPBOOK_OWNERS.length
      ].name,
    }))

    return {
      flipbookIndex: ownerIndex,
      galleryId: `dummy-gallery-${ownerIndex + 1}`,
      artifactId: `dummy-flipbook-${ownerIndex + 1}`,
      thumbnailUrl: frames[0]?.imageUrl ?? null,
      gifUrl: null,
      firstImageUrl: frames[0]?.imageUrl ?? null,
      createdAt,
      frames,
    }
  })
}

function createDummyFrameImageUrl({
  accentColor,
  frameIndex,
  ownerIndex,
  ownerName,
  sceneTitle,
}: {
  accentColor: string
  frameIndex: number
  ownerIndex: number
  ownerName: string
  sceneTitle: string
}) {
  const characterX = 210 + frameIndex * 96
  const characterY = 268 - frameIndex * 24 + ownerIndex * 8
  const secondaryX = 430 - frameIndex * 42
  const secondaryY = 210 + frameIndex * 26
  const svg = `
<svg xmlns="http://www.w3.org/2000/svg" width="${DUMMY_FRAME_WIDTH}" height="${DUMMY_FRAME_HEIGHT}" viewBox="0 0 ${DUMMY_FRAME_WIDTH} ${DUMMY_FRAME_HEIGHT}">
  <rect width="680" height="520" fill="#fffefa"/>
  <rect x="28" y="28" width="624" height="464" rx="26" fill="#fff8ee" stroke="${accentColor}" stroke-width="12"/>
  <path d="M80 388 C160 330 238 430 330 366 C430 296 508 382 604 318" fill="none" stroke="#b9d8a5" stroke-width="18" stroke-linecap="round"/>
  <circle cx="${secondaryX}" cy="${secondaryY}" r="${34 + frameIndex * 5}" fill="#f3c66f" opacity="0.9"/>
  <circle cx="${characterX}" cy="${characterY}" r="48" fill="${accentColor}"/>
  <circle cx="${characterX - 18}" cy="${characterY - 12}" r="6" fill="#332222"/>
  <circle cx="${characterX + 18}" cy="${characterY - 12}" r="6" fill="#332222"/>
  <path d="M${characterX - 18} ${characterY + 16} Q${characterX} ${characterY + 32} ${characterX + 18} ${characterY + 16}" fill="none" stroke="#332222" stroke-width="5" stroke-linecap="round"/>
  <path d="M${characterX - 54} ${characterY - 36} Q${characterX - 82} ${characterY - 88} ${characterX - 28} ${characterY - 68}" fill="${accentColor}"/>
  <path d="M${characterX + 54} ${characterY - 36} Q${characterX + 82} ${characterY - 88} ${characterX + 28} ${characterY - 68}" fill="${accentColor}"/>
  <text x="70" y="102" fill="#332222" font-family="Arial, sans-serif" font-size="38" font-weight="700">${ownerName}의 플립북</text>
  <text x="70" y="152" fill="#6c7b67" font-family="Arial, sans-serif" font-size="28" font-weight="700">${frameIndex + 1}. ${sceneTitle}</text>
  <text x="560" y="452" fill="${accentColor}" font-family="Arial, sans-serif" font-size="42" font-weight="700">${frameIndex + 1}</text>
</svg>`

  return `data:image/svg+xml;utf8,${encodeURIComponent(svg)}`
}
