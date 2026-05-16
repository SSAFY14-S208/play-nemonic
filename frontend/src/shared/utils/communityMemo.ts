import type { CommunityMemoItemResponse } from '@/shared/types'

export const DEFAULT_COMMUNITY_MEMO_COLOR = '#ffe887'

const COMMUNITY_MEMO_COLOR_PATTERN = /^#[0-9a-fA-F]{6}$/

function readMemoColorFromDecoration(decoration: unknown) {
  if (!decoration || typeof decoration !== 'object') return null

  const memoColor = (decoration as { memoColor?: unknown }).memoColor
  if (typeof memoColor !== 'string') return null
  if (!COMMUNITY_MEMO_COLOR_PATTERN.test(memoColor)) return null

  return memoColor
}

export function getCommunityMemoColor(memo: Pick<CommunityMemoItemResponse, 'decoration'>) {
  return readMemoColorFromDecoration(memo.decoration) ?? DEFAULT_COMMUNITY_MEMO_COLOR
}
