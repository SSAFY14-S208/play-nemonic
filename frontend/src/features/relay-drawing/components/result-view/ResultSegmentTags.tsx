import { cn } from '@/shared/libs'

import { RESULT_SEGMENTS } from './resultSegments'

// 최종 합성 캔버스 위에 얹는 세그먼트 태그(🐱 고양이 · 얼굴 등).
export default function ResultSegmentTags() {
  return (
    <>
      {RESULT_SEGMENTS.map((segment, index) => (
        <span
          key={segment.key}
          className={cn(
            'caption-b absolute left-4 rounded-full px-3 py-1 text-relay-ink',
            segment.tagClassName,
          )}
          style={{ top: `${3 + index * 29}%` }}
        >
          {segment.tagLabel}
        </span>
      ))}
    </>
  )
}
