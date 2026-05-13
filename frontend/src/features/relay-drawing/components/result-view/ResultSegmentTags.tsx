import type { RelayResultSegment } from '../../constants'
import { cn } from '@/shared/libs'

interface ResultSegmentTagsProps {
  segments: RelayResultSegment[]
}

// 최종 합성 캔버스 위에 얹는 세그먼트 태그(닉네임 · 라운드 라벨).
export default function ResultSegmentTags({ segments }: ResultSegmentTagsProps) {
  return (
    <>
      {segments.map((segment, index) => (
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
