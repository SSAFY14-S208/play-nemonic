import { useFortuneSessionStore } from '../fortuneSessionStore'

import FortuneFloatingPanel from './FortuneFloatingPanel'

interface FortuneErrorViewProps {
  onRetry: () => void
}

const PRIMARY_BUTTON_BG =
  'bg-[linear-gradient(180deg,rgba(255,255,255,0.22),rgba(255,255,255,0)),var(--color-fortune-accent)]'

const PRIMARY_BUTTON_SHADOW =
  'shadow-[0_0.8rem_1.7rem_rgba(88,52,129,0.24),0_0_0_0.2rem_rgba(255,234,160,0.2),inset_0_0.1rem_0_rgba(255,255,255,0.3)]'

const PRIMARY_BUTTON_SHINE =
  "relative overflow-hidden after:content-[''] after:absolute after:inset-0 after:-translate-x-[120%] after:skew-x-[-18deg] after:bg-[linear-gradient(90deg,rgba(255,255,255,0),rgba(255,255,255,0.35),rgba(255,255,255,0))] after:transition-transform after:duration-[420ms] after:ease hover:after:translate-x-[120%] motion-reduce:after:transition-none"

export default function FortuneErrorView({ onRetry }: FortuneErrorViewProps) {
  const message = useFortuneSessionStore((state) => state.errorMessage)

  return (
    <FortuneFloatingPanel className="grid gap-5 p-6">
      <h1 className="h2-b text-fortune-ink">운세를 가져오지 못했어요</h1>
      <p className="body-r text-fortune-muted">{message}</p>
      <button
        type="button"
        className={[
          'body-b min-h-12 rounded-[var(--radius-md)] border-0 px-4 text-fortune-inverse',
          PRIMARY_BUTTON_BG,
          PRIMARY_BUTTON_SHADOW,
          PRIMARY_BUTTON_SHINE,
        ].join(' ')}
        onClick={onRetry}
      >
        다시 뽑기
      </button>
    </FortuneFloatingPanel>
  )
}
