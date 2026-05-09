'use client'

/* eslint-disable @next/next/no-img-element */

import { Feather, Sparkles } from 'lucide-react'
import { useMemo } from 'react'
import { useShallow } from 'zustand/react/shallow'

import { useFortuneSessionStore } from '../fortuneSessionStore'
import { calculateFortuneSaju, isBirthInfoComplete } from '../utils'

interface FortuneDrawPanelProps {
  onDraw: () => void
  onEdit: () => void
}

const PILLAR_KEYS: Array<'year' | 'month' | 'day' | 'hour'> = ['year', 'month', 'day', 'hour']

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
    <div className="fortune-draw-stage" aria-label="사주 입력 정보 확인">
      <img
        className="fortune-draw-speech-bubble"
        src="/images/fortune/draw/speech-bubble.png"
        alt="포포: 좋아 이 정보 맞지? 그럼 네모닉에 마법을 걸어 오늘의 운세 메모를 뽑아보자."
        draggable={false}
        onDragStart={(event) => event.preventDefault()}
      />
      <div className="fortune-draw-info-panel" aria-hidden={false}>
        <img
          className="fortune-draw-info-frame"
          src="/images/fortune/draw/info-panel.png"
          alt=""
          draggable={false}
          onDragStart={(event) => event.preventDefault()}
        />
        <p className="fortune-draw-info-date">
          {calendarLabel} {birthInfo.birthDate} {timeLabel}
        </p>
        {pillarValueByKey && (
          <ul className="fortune-draw-pillar-grid">
            {PILLAR_KEYS.map((key) => (
              <li key={key} className="fortune-draw-pillar-cell">
                <span className="fortune-draw-pillar-value">{pillarValueByKey[key]}</span>
              </li>
            ))}
          </ul>
        )}
      </div>

      <div className="fortune-draw-actions">
        <button
          type="button"
          className="fortune-draw-action fortune-draw-action-edit"
          onClick={onEdit}
        >
          <Feather className="fortune-draw-action-icon" aria-hidden />
          <span>수정하기</span>
        </button>
        <button
          type="button"
          className="fortune-draw-action fortune-draw-action-print"
          disabled={isDrawing}
          onClick={onDraw}
        >
          <Sparkles className="fortune-draw-action-icon" aria-hidden />
          <span>{isDrawing ? '포포가 준비 중' : '오늘의 운세 인쇄하기'}</span>
        </button>
      </div>
    </div>
  )
}
