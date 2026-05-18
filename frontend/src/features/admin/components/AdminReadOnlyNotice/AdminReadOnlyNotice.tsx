interface AdminReadOnlyNoticeProps {
  message?: string
}

export function AdminReadOnlyNotice({
  message = '뷰어 권한은 조회만 가능합니다.',
}: AdminReadOnlyNoticeProps) {
  return (
    <p className="caption-r rounded-[var(--radius-md)] border border-border-default bg-surface-subtle px-3 py-2 text-fg-secondary">
      {message}
    </p>
  )
}
