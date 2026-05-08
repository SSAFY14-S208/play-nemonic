interface FortuneErrorViewProps {
  message: string
  onRetry: () => void
}

export default function FortuneErrorView({ message, onRetry }: FortuneErrorViewProps) {
  return (
    <section className="fortune-floating-panel grid gap-5 rounded-[var(--radius-xl)] border border-fortune-border bg-fortune-panel p-6 text-center shadow-soft-lg">
      <h1 className="h2-b text-fortune-ink">운세를 가져오지 못했어요</h1>
      <p className="body-r text-fortune-muted">{message}</p>
      <button
        type="button"
        className="body-b fortune-primary-button min-h-12 rounded-[var(--radius-md)] bg-fortune-accent px-4 text-fortune-inverse"
        onClick={onRetry}
      >
        다시 뽑기
      </button>
    </section>
  )
}
