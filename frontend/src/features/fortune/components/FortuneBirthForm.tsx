import { Moon, Sun } from 'lucide-react'
import { useState } from 'react'
import type { ChangeEvent, FormEvent } from 'react'
import { useShallow } from 'zustand/react/shallow'

import { cn } from '@/shared/libs'

import { useFortuneSessionStore } from '../fortuneSessionStore'
import type { FortuneCalendarType } from '../types'
import { isBirthInfoComplete } from '../utils'

interface FortuneBirthFormProps {
  onSubmit: () => Promise<void>
}

type BirthDatePart = 'year' | 'month' | 'day'
type BirthTimePart = 'hour' | 'minute'

interface BirthDateParts {
  year: string
  month: string
  day: string
}

interface BirthTimeParts {
  hour: string
  minute: string
}

const FIRST_BIRTH_YEAR = 1900
const CURRENT_YEAR = new Date().getFullYear()
const BIRTH_YEAR_OPTIONS = Array.from({ length: CURRENT_YEAR - FIRST_BIRTH_YEAR + 1 }, (_, yearIndex) => String(CURRENT_YEAR - yearIndex))
const BIRTH_MONTH_OPTIONS = Array.from({ length: 12 }, (_, monthIndex) => padDatePart(monthIndex + 1))
const BIRTH_HOUR_OPTIONS = Array.from({ length: 24 }, (_, hourIndex) => padDatePart(hourIndex))
const BIRTH_MINUTE_OPTIONS = Array.from({ length: 12 }, (_, minuteIndex) => padDatePart(minuteIndex * 5))

export default function FortuneBirthForm({ onSubmit }: FortuneBirthFormProps) {
  const { birthInfo, isSubmitting, setBirthInfo } = useFortuneSessionStore(
    useShallow((state) => ({
      birthInfo: state.birthInfo,
      isSubmitting: state.isSubmittingBirthInfo,
      setBirthInfo: state.setBirthInfo,
    })),
  )
  const isComplete = isBirthInfoComplete(birthInfo)
  const [birthDateParts, setBirthDateParts] = useState<BirthDateParts>(() => splitBirthDate(birthInfo.birthDate))
  const [birthTimeParts, setBirthTimeParts] = useState<BirthTimeParts>(() => splitBirthTime(birthInfo.birthTime))
  const birthDayOptions = createBirthDayOptions(birthDateParts.year, birthDateParts.month)

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    void onSubmit()
  }

  const updateCalendarType = (calendarType: FortuneCalendarType) => {
    setBirthInfo({ ...birthInfo, calendarType })
  }

  const updateBirthDatePart = (part: BirthDatePart, value: string) => {
    const nextBirthDateParts = normalizeBirthDateParts({
      ...birthDateParts,
      [part]: value,
    })

    setBirthDateParts(nextBirthDateParts)
    setBirthInfo({
      ...birthInfo,
      birthDate: formatBirthDate(nextBirthDateParts),
    })
  }

  const updateBirthTimePart = (part: BirthTimePart, value: string) => {
    const nextBirthTimeParts = {
      ...birthTimeParts,
      [part]: value,
    }

    setBirthTimeParts(nextBirthTimeParts)
    setBirthInfo({
      ...birthInfo,
      birthTime: formatBirthTime(nextBirthTimeParts),
      timeUnknown: false,
    })
  }

  const updateTimeUnknown = (event: ChangeEvent<HTMLInputElement>) => {
    const isTimeUnknown = event.target.checked

    if (isTimeUnknown) {
      setBirthTimeParts({ hour: '', minute: '' })
    }

    setBirthInfo({
      ...birthInfo,
      birthTime: isTimeUnknown ? '' : birthInfo.birthTime,
      timeUnknown: isTimeUnknown,
    })
  }

  return (
    <form className="fortune-birth-form" onSubmit={handleSubmit}>
      <div className="fortune-birth-heading">
        <span className="fortune-birth-heading-spark" aria-hidden />
        <h1>오늘의 운세를 위한 사주 정보를 알려줘</h1>
        <p>입력한 정보로 오늘의 운세 메모를 정성껏 준비할게요.</p>
      </div>

      <fieldset className="fortune-birth-fieldset">
        <legend>날짜 기준</legend>
        <div
          className="fortune-birth-segmented"
          data-calendar={birthInfo.calendarType}
          role="group"
          aria-label="양력 음력 선택"
        >
          <button
            type="button"
            className={optionButtonClassName(birthInfo.calendarType === 'solar')}
            aria-pressed={birthInfo.calendarType === 'solar'}
            onClick={() => updateCalendarType('solar')}
          >
            <Sun className="fortune-birth-option-icon" aria-hidden />
            양력
          </button>
          <button
            type="button"
            className={optionButtonClassName(birthInfo.calendarType === 'lunar')}
            aria-pressed={birthInfo.calendarType === 'lunar'}
            onClick={() => updateCalendarType('lunar')}
          >
            <Moon className="fortune-birth-option-icon" aria-hidden />
            음력
          </button>
        </div>
      </fieldset>

      <fieldset className="fortune-birth-fieldset">
        <legend>생년월일</legend>
        <div className="fortune-birth-select-grid fortune-birth-date-grid">
          <label className={selectFieldClassName(Boolean(birthDateParts.year))}>
            <span>년</span>
            <select
              required
              className="fortune-birth-select"
              value={birthDateParts.year}
              onChange={(event) => updateBirthDatePart('year', event.target.value)}
            >
              <option value=""></option>
              {BIRTH_YEAR_OPTIONS.map((yearOption) => (
                <option key={yearOption} value={yearOption}>
                  {yearOption}
                </option>
              ))}
            </select>
          </label>
          <label className={selectFieldClassName(Boolean(birthDateParts.month))}>
            <span>월</span>
            <select
              required
              className="fortune-birth-select"
              value={birthDateParts.month}
              onChange={(event) => updateBirthDatePart('month', event.target.value)}
            >
              <option value=""></option>
              {BIRTH_MONTH_OPTIONS.map((monthOption) => (
                <option key={monthOption} value={monthOption}>
                  {Number(monthOption)}
                </option>
              ))}
            </select>
          </label>
          <label className={selectFieldClassName(Boolean(birthDateParts.day))}>
            <span>일</span>
            <select
              required
              className="fortune-birth-select"
              value={birthDateParts.day}
              onChange={(event) => updateBirthDatePart('day', event.target.value)}
            >
              <option value=""></option>
              {birthDayOptions.map((dayOption) => (
                <option key={dayOption} value={dayOption}>
                  {Number(dayOption)}
                </option>
              ))}
            </select>
          </label>
        </div>
      </fieldset>

      <fieldset className="fortune-birth-fieldset">
        <legend>태어난 시</legend>
        <div className="fortune-birth-select-grid fortune-birth-time-grid">
          <label className={selectFieldClassName(Boolean(birthTimeParts.hour))}>
            <span>시</span>
            <select
              required={!birthInfo.timeUnknown}
              disabled={birthInfo.timeUnknown}
              className="fortune-birth-select"
              value={birthTimeParts.hour}
              onChange={(event) => updateBirthTimePart('hour', event.target.value)}
            >
              <option value=""></option>
              {BIRTH_HOUR_OPTIONS.map((hourOption) => (
                <option key={hourOption} value={hourOption}>
                  {Number(hourOption)}
                </option>
              ))}
            </select>
          </label>
          <label className={selectFieldClassName(Boolean(birthTimeParts.minute))}>
            <span>분</span>
            <select
              required={!birthInfo.timeUnknown}
              disabled={birthInfo.timeUnknown}
              className="fortune-birth-select"
              value={birthTimeParts.minute}
              onChange={(event) => updateBirthTimePart('minute', event.target.value)}
            >
              <option value=""></option>
              {BIRTH_MINUTE_OPTIONS.map((minuteOption) => (
                <option key={minuteOption} value={minuteOption}>
                  {minuteOption}
                </option>
              ))}
            </select>
          </label>
        </div>

        <label className="fortune-birth-unknown-toggle">
          <input
            type="checkbox"
            checked={birthInfo.timeUnknown}
            onChange={updateTimeUnknown}
          />
          <span className="fortune-birth-checkbox" aria-hidden />
          <span className="fortune-birth-unknown-text">시간 모름</span>
        </label>
        <p className="fortune-birth-helper">태어난 시간을 모르면 체크해도 괜찮아요.</p>
      </fieldset>

      <div className="fortune-birth-actions">
        <button
          type="submit"
          disabled={!isComplete || isSubmitting}
          className="fortune-birth-submit"
        >
          <span className="fortune-birth-submit-copy">
            {isSubmitting ? '정보 저장 중' : '오늘의 운세 인쇄하기'}
          </span>
          <span className="fortune-birth-submit-orb" aria-hidden />
        </button>
      </div>
    </form>
  )
}

function optionButtonClassName(isActive: boolean) {
  return cn(
    'fortune-birth-option-button',
    isActive
      ? 'is-active'
      : 'is-idle',
  )
}

function selectFieldClassName(hasValue: boolean) {
  return cn('fortune-birth-select-field', hasValue && 'has-value')
}

function splitBirthDate(birthDate: string): BirthDateParts {
  const [year = '', month = '', day = ''] = birthDate.split('-')

  return { year, month, day }
}

function splitBirthTime(birthTime: string): BirthTimeParts {
  const [hour = '', minute = ''] = birthTime.split(':')

  return { hour, minute }
}

function normalizeBirthDateParts(birthDateParts: BirthDateParts): BirthDateParts {
  if (!birthDateParts.year || !birthDateParts.month || !birthDateParts.day) {
    return birthDateParts
  }

  const maxDay = getDaysInMonth(Number(birthDateParts.year), Number(birthDateParts.month))

  if (Number(birthDateParts.day) <= maxDay) {
    return birthDateParts
  }

  return {
    ...birthDateParts,
    day: '',
  }
}

function formatBirthDate(birthDateParts: BirthDateParts) {
  if (!birthDateParts.year || !birthDateParts.month || !birthDateParts.day) {
    return ''
  }

  return `${birthDateParts.year}-${birthDateParts.month}-${birthDateParts.day}`
}

function formatBirthTime(birthTimeParts: BirthTimeParts) {
  if (!birthTimeParts.hour || !birthTimeParts.minute) {
    return ''
  }

  return `${birthTimeParts.hour}:${birthTimeParts.minute}`
}

function createBirthDayOptions(year: string, month: string) {
  const dayCount = year && month ? getDaysInMonth(Number(year), Number(month)) : 31

  return Array.from({ length: dayCount }, (_, dayIndex) => padDatePart(dayIndex + 1))
}

function getDaysInMonth(year: number, month: number) {
  return new Date(year, month, 0).getDate()
}

function padDatePart(value: number) {
  return String(value).padStart(2, '0')
}
