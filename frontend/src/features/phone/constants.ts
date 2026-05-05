import type { StaticImageData } from 'next/image'
import {
  phoneAppDrawing,
  phoneAppGallery,
  phoneAppSettings,
  phoneAppShop,
} from '@/shared/assets'
import type {
  PhoneGalleryFilterKey,
  PhoneGalleryItem,
  PhoneGalleryItemKind,
} from './types'

export const PHONE_PROFILE = {
  nickname: '동그란고구마',
}

export const PHONE_OFFICIAL_STORE_URL = 'https://kr.nemonic.me/'

export const PHONE_COLORS = {
  black: '#11151d',
  cameraLens: '#05070c',
  drawingAccent: '#ffb52e',
  drawingAccentMuted: '#fff2d6',
  drawingBackground: '#f7f7f7',
  drawingPanel: '#f6f6f6',
  homeHeader: '#55adf0',
  launcherBadge: '#ff6f7b',
  launcherScreen: 'linear-gradient(135deg,#dff4ff 0%,#ffe6f2 100%)',
  saveButton: '#ffaad8',
  printButton: '#ffcc66',
  screenHandle: 'rgba(70,63,78,0.72)',
}

export const PHONE_DRAWING_STAGE_SIZE = {
  width: 327,
  height: 327,
}

export const PHONE_DRAWING_PAPER_COLOR = '#fefefe'

export const PHONE_DRAWING_COLORS = [
  '#1f1f1f',
  '#ef4444',
  '#f97316',
  '#facc15',
  '#22c55e',
  '#3b82f6',
  '#6366f1',
  '#a855f7',
  '#ec4899',
  '#92400e',
  '#ffffff',
  '#9ca3af',
]

export const PHONE_BRUSH_SIZES = [3, 6, 10]

export const PHONE_MIN_BRUSH_SIZE = PHONE_BRUSH_SIZES[0]

export const PHONE_MAX_BRUSH_SIZE =
  PHONE_BRUSH_SIZES[PHONE_BRUSH_SIZES.length - 1]

export const PHONE_APP_SHORTCUTS: Array<{
  key: 'drawing' | 'gallery' | 'shop' | 'settings'
  label: string
  asset: StaticImageData
  isEnabled: boolean
  externalUrl?: string
}> = [
  {
    key: 'drawing',
    label: '네모닉 그림판',
    asset: phoneAppDrawing,
    isEnabled: true,
  },
  {
    key: 'gallery',
    label: '갤러리',
    asset: phoneAppGallery,
    isEnabled: true,
  },
  {
    key: 'shop',
    label: '공식몰 바로가기',
    asset: phoneAppShop,
    externalUrl: PHONE_OFFICIAL_STORE_URL,
    isEnabled: true,
  },
  {
    key: 'settings',
    label: '설정',
    asset: phoneAppSettings,
    isEnabled: false,
  },
]

export const PHONE_GALLERY_FILTERS: Array<{
  key: PhoneGalleryFilterKey
  label: string
}> = [
  { key: 'all', label: '전체' },
  { key: 'phone', label: '내 그림' },
  { key: 'fortune', label: '운세' },
  { key: 'flipbook', label: '플립북' },
  { key: 'relay', label: '릴레이' },
  { key: 'infinite', label: '캔버스' },
]

export const PHONE_GALLERY_ITEM_STYLES: Record<
  PhoneGalleryItemKind,
  { label: string; color: string; background: string }
> = {
  phone: { label: '내 그림', color: '#5b7cff', background: '#eef3ff' },
  fortune: { label: '운세', color: '#9b6df2', background: '#f1eaff' },
  flipbook: { label: '플립북', color: '#d69a00', background: '#fff4c7' },
  relay: { label: '릴레이', color: '#ff8a44', background: '#ffe7d6' },
  infinite: { label: '캔버스', color: '#42a8e8', background: '#e6f6ff' },
}

export const PHONE_INITIAL_GALLERY_ITEMS: PhoneGalleryItem[] = [
  {
    id: 'sample-phone-1',
    kind: 'phone',
    title: '내가 그린 메모',
    createdAtLabel: '방금 전',
    badgeLabel: 'NEW',
    isNew: true,
  },
  {
    id: 'sample-fortune-1',
    kind: 'fortune',
    title: '오늘의 운세',
    createdAtLabel: '오늘',
  },
  {
    id: 'sample-flipbook-1',
    kind: 'flipbook',
    title: '댄싱 펭귄',
    createdAtLabel: '어제',
    badgeLabel: '8 컷',
    contributorLabel: '친구 4명과 함께',
  },
  {
    id: 'sample-relay-1',
    kind: 'relay',
    title: '우당탕 캐릭터',
    createdAtLabel: '3일 전',
  },
  {
    id: 'sample-infinite-1',
    kind: 'infinite',
    title: '친구들 캔버스',
    createdAtLabel: '1주 전',
  },
  {
    id: 'sample-phone-2',
    kind: 'phone',
    title: '낙서',
    createdAtLabel: '1주 전',
  },
]
