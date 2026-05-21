import { FortunePage } from '@/features/fortune'
import { WorldHomeLink } from '@/shared/components'

// Fortune 페이지 테마용 버튼 클래스. fortune feature의 cream/purple/gold 톤에 맞춘
// 색상을 confirm 모달에 적용합니다. (ADR 0003 closed visual system 범위 내)
const FORTUNE_LEAVE_CANCEL_BUTTON_CLASS =
  'bg-fortune-paper text-fortune-accent-strong border-fortune-border hover:bg-fortune-glow'

// background-color와 background-image를 분리합니다. 한 클래스 안에 gradient와
// 색상 var를 콤마로 같이 적으면 lightningcss가 background-image에 색상이
//들어왔다며 빌드를 거부합니다. tailwind-merge가 기본 primary 배경을 fortune
// accent로 자연스럽게 치환하도록 arbitrary property가 아닌 semantic 토큰을 씁니다.
const FORTUNE_LEAVE_CONFIRM_BUTTON_CLASS =
  'bg-fortune-accent bg-[linear-gradient(180deg,rgba(255,255,255,0.22),rgba(255,255,255,0))] text-fortune-inverse shadow-[0_0.6rem_1.3rem_rgba(88,52,129,0.24),inset_0_0.1rem_0_rgba(255,255,255,0.3)] hover:scale-[1.02]'

export default function Page() {
  return (
    <>
      <WorldHomeLink
        leaveConfirmCancelButtonClassName={FORTUNE_LEAVE_CANCEL_BUTTON_CLASS}
        leaveConfirmConfirmButtonClassName={FORTUNE_LEAVE_CONFIRM_BUTTON_CLASS}
      />
      <FortunePage />
    </>
  )
}
