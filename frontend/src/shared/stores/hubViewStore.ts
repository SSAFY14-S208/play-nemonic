import { create } from 'zustand'

export type HubContentKey = 'community' | 'fortune' | 'relay' | 'infinite' | 'flipbook'

export interface HubViewCopy {
  eyebrow: string
  title: string
  description: string
}

export interface HubContentView extends HubViewCopy {
  platform: 'green' | 'purple' | 'coral' | 'blue' | 'yellow'
  angle: number
  zoom: number
}

export const HUB_MIN_ZOOM = 11.5
export const HUB_MAX_ZOOM = 21
export const HUB_VIEW_TRANSITION_DURATION_MS = 2800

export const HUB_OVERVIEW_VIEW: HubContentView = {
  platform: 'coral',
  angle: 0.51,
  zoom: 13.9,
  eyebrow: '망고슬래브 플레이 입구',
  title: 'Play! Nemonic',
  description: 'Play! Nemonic에 도착했어요. 오늘은 어떤 상상이 출력될까요?',
}

export const HUB_CONTENT_VIEWS: Record<HubContentKey, HubContentView> = {
  community: {
    platform: 'green',
    angle: -2.794,
    zoom: 11.7,
    eyebrow: '초록 티켓 · 모두의 갤러리',
    title: '커뮤니티 캔버스',
    description: '모두의 메모가 모여서 만드는 오늘의 보드.',
  },
  fortune: {
    platform: 'purple',
    angle: 1.679,
    zoom: 11.5,
    eyebrow: '보라 티켓 · 오늘의 운세 부스',
    title: '오늘의 운세',
    description: '네모닉에 마법을 걸어, 오늘의 행운을 한 장 뽑아볼까요?',
  },
  relay: {
    platform: 'yellow',
    angle: -1.645,
    zoom: 11.6,
    eyebrow: '노랑 티켓 · 이어 그리는 놀이',
    title: '우당탕 릴레이 드로잉',
    description: '머리는 몰랐답니다. 이런 몸통과 다리를 만나게 될 줄은.',
  },
  infinite: {
    platform: 'blue',
    angle: -0.904,
    zoom: 11.7,
    eyebrow: '파랑 티켓 · 끝없이 펼쳐지는 판',
    title: '무한 캔버스',
    description: '끝없이 펼쳐지는 캔버스 위에, 지금 떠오른 걸 그리고 뽑아봐요.',
  },
  flipbook: {
    platform: 'coral',
    angle: 0.51,
    zoom: 11.7,
    eyebrow: '코랄 티켓 · 움직임 만드는 책',
    title: '플립북',
    description: '친구들과 메모지를 모아 플립북을 만들어봐요.',
  },
}

interface HubViewStore {
  selectedContentKey: HubContentKey | null
  currentCopy: HubViewCopy
  targetAngle: number
  targetZoom: number
  viewTransitionUntil: number
  lastInteractionAt: number
  isDragging: boolean
  isWitchHovered: boolean
  selectContent: (contentKey: HubContentKey, immediate?: boolean) => void
  selectOverview: (immediate?: boolean) => void
  adjustTargetAngle: (angleDelta: number) => void
  setTargetAngle: (angle: number) => void
  setTargetZoom: (zoom: number) => void
  setDragging: (isDragging: boolean) => void
  setWitchHovered: (isWitchHovered: boolean) => void
  markInteraction: () => void
  cancelViewTransition: () => void
  syncActiveContentByAngle: (currentAngle: number) => void
}

const HUB_ACTIVE_CONTENT_ANGLE_THRESHOLD = 0.6

function clampZoom(zoom: number) {
  return Math.min(Math.max(zoom, HUB_MIN_ZOOM), HUB_MAX_ZOOM)
}

function nowMs() {
  return typeof performance === 'undefined' ? Date.now() : performance.now()
}

function normalizeAngle(angle: number) {
  return Math.atan2(Math.sin(angle), Math.cos(angle))
}

function findNearestContentKey(currentAngle: number): {
  key: HubContentKey | null
  distance: number
} {
  const normalized = normalizeAngle(currentAngle)
  let nearestKey: HubContentKey | null = null
  let nearestDistance = Infinity

  for (const key of Object.keys(HUB_CONTENT_VIEWS) as HubContentKey[]) {
    const candidateAngle = normalizeAngle(HUB_CONTENT_VIEWS[key].angle)
    const delta = Math.abs(normalizeAngle(normalized - candidateAngle))
    if (delta < nearestDistance) {
      nearestDistance = delta
      nearestKey = key
    }
  }
  return { key: nearestKey, distance: nearestDistance }
}

export const useHubViewStore = create<HubViewStore>((set) => ({
  selectedContentKey: null,
  currentCopy: HUB_OVERVIEW_VIEW,
  targetAngle: HUB_OVERVIEW_VIEW.angle,
  targetZoom: HUB_OVERVIEW_VIEW.zoom,
  viewTransitionUntil: 0,
  lastInteractionAt: 0,
  isDragging: false,
  isWitchHovered: false,
  selectContent: (contentKey, immediate = false) => {
    const view = HUB_CONTENT_VIEWS[contentKey]
    set({
      selectedContentKey: contentKey,
      currentCopy: view,
      targetAngle: view.angle,
      targetZoom: clampZoom(view.zoom),
      viewTransitionUntil: immediate ? 0 : nowMs() + HUB_VIEW_TRANSITION_DURATION_MS,
      lastInteractionAt: nowMs(),
    })
  },
  selectOverview: (immediate = false) => {
    set({
      selectedContentKey: null,
      currentCopy: HUB_OVERVIEW_VIEW,
      targetAngle: HUB_OVERVIEW_VIEW.angle,
      targetZoom: clampZoom(HUB_OVERVIEW_VIEW.zoom),
      viewTransitionUntil: immediate ? 0 : nowMs() + HUB_VIEW_TRANSITION_DURATION_MS,
      lastInteractionAt: nowMs(),
    })
  },
  adjustTargetAngle: (angleDelta) => {
    set((state) => ({
      targetAngle: state.targetAngle + angleDelta,
      lastInteractionAt: nowMs(),
      viewTransitionUntil: 0,
    }))
  },
  setTargetAngle: (angle) => {
    set({
      targetAngle: angle,
      lastInteractionAt: nowMs(),
      viewTransitionUntil: 0,
    })
  },
  setTargetZoom: (zoom) => {
    set({
      targetZoom: clampZoom(zoom),
      lastInteractionAt: nowMs(),
      viewTransitionUntil: 0,
    })
  },
  setDragging: (isDragging) => set({ isDragging }),
  setWitchHovered: (isWitchHovered) => set({ isWitchHovered }),
  markInteraction: () => set({ lastInteractionAt: nowMs() }),
  cancelViewTransition: () => set({ viewTransitionUntil: 0 }),
  syncActiveContentByAngle: (currentAngle) => {
    const { key, distance } = findNearestContentKey(currentAngle)
    set((state) => {
      if (key && distance <= HUB_ACTIVE_CONTENT_ANGLE_THRESHOLD) {
        if (state.selectedContentKey === key) return state
        return {
          selectedContentKey: key,
          currentCopy: HUB_CONTENT_VIEWS[key],
        }
      }
      if (state.selectedContentKey === null) return state
      return {
        selectedContentKey: null,
        currentCopy: HUB_OVERVIEW_VIEW,
      }
    })
  },
}))
