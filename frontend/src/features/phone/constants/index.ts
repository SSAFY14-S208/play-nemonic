import type { StaticImageData } from 'next/image'
import {
  phoneAppCs,
  phoneAppDrawing,
  phoneAppGallery,
  phoneAppShop,
} from '@/shared/assets'
import type { CsInquiryType } from '@/shared/types'
import { PHONE_COLORS } from './colors'
import type {
  PhoneGalleryFilterKey,
  PhoneGalleryItemKind,
} from '../types'

export { PHONE_COLORS } from './colors'

export const PHONE_OFFICIAL_STORE_URL = 'https://kr.nemonic.me/'

export const PHONE_FRAME_LAYOUT = {
  aspectRatio: '1131.3865966796875 / 2348',
  // 디자인 기준 사이즈로 고정. viewport 적응은 PhoneModal wrapper의 transform: scale이 담당한다.
  deviceMaxWidth: '393px',
  deviceFrame: {
    left: '0%',
    top: '0%',
    width: '100.85%',
    height: '100%',
  },
  screen: {
    left: '1.04%',
    top: '0.51%',
    width: '97.76%',
    height: '99.02%',
  },
  display: {
    left: '3.11%',
    top: '1.51%',
    width: '93.56%',
    height: '96.99%',
    borderRadius: '10.47% / 4.87%',
  },
  speakerCamera: {
    left: '30.62%',
    top: '0.69%',
    width: '38.56%',
    height: '4.4%',
  },
  homeIndicator: {
    left: '33.81%',
    top: '97.01%',
    width: '29.57%',
    height: '0.49%',
  },
} as const

export const PHONE_STATUS_BAR_LAYOUT = {
  time: {
    left: '8.53%',
    top: '2.9%',
  },
  signal: {
    left: '78.95%',
    top: '3.22%',
    width: '3.18%',
    height: '1.28%',
  },
  battery: {
    left: '83.82%',
    top: '3.22%',
    width: '4.43%',
    height: '1.28%',
  },
} as const

export const PHONE_CLOSE_BUTTON_LAYOUT = {
  left: '99.5%',
  top: '5.83%',
  width: '20.6%',
  aspectRatio: '233 / 400',
} as const

export const PHONE_DRAWING_LAYOUT = {
  backgroundTop: {
    left: '-1.52%',
    top: '-3.89%',
    width: '100%',
    height: '17.79%',
    background: PHONE_COLORS.drawingBackground,
    borderBottom: `1px solid ${PHONE_COLORS.drawingDivider}`,
  },
  backgroundMiddle: {
    left: '-1.52%',
    top: '13.9%',
    width: '100%',
    height: '5.48%',
    background: PHONE_COLORS.white,
    borderBottom: `1px solid ${PHONE_COLORS.drawingDivider}`,
  },
  backgroundBottom: {
    left: '-1.52%',
    top: '82.55%',
    width: '100%',
    height: '21.33%',
    background: PHONE_COLORS.drawingBackground,
    borderTop: `1px solid ${PHONE_COLORS.drawingDivider}`,
  },
  backButton: {
    left: '6.69%',
    top: '10.41%',
    width: '4.76%',
    height: '2.06%',
  },
  title: {
    left: '50.12%',
    top: '11.43%',
    width: '25.4%',
    transform: 'translate(-50%, -50%)',
  },
  undoRedo: {
    left: '72.29%',
    top: '9.71%',
    width: '19.05%',
    height: '3.22%',
  },
  toolPen: {
    left: '28.23%',
    top: '14.77%',
    width: '7.67%',
    height: '3.79%',
  },
  toolEraser: {
    left: '46.13%',
    top: '14.77%',
    width: '7.67%',
    height: '3.6%',
  },
  toolTrash: {
    left: '63.72%',
    top: '14.98%',
    width: '7.67%',
    height: '3.58%',
  },
  sliderPanel: {
    left: '5.65%',
    top: '19.91%',
    width: '88.71%',
    height: '5.61%',
    background: PHONE_COLORS.drawingPanel,
    borderRadius: '4px',
    boxShadow: '0 1.3px 4px rgba(0, 0, 0, 0.5)',
  },
  paper: {
    left: '8.42%',
    top: '32.17%',
    width: '83.16%',
  },
  paperSurface: {
    background: PHONE_COLORS.drawingPaper,
    boxShadow: '0.7px 0.7px 3.5px rgba(0, 0, 0, 0.6)',
  },
  previewStroke: {
    color: PHONE_COLORS.black,
  },
  sliderPreview: {
    left: '5.6%',
    top: '18.2%',
    width: '38.8%',
    height: '63.6%',
    background: PHONE_COLORS.white,
  },
  sliderTrackByTool: {
    pen: {
      left: '55.2%',
      top: '31.54%',
      width: '25.5%',
      height: '49.2%',
    },
    eraser: {
      left: '21.33%',
      top: '31.54%',
      width: '61.23%',
      height: '49.2%',
    },
  },
  sliderClose: {
    left: '91.35%',
    top: '31.54%',
    width: '5.17%',
    height: '40%',
  },
  actionButtonRow: {
    left: '6.51%',
    top: '85.35%',
    width: '85.69%',
    height: '7.73%',
    display: 'grid',
    gridTemplateColumns: 'repeat(2, minmax(0, 1fr))',
    columnGap: '6.55%',
  },
  saveButton: {
    background: PHONE_COLORS.saveButton,
    borderRadius: '4px',
    boxShadow: '0 1.2px 1.2px rgba(0, 0, 0, 0.25)',
    color: PHONE_COLORS.drawingButtonText,
  },
  printButton: {
    background: PHONE_COLORS.printButton,
    borderRadius: '4px',
    boxShadow: '0 1.2px 1.2px rgba(0, 0, 0, 0.25)',
    color: PHONE_COLORS.drawingButtonText,
  },
} as const

export const PHONE_DRAWING_STAGE_SIZE = {
  width: 327,
  height: 327,
}

export const PHONE_DRAWING_PAPER_COLOR = PHONE_COLORS.drawingPaper

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
  key: 'drawing' | 'gallery' | 'shop' | 'cs'
  action: 'open-drawing' | 'open-gallery' | 'open-external' | 'open-inquiry'
  label: string
  asset: StaticImageData
  isEnabled: boolean
  externalUrl?: string
}> = [
  {
    key: 'drawing',
    action: 'open-drawing',
    label: '네모닉 그림판',
    asset: phoneAppDrawing,
    isEnabled: true,
  },
  {
    key: 'gallery',
    action: 'open-gallery',
    label: '갤러리',
    asset: phoneAppGallery,
    isEnabled: true,
  },
  {
    key: 'shop',
    action: 'open-external',
    label: '공식몰 바로가기',
    asset: phoneAppShop,
    externalUrl: PHONE_OFFICIAL_STORE_URL,
    isEnabled: true,
  },
  {
    key: 'cs',
    action: 'open-inquiry',
    label: '고객 문의',
    asset: phoneAppCs,
    isEnabled: true,
  },
]

export const PHONE_INQUIRY_TYPE_OPTIONS: Array<{
  value: CsInquiryType
  label: string
}> = [
  { value: 'error', label: '오류/버그 신고' },
  { value: 'feature_request', label: '기능 제안' },
  { value: 'content_report', label: '콘텐츠 신고' },
  { value: 'other', label: '기타 문의' },
]

export const PHONE_GALLERY_FILTERS: Array<{
  key: PhoneGalleryFilterKey
  label: string
}> = [
  { key: 'all', label: '전체' },
  { key: 'phone', label: '폰 그림' },
  { key: 'fortune', label: '운세' },
  { key: 'flipbook', label: '플립북' },
  { key: 'relay', label: '릴레이' },
  { key: 'infinite', label: '캔버스' },
]

export const PHONE_GALLERY_ITEM_STYLES: Record<
  PhoneGalleryItemKind,
  { label: string; color: string; background: string }
> = {
  phone: {
    label: '폰 그림',
    color: PHONE_COLORS.galleryFilterPhone,
    background: PHONE_COLORS.galleryFilterPhoneBackground,
  },
  fortune: {
    label: '운세',
    color: PHONE_COLORS.galleryFilterFortune,
    background: PHONE_COLORS.galleryFilterFortuneBackground,
  },
  flipbook: {
    label: '플립북',
    color: PHONE_COLORS.galleryFilterFlipbook,
    background: PHONE_COLORS.galleryFilterFlipbookBackground,
  },
  relay: {
    label: '릴레이',
    color: PHONE_COLORS.galleryFilterRelay,
    background: PHONE_COLORS.galleryFilterRelayBackground,
  },
  infinite: {
    label: '캔버스',
    color: PHONE_COLORS.galleryFilterInfinite,
    background: PHONE_COLORS.galleryFilterInfiniteBackground,
  },
}

