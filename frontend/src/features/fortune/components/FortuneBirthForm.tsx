import { ArrowRight, ChevronLeft } from 'lucide-react'
import { useState } from 'react'
import type { ChangeEvent, FormEvent } from 'react'

import { cn } from '@/shared/libs'

import type { FortuneBirthInfo, FortuneCalendarType } from '../types'

interface FortuneBirthFormProps {
  birthInfo: FortuneBirthInfo
  isComplete: boolean
  onBack: () => void
  onChange: (birthInfo: FortuneBirthInfo) => void
  onSubmit: () => void
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

export default function FortuneBirthForm({
  birthInfo,
  isComplete,
  onBack,
  onChange,
  onSubmit,
}: FortuneBirthFormProps) {
  const [birthDateParts, setBirthDateParts] = useState<BirthDateParts>(() => splitBirthDate(birthInfo.birthDate))
  const [birthTimeParts, setBirthTimeParts] = useState<BirthTimeParts>(() => splitBirthTime(birthInfo.birthTime))
  const birthDayOptions = createBirthDayOptions(birthDateParts.year, birthDateParts.month)

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    onSubmit()
  }

  const updateCalendarType = (calendarType: FortuneCalendarType) => {
    onChange({ ...birthInfo, calendarType })
  }

  const updateBirthDatePart = (part: BirthDatePart, value: string) => {
    const nextBirthDateParts = normalizeBirthDateParts({
      ...birthDateParts,
      [part]: value,
    })

    setBirthDateParts(nextBirthDateParts)
    onChange({
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
    onChange({
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

    onChange({
      ...birthInfo,
      birthTime: isTimeUnknown ? '' : birthInfo.birthTime,
      timeUnknown: isTimeUnknown,
    })
  }

  return (
    <form className="fortune-paper-panel fortune-birth-form" onSubmit={handleSubmit}>
      <div className="fortune-birth-topbar">
        <button
          type="button"
          className="fortune-birth-back-button"
          onClick={onBack}
        >
          <ChevronLeft className="size-5" aria-hidden />
          뒤로
        </button>
      </div>

      <div className="fortune-birth-heading">
        <p className="caption-b">포포의 질문</p>
        <h1>운세 메모에 필요한 정보를 알려줘</h1>
        <p>입력한 정보는 오늘의 운세 메모를 만드는 데만 사용돼요.</p>
      </div>

      <fieldset className="fortune-birth-fieldset">
        <legend>음/양력</legend>
        <div className="fortune-birth-segmented" role="group" aria-label="양력 음력 선택">
          <button
            type="button"
            className={optionButtonClassName(birthInfo.calendarType === 'solar')}
            aria-pressed={birthInfo.calendarType === 'solar'}
            onClick={() => updateCalendarType('solar')}
          >
            양력
          </button>
          <button
            type="button"
            className={optionButtonClassName(birthInfo.calendarType === 'lunar')}
            aria-pressed={birthInfo.calendarType === 'lunar'}
            onClick={() => updateCalendarType('lunar')}
          >
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
              <option value="">년</option>
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
              <option value="">월</option>
              {BIRTH_MONTH_OPTIONS.map((monthOption) => (
                <option key={monthOption} value={monthOption}>
                  {Number(monthOption)}월
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
              <option value="">일</option>
              {birthDayOptions.map((dayOption) => (
                <option key={dayOption} value={dayOption}>
                  {Number(dayOption)}일
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
              <option value="">시</option>
              {BIRTH_HOUR_OPTIONS.map((hourOption) => (
                <option key={hourOption} value={hourOption}>
                  {Number(hourOption)}시
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
              <option value="">분</option>
              {BIRTH_MINUTE_OPTIONS.map((minuteOption) => (
                <option key={minuteOption} value={minuteOption}>
                  {minuteOption}분
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
          <span aria-hidden />
          모름
        </label>
      </fieldset>

      <div className="fortune-birth-actions">
        <button
          type="submit"
          disabled={!isComplete}
          className="fortune-birth-submit fortune-primary-button"
        >
          운세 메모 뽑기 준비
          <span>
            <ArrowRight className="size-6" aria-hidden />
          </span>
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
