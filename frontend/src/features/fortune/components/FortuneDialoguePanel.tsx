'use client'

import { Fragment } from 'react'

import { useFortuneReducedMotion, useFortuneTypewriterText } from '../hooks'

interface FortuneDialoguePanelProps {
  dialogueIndex: number
  onNext: () => void
}

interface FortuneDialogueTextSegment {
  text: string
  accent?: boolean
}

type FortuneDialogueLine = FortuneDialogueTextSegment[]

interface FortuneDialogue {
  lines: FortuneDialogueLine[]
  actionLabel: string
}

const INTRO_DIALOGUE_TYPEWRITER_START_DELAY_MS = 1120
const NEXT_DIALOGUE_TYPEWRITER_START_DELAY_MS = 120

export const FORTUNE_DIALOGUES: FortuneDialogue[] = [
  {
    lines: [
      [
        { text: '안녕 난 ' },
        { text: '네모닉', accent: true },
        { text: ' 마법사 포포!' },
      ],
      [{ text: '오늘의 운세 메모를 뽑아줄게~' }],
    ],
    actionLabel: '다음 이야기 듣기',
  },
  {
    lines: [
      [{ text: '사주에 기반한 멋진 운세를 뽑기 위해서,' }],
      [{ text: '정보를 입력해줘!' }],
    ],
    actionLabel: '정보 입력하기',
  },
]

export default function FortuneDialoguePanel({ dialogueIndex, onNext }: FortuneDialoguePanelProps) {
  const dialogue = FORTUNE_DIALOGUES[dialogueIndex] ?? FORTUNE_DIALOGUES[0]
  const startDelayMs =
    dialogueIndex === 0
      ? INTRO_DIALOGUE_TYPEWRITER_START_DELAY_MS
      : NEXT_DIALOGUE_TYPEWRITER_START_DELAY_MS

  return (
    <FortuneDialoguePanelContent
      key={dialogueIndex}
      dialogue={dialogue}
      startDelayMs={startDelayMs}
      onNext={onNext}
    />
  )
}

function FortuneDialoguePanelContent({
  dialogue,
  startDelayMs,
  onNext,
}: {
  dialogue: FortuneDialogue
  startDelayMs: number
  onNext: () => void
}) {
  const prefersReducedMotion = useFortuneReducedMotion()
  const characterCount = getDialogueCharacterCount(dialogue)
  const plainText = getDialoguePlainText(dialogue)
  const { completeText, isComplete, visibleCharacterCount } = useFortuneTypewriterText({
    characterCount,
    prefersReducedMotion,
    startDelayMs,
  })
  const buttonLabel = isComplete ? dialogue.actionLabel : '대사 모두 표시하기'

  const handleNext = () => {
    if (!isComplete) {
      completeText()
      return
    }

    onNext()
  }

  return (
    <section className="fortune-dialogue-panel" aria-label="포포의 안내">
      <p className="fortune-dialogue-speaker">포포</p>
      <p className="fortune-dialogue-copy" aria-label={plainText}>
        {renderDialogueLines(dialogue, visibleCharacterCount)}
        {!isComplete && <span className="fortune-dialogue-caret" aria-hidden />}
      </p>
      <button
        type="button"
        className="fortune-dialogue-next"
        aria-label={buttonLabel}
        onClick={handleNext}
      >
        <span className="fortune-dialogue-next-label" aria-hidden>
          {buttonLabel}
        </span>
      </button>
    </section>
  )
}

function getDialogueCharacterCount(dialogue: FortuneDialogue) {
  return dialogue.lines.reduce((totalCharacterCount, line) => {
    const lineCharacterCount = line.reduce((lineTotal, segment) => {
      return lineTotal + Array.from(segment.text).length
    }, 0)

    return totalCharacterCount + lineCharacterCount
  }, 0)
}

function getDialoguePlainText(dialogue: FortuneDialogue) {
  return dialogue.lines
    .map((line) => line.map((segment) => segment.text).join(''))
    .join('\n')
}

function renderDialogueLines(dialogue: FortuneDialogue, visibleCharacterCount: number) {
  let remainingCharacterCount = visibleCharacterCount

  return dialogue.lines.map((line, lineIndex) => {
    const lineContent = line.map((segment, segmentIndex) => {
      const segmentCharacters = Array.from(segment.text)
      const visibleSegmentCharacterCount = Math.min(
        remainingCharacterCount,
        segmentCharacters.length,
      )

      remainingCharacterCount = Math.max(
        remainingCharacterCount - segmentCharacters.length,
        0,
      )

      if (visibleSegmentCharacterCount <= 0) {
        return null
      }

      const visibleSegmentCharacters = segmentCharacters.slice(0, visibleSegmentCharacterCount)

      return (
        <span
          key={`${lineIndex}-${segmentIndex}`}
          className={segment.accent ? 'text-fortune-accent' : undefined}
        >
          {visibleSegmentCharacters.map((character, characterIndex) => {
            if (character === ' ') {
              return character
            }

            return (
              <span
                key={`${lineIndex}-${segmentIndex}-${characterIndex}`}
                className="fortune-dialogue-glyph"
              >
                {character}
              </span>
            )
          })}
        </span>
      )
    })

    return (
      <Fragment key={lineIndex}>
        {lineIndex > 0 && <br />}
        {lineContent}
      </Fragment>
    )
  })
}
