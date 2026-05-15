'use client'

/* eslint-disable @next/next/no-img-element */

import { Feather, Sparkles } from 'lucide-react'
import { useMemo } from 'react'
import { useShallow } from 'zustand/react/shallow'

import { useFortuneSessionStore } from '../fortuneSessionStore'
import { calculateFortuneSaju, isBirthInfoComplete } from '../utils'

import FortuneDrawAction from './FortuneDrawAction'

interface FortuneDrawPanelProps {
  onDraw: () => void
  onEdit: () => void
}

const PILLAR_KEYS: Array<'year' | 'month' | 'day' | 'hour'> = ['year', 'month', 'day', 'hour']

const DRAW_RISE = 'animate-fortune-draw-rise motion-reduce:animate-none'
const DRAW_ACTIONS_RISE = 'animate-fortune-draw-actions-rise motion-reduce:animate-none'

const SPEECH_BUBBLE_CLASS = [
  'absolute left-[clamp(1vw,3vw,5vw)] bottom-[clamp(56dvh,62dvh,68dvh)] h-auto w-[min(56vw,44rem)]',
  'select-none pointer-events-none',
  '[filter:drop-shadow(0_0.6rem_1.2rem_rgba(8,1,22,0.55))]',
  DRAW_RISE,
  '[animation-delay:1620ms]',
].join(' ')

const INFO_PANEL_CLASS = [
  'absolute right-[clamp(0vw,2vw,4vw)] top-[clamp(6dvh,12dvh,18dvh)] w-[min(69vw,54rem)] aspect-[358/245] pointer-events-none',
  DRAW_RISE,
  '[animation-delay:120ms]',
].join(' ')

const INFO_FRAME_CLASS = 'absolute inset-0 h-full w-full object-contain select-none'

const INFO_DATE_CLASS = [
  'absolute top-[32%] left-[calc(50%+4vw)] -translate-x-1/2 w-[84%] text-center whitespace-nowrap',
  'font-fortune-eulyoo font-semibold text-[clamp(1.3rem,2.4vw,2rem)] tracking-[0.04em] text-[#fff8ff]',
  '[text-shadow:0_0_0.5rem_rgba(220,170,255,0.7),0_0_0.18rem_rgba(255,255,255,0.5)]',
].join(' ')

const PILLAR_VALUE_CLASS = [
  'font-fortune-serif text-[clamp(1.05rem,1.85vw,1.55rem)] text-white',
  '[text-shadow:0_0_0.5rem_rgba(220,170,255,0.75),0_0_0.15rem_rgba(255,255,255,0.55)]',
].join(' ')

const ACTION_ICON_CLASS = 'w-[clamp(1.1rem,1.5vw,1.4rem)] h-[clamp(1.1rem,1.5vw,1.4rem)]'

const ACTIONS_ROW_CLASS = [
  'absolute left-1/2 bottom-[clamp(3dvh,5dvh,7dvh)] -translate-x-1/2',
  'flex items-center gap-[clamp(1rem,2vw,2.2rem)]',
  DRAW_ACTIONS_RISE,
  '[animation-delay:3120ms]',
].join(' ')

export default function FortuneDrawPanel({ onDraw, onEdit }: FortuneDrawPanelProps) {
  const { birthInfo, isDrawing } = useFortuneSessionStore(
    useShallow((state) => ({
      birthInfo: state.birthInfo,
      isDrawing: state.isDrawingFortune,
    })),
  )
  const saju = useMemo(() => {
    if (!isBirthInfoComplete(birthInfo)) return null
    try {
      return calculateFortuneSaju(birthInfo)
    } catch {
      return null
    }
  }, [birthInfo])

  const calendarLabel = birthInfo.calendarType === 'solar' ? '양력' : '음력'
  const timeLabel = birthInfo.timeUnknown ? '시간 모름' : birthInfo.birthTime
  const pillarValueByKey = saju
    ? {
        year: saju.sajuYear,
        month: saju.sajuMonth,
        day: saju.sajuDay,
        hour: saju.sajuHour,
      }
    : null

  return (
    <div className="fixed inset-0 z-5 pointer-events-none *:pointer-events-auto" aria-label="사주 입력 정보 확인">
      <img
        className={SPEECH_BUBBLE_CLASS}
        src="/images/fortune/draw/speech-bubble.png"
        alt="포포: 좋아 이 정보 맞지? 그럼 네모닉에 마법을 걸어 오늘의 운세 메모를 뽑아보자."
        draggable={false}
        onDragStart={(event) => event.preventDefault()}
      />
      <div className={INFO_PANEL_CLASS} aria-hidden={false}>
        <img
          className={INFO_FRAME_CLASS}
          src="/images/fortune/draw/info-panel.png"
          alt=""
          draggable={false}
          onDragStart={(event) => event.preventDefault()}
        />
        <p className={INFO_DATE_CLASS}>
          {calendarLabel} {birthInfo.birthDate} {timeLabel}
        </p>
        {pillarValueByKey && (
          <ul className="absolute top-[57%] left-[calc(50%+4vw)] -translate-x-1/2 grid grid-cols-4 w-[64%] m-0 p-0 list-none">
            {PILLAR_KEYS.map((key) => (
              <li key={key} className="flex items-center justify-center">
                <span className={PILLAR_VALUE_CLASS}>{pillarValueByKey[key]}</span>
              </li>
            ))}
          </ul>
        )}
      </div>

      <div className={ACTIONS_ROW_CLASS}>
        <FortuneDrawAction tone="edit" icon={<Feather className={ACTION_ICON_CLASS} aria-hidden />} onClick={onEdit}>
          수정하기
        </FortuneDrawAction>
        <FortuneDrawAction
          tone="print"
          icon={<Sparkles className={ACTION_ICON_CLASS} aria-hidden />}
          disabled={isDrawing}
          onClick={onDraw}
        >
          {isDrawing ? '포포가 준비 중' : '오늘의 운세 인쇄하기'}
        </FortuneDrawAction>
      </div>
    </div>
  )
}
