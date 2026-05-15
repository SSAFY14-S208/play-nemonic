'use client'

import { Fragment } from 'react'

import { useFortuneSessionStore } from '../fortuneSessionStore'
import { useFortuneReducedMotion, useFortuneTypewriterText } from '../hooks'
import { getNextKoreanMidnightLabel } from '../utils'

interface FortuneLimitNoticeProps {
  onShowResult: () => void
  onBackToHub: () => void
}

interface LimitDialogueSegment {
  text: string
  accent?: boolean
}

type LimitDialogueLine = LimitDialogueSegment[]

const LIMIT_DIALOGUE_LINES: LimitDialogueLine[] = [
  [
    { text: '오늘은 이미 ' },
    { text: '오늘의 운세', accent: true },
    { text: '를 확인했어!' },
  ],
  [{ text: '내일 다시 찾아와줘~' }],
]

const TYPEWRITER_START_DELAY_MS = 320

export default function FortuneLimitNotice({ onShowResult, onBackToHub }: FortuneLimitNoticeProps) {
  const result = useFortuneSessionStore((state) => state.result)
  const prefersReducedMotion = useFortuneReducedMotion()
  const characterCount = getCharacterCount(LIMIT_DIALOGUE_LINES)
  const plainText = getPlainText(LIMIT_DIALOGUE_LINES)
  const { completeText, isComplete, visibleCharacterCount } = useFortuneTypewriterText({
    characterCount,
    prefersReducedMotion,
    startDelayMs: TYPEWRITER_START_DELAY_MS,
  })
  const nextResetLabel = getNextKoreanMidnightLabel()

  const handleAdvance = () => {
    if (!isComplete) {
      completeText()
    }
  }

  return (
    <section className="fortune-dialogue-panel" aria-label="포포의 한도 안내">
      <p className="fortune-dialogue-speaker">포포</p>
      <p className="fortune-dialogue-copy" aria-label={plainText} onClick={handleAdvance}>
        {renderLines(LIMIT_DIALOGUE_LINES, visibleCharacterCount)}
        {!isComplete && <span className="fortune-dialogue-caret" aria-hidden />}
      </p>
      <p className="caption-r mt-1 text-fortune-muted">{nextResetLabel}</p>
      <div className="mt-3 flex flex-wrap justify-center gap-3">
        <button
          type="button"
          disabled={!result}
          className="body-l-b min-h-12 flex-1 rounded-[var(--radius-md)] bg-fortune-accent px-5 text-fortune-inverse transition hover:-translate-y-0.5 disabled:translate-y-0 disabled:bg-fortune-disabled"
          onClick={onShowResult}
        >
          운세 확인
        </button>
        <button
          type="button"
          className="body-l-b min-h-12 flex-1 rounded-[var(--radius-md)] border border-fortune-border bg-fortune-paper px-5 text-fortune-ink transition hover:bg-fortune-glow"
          onClick={onBackToHub}
        >
          종료하기
        </button>
      </div>
    </section>
  )
}

function getCharacterCount(lines: LimitDialogueLine[]) {
  return lines.reduce((total, line) => {
    return total + line.reduce((lineTotal, segment) => lineTotal + Array.from(segment.text).length, 0)
  }, 0)
}

function getPlainText(lines: LimitDialogueLine[]) {
  return lines.map((line) => line.map((segment) => segment.text).join('')).join('\n')
}

function renderLines(lines: LimitDialogueLine[], visibleCharacterCount: number) {
  let remaining = visibleCharacterCount

  return lines.map((line, lineIndex) => {
    const lineContent = line.map((segment, segmentIndex) => {
      const segmentCharacters = Array.from(segment.text)
      const visible = Math.min(remaining, segmentCharacters.length)

      remaining = Math.max(remaining - segmentCharacters.length, 0)

      if (visible <= 0) {
        return null
      }

      const visibleCharacters = segmentCharacters.slice(0, visible)

      return (
        <span
          key={`${lineIndex}-${segmentIndex}`}
          className={segment.accent ? 'text-fortune-accent' : undefined}
        >
          {visibleCharacters.map((character, characterIndex) => {
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
