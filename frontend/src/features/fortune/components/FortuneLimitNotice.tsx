'use client'

import { LogOut, Sparkles } from 'lucide-react'
import { Fragment } from 'react'

import { cn } from '@/shared/libs'

import { useFortuneSessionStore } from '../fortuneSessionStore'
import { useFortuneReducedMotion, useFortuneTypewriterText } from '../hooks'
import { getNextKoreanMidnightLabel } from '../utils'

import {
  DIALOGUE_COPY_CLASS,
  DIALOGUE_PANEL_CLASS,
  DIALOGUE_SPEAKER_CLASS,
  FORTUNE_DIALOGUE_GLYPH_CLASS,
  FortuneDialogueCaret,
} from './FortuneDialoguePanel'
import FortuneDrawAction from './FortuneDrawAction'

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
    <section
      // DIALOGUE_PANEL_CLASS의 `grid items-end`는 기본 align-content가 stretch라
      // 행이 늘어나서 빈 공간이 행 안에 생김. auto-rows-min + content-center로
      // 행을 콘텐츠 크기에 맞추고 전체 콘텐츠를 패널 가운데로 모음.
      className={cn(DIALOGUE_PANEL_CLASS, "gap-0 auto-rows-min content-center")}
      aria-label="포포의 한도 안내"
    >
      <p className={DIALOGUE_SPEAKER_CLASS}>포포</p>
      {/* 대사 — col-span-full로 그리드 2컬럼을 가로지르게 함 + 폰트 축소 */}
      <p
        className={cn(
          DIALOGUE_COPY_CLASS,
          "col-span-full text-center",
          LIMIT_COPY_CLASS,
        )}
        aria-label={plainText}
        onClick={handleAdvance}
      >
        {renderLines(LIMIT_DIALOGUE_LINES, visibleCharacterCount)}
        {!isComplete && <FortuneDialogueCaret />}
      </p>
      {/* 자정 안내 캡션 — 대사 바로 아래, 가운데 정렬 */}
      <p className="col-span-full caption-r mt-1 text-center text-fortune-muted">
        {nextResetLabel}
      </p>
      {/* 액션 버튼 두 개 — col-span-full로 그리드 풀고 flex 중앙 정렬 */}
      <div
        className={cn(
          "col-span-full mt-3 flex items-center justify-center",
          "gap-[clamp(0.6rem,1.4vw,1.8rem)]",
        )}
      >
        <FortuneDrawAction
          tone="edit"
          className={LIMIT_ACTION_CLASS}
          icon={<Sparkles className={LIMIT_ACTION_ICON_CLASS} aria-hidden />}
          disabled={!result}
          onClick={onShowResult}
        >
          운세 확인
        </FortuneDrawAction>
        <FortuneDrawAction
          tone="edit"
          className={LIMIT_ACTION_CLASS}
          icon={<LogOut className={LIMIT_ACTION_ICON_CLASS} aria-hidden />}
          onClick={onBackToHub}
        >
          종료하기
        </FortuneDrawAction>
      </div>
    </section>
  )
}

// 한도 안내 패널 안에서 사용할 작은 사이즈 override.
// 대사 폰트는 다이얼로그 패널의 기본보다 한 단계 축소 — 두 줄 + caption + 버튼이
// 한 박스에 같이 들어가야 해서 공간 확보가 필요.
const LIMIT_COPY_CLASS = cn(
  "text-[clamp(0.9rem,1.5vw,1.25rem)]",
  "max-[800px]:text-[clamp(0.78rem,3.4vw,1rem)]",
)

// 액션 버튼 사이즈 override — 기본 FortuneDrawAction(w-[clamp(9rem,30vw,22rem)])
// 보다 작게 잡아 패널 가로 폭 안에 두 개가 여유 있게 들어오도록.
const LIMIT_ACTION_CLASS = cn(
  "w-[clamp(7rem,20vw,15rem)] px-[clamp(0.5rem,1.5vw,1.2rem)]",
  "text-[clamp(0.62rem,1.2vw,0.95rem)]",
  "max-[767px]:portrait:w-[36vw] max-[767px]:portrait:px-[0.7rem]",
  "max-[767px]:portrait:text-[clamp(0.7rem,2.6vw,0.95rem)]",
)

const LIMIT_ACTION_ICON_CLASS = cn(
  "w-[clamp(0.85rem,1.4vw,1.3rem)] h-[clamp(0.85rem,1.4vw,1.3rem)]",
  "max-[767px]:portrait:w-[clamp(1rem,3.4vw,1.25rem)] max-[767px]:portrait:h-[clamp(1rem,3.4vw,1.25rem)]",
)

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
                className={FORTUNE_DIALOGUE_GLYPH_CLASS}
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
