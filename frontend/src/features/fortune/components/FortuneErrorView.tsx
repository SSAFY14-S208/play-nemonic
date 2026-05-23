import { useFortuneSessionStore } from '..'

import FortuneFloatingPanel from './FortuneFloatingPanel'

interface FortuneErrorViewProps {
  onRetry: () => void
}

// background-color와 background-image를 분리합니다. 한 클래스 안에 gradient와
// 색상 var를 콤마로 같이 적으면 background-image에 색상이 들어왔다며
// lightningcss가 빌드를 거부합니다.
const PRIMARY_BUTTON_BG =
  'bg-fortune-accent bg-[linear-gradient(180deg,rgba(255,255,255,0.22),rgba(255,255,255,0))]'

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
