import type { ReactNode } from 'react'

interface FortuneDialoguePanelProps {
  dialogueIndex: number
  onNext: () => void
}

export const FORTUNE_DIALOGUES: Array<{
  body: ReactNode
  actionLabel: string
}> = [
  {
    body: (
      <>
        안녕 난 <span className="text-fortune-accent">네모닉</span> 마법사 포포!
        <br />
        오늘의 운세 메모를 뽑아줄게~
      </>
    ),
    actionLabel: '다음 이야기 듣기',
  },
  {
    body: (
      <>
        사주에 기반한 멋진 운세를 뽑기 위해서,
        <br />
        정보를 입력해줘!
      </>
    ),
    actionLabel: '정보 입력하기',
  },
]

export default function FortuneDialoguePanel({ dialogueIndex, onNext }: FortuneDialoguePanelProps) {
  const dialogue = FORTUNE_DIALOGUES[dialogueIndex] ?? FORTUNE_DIALOGUES[0]

  return (
    <section className="fortune-dialogue-panel" aria-label="포포의 안내">
      <p className="fortune-dialogue-speaker">포포</p>
      <p className="fortune-dialogue-copy">{dialogue.body}</p>
      <button type="button" className="fortune-dialogue-next" onClick={onNext}>
        {dialogue.actionLabel}
      </button>
    </section>
  )
}
