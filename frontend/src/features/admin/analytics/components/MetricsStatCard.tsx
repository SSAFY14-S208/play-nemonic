'use client'

type Props = {
  // 큰 숫자 또는 문자열 (예: "12,345" / "—" / "DOWN").
  value: string
  // 보조 라벨 (예: "활성 사용자").
  label?: string
  // 추가 hint (예: "최근 1시간 distinct uuid").
  hint?: string
  accentColor?: string
  isLoading?: boolean
}

// 단일 숫자 stat. KPI 카드 사이즈와 비슷하지만 1개 cell만.

export function MetricsStatCard({
  value,
  label,
  hint,
  accentColor,
  isLoading = false,
}: Props) {
  return (
    <div className="flex h-full flex-col items-center justify-center gap-1 p-4 text-center">
      <p
        className="h1-b"
        style={{
          color: accentColor ?? 'var(--color-fg-primary, currentColor)',
          opacity: isLoading ? 0.45 : 1,
        }}
      >
        {isLoading ? '—' : value}
      </p>
      {label && <p className="caption-b text-fg-secondary">{label}</p>}
      {hint && <p className="caption-r text-fg-disabled">{hint}</p>}
    </div>
  )
}
