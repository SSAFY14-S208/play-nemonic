export const FLIPBOOK_ENTRANCE_FRAME_COUNT = 12
export const FLIPBOOK_SCROLL_HINT_FRAME_INDEX = 0
export const FLIPBOOK_SCROLL_HINT_LABEL = '아래로 스크롤 하세요'
export const FLIPBOOK_ENTRANCE_FRAMES = Array.from(
  { length: FLIPBOOK_ENTRANCE_FRAME_COUNT },
  (unusedValue, frameIndex) => {
    const frameNumber = String(frameIndex + 1).padStart(2, '0')

    return {
      src: `/images/flipbook-entrance/${frameNumber}.webp`,
      alt: `플립북 스케치북 재생 ${frameIndex + 1}번째 장면`,
    }
  },
)
export const FLIPBOOK_ENTRANCE_FRAME_SOURCES = FLIPBOOK_ENTRANCE_FRAMES.map(
  (entranceFrame) => entranceFrame.src,
)
export const FLIPBOOK_ENTRANCE_PRELOAD_LINK_SOURCES = FLIPBOOK_ENTRANCE_FRAME_SOURCES.slice(1)
export const FLIPBOOK_SCENE_IMAGES = {
  background: '/images/flipbook-entrance-scene/room-background.png',
  furnitureSprite: '/images/flipbook-entrance-scene/furniture-sprite.png',
  titleLogoSprite: '/images/flipbook-entrance-scene/title-logo-sprite.png',
  actionButtonsSprite: '/images/flipbook-entrance-scene/action-buttons-sprite.png',
  sketchbook: '/images/flipbook-entrance-scene/sketchbook.png',
  howToPlayButton: '/images/flipbook-entrance-scene/how-to-play-button.png',
  soundOnButton: '/images/flipbook-entrance-scene/sound-on-button.png',
  soundMutedButton: '/images/flipbook-entrance-scene/sound-muted-button.png',
}
export const FLIPBOOK_ACTION_BUTTON_IMAGE_QUALITY = 92
export const FLIPBOOK_ACTION_BUTTON_IMAGE_SIZES = '(max-width: 639px) 100vw, 40vw'

export const DROP_SPRING_TRANSITION = {
  type: 'spring',
  stiffness: 96,
  damping: 13,
  mass: 0.82,
} as const

export const DROP_LAYERS = [
  {
    key: 'furniture-top',
    className: 'left-[17.43%] top-[-5.33%] h-[22.04%] w-[57.76%]',
    imageClassName: 'h-[593.26%] w-[191.05%] -left-[36.96%] -top-[33.16%]',
    rotate: 3.2,
    fallDistance: 720,
    delay: 0.12,
  },
  {
    key: 'furniture-left',
    className: 'left-[-5.05%] top-[44.91%] h-[62.50%] w-[47.97%]',
    imageClassName: 'h-[181.75%] w-[199.94%] -left-[0.03%] -top-[81.75%]',
    rotate: 0,
    fallDistance: 860,
    delay: 0.34,
  },
  {
    key: 'furniture-right',
    className: 'left-[63.13%] top-[3.98%] h-[103.06%] w-[39.22%]',
    imageClassName: 'h-[125.96%] w-[279.19%] -left-[165.03%] -top-[25.96%]',
    rotate: 0,
    fallDistance: 940,
    delay: 0.24,
  },
] as const

export const FLIPBOOK_ENTRANCE_ACTIONS = [
  {
    key: 'create-room',
    buttonClassName: 'left-[11.90%] top-[62.69%] aspect-[635/267] w-[15.21%]',
    imageCropClassName: 'h-[383.52%] -left-[17.95%] -top-[150.56%]',
    label: '방 만들기',
  },
  {
    key: 'enter-room',
    buttonClassName: 'left-[28.36%] top-[62.69%] aspect-[635/267] w-[15.21%]',
    imageCropClassName: 'h-[383.52%] -left-[128.98%] -top-[150.56%]',
    label: '입장하기',
  },
] as const

export type FlipbookEntranceActionKey = (typeof FLIPBOOK_ENTRANCE_ACTIONS)[number]['key']
