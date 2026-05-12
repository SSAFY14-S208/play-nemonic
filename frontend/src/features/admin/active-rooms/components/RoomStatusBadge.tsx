import { cn } from '@/shared/libs'

// 백오피스 활성 방 status 표시. relay/flipbook 양쪽 가능한 모든 상태를 한 곳에서 매핑.
// CLOSED는 백엔드가 응답에서 제외하지만 type 호환을 위해 포함.

interface RoomStatusBadgeProps {
  status: string
}

const STATUS_PRESET: Record<string, { label: string; className: string }> = {
  WAITING: { label: '대기중', className: 'bg-amber-100 text-amber-700' },
  PLAYING: { label: '진행중', className: 'bg-emerald-100 text-emerald-700' },
  FINALIZING: { label: '마무리중', className: 'bg-sky-100 text-sky-700' },
  FINISHED: { label: '완료', className: 'bg-slate-100 text-slate-600' },
  CLOSED: { label: '종료됨', className: 'bg-slate-200 text-slate-600' },
}

export function RoomStatusBadge({ status }: RoomStatusBadgeProps) {
  const preset = STATUS_PRESET[status] ?? {
    label: status,
    className: 'bg-slate-100 text-slate-600',
  }
  return (
    <span
      className={cn(
        'caption-b inline-flex items-center gap-1 rounded-full px-2 py-0.5',
        preset.className,
      )}
    >
      <span className="h-1.5 w-1.5 rounded-full bg-current opacity-70" />
      {preset.label}
    </span>
  )
}
