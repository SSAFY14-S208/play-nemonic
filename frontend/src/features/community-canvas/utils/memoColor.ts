import type { CommunityMemoItemResponse } from '@/shared/types'

export const DEFAULT_COMMUNITY_MEMO_COLOR = '#ffe887'

export const COMMUNITY_MEMO_COLOR_OPTIONS = [
  { name: '레몬', value: '#ffe887' },
  { name: '민트', value: '#dff4b8' },
  { name: '하늘', value: '#cceef6' },
  { name: '복숭아', value: '#ffd6b8' },
  { name: '분홍', value: '#ffd0dc' },
  { name: '라벤더', value: '#ded6ff' },
]

function readMemoColorFromDecoration(decoration: unknown) {
  if (!decoration || typeof decoration !== 'object') return null
  const memoColor = (decoration as { memoColor?: unknown }).memoColor
  if (typeof memoColor !== 'string') return null
  if (!/^#[0-9a-fA-F]{6}$/.test(memoColor)) return null
  return memoColor
}

export function getCommunityMemoColor(memo: CommunityMemoItemResponse) {
  return readMemoColorFromDecoration(memo.decoration) ?? DEFAULT_COMMUNITY_MEMO_COLOR
}
