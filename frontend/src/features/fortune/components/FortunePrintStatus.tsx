import { useFortuneSessionStore } from '../fortuneSessionStore'

import FortuneFloatingPanel from './FortuneFloatingPanel'

const SIGIL_BEFORE = [
  "before:content-['']",
  'before:absolute before:left-1/2 before:top-[0.35rem] before:h-[0.32rem] before:w-full',
  'before:-translate-x-1/2 before:rounded-full',
  'before:bg-[linear-gradient(90deg,rgba(255,230,144,0),rgba(255,219,101,0.88),rgba(255,230,144,0))]',
  'before:shadow-[0_0_1rem_rgba(255,215,89,0.42)]',
  'before:animate-fortune-sigil-glow motion-reduce:before:animate-none',
].join(' ')

const SIGIL_AFTER = [
  "after:content-['']",
  'after:absolute after:left-1/2 after:top-0 after:h-4 after:w-4',
  'after:-translate-x-1/2 after:rotate-45',
  'after:bg-[#fff0a8]',
  'after:shadow-[0_0_0.8rem_rgba(255,214,93,0.48)]',
].join(' ')

export default function FortunePrintStatus() {
  const isPrinting = useFortuneSessionStore((state) => state.step === 'printing')

  return (
    <FortuneFloatingPanel className="p-6">
      <div
        aria-hidden
        className={`relative mx-auto mb-[0.8rem] h-[1.2rem] w-[min(8.5rem,42vw)] ${SIGIL_BEFORE} ${SIGIL_AFTER}`}
      />
      <p className="caption-b text-fortune-muted">3 / 3</p>
      <h1 className="h2-b mt-1 text-fortune-ink">
        {isPrinting ? '오늘의 기운을 메모에 담는 중' : '포포가 메모를 준비하고 있어요'}
      </h1>
      <p className="body-r mt-3 text-fortune-muted">
        네모닉 프린터에서 작은 운세 메모가 천천히 밀려 나옵니다.
      </p>
    </FortuneFloatingPanel>
  )
}
