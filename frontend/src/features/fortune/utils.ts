import {
  calculateFourPillars,
  lunarToSolar,
  solarToLunar,
  type FourPillarsDetail,
  type Pillar,
} from 'manseryeok'

import {
  FORTUNE_KEYWORDS,
  FORTUNE_LUCKY_COLORS,
  FORTUNE_POSTIT_LINES,
  FORTUNE_STORAGE_KEY,
  FORTUNE_TITLES,
} from './constants'
import type {
  FortuneBirthInfo,
  FortuneGenerationPayload,
  FortuneResult,
  FortuneSaju,
  FortuneSajuPillar,
  FortuneSajuYinYang,
  StoredFortune,
} from './types'

const FORTUNE_TIMEZONE = 'Asia/Seoul'
const NOON_FALLBACK_TIME = '12:00'
const MANSERYEOK_VERSION = '1.0.1'

export function getKoreanDateKey(date = new Date()) {
  const dateParts = new Intl.DateTimeFormat('en-CA', {
    timeZone: FORTUNE_TIMEZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(date)

  const year = dateParts.find((part) => part.type === 'year')?.value
  const month = dateParts.find((part) => part.type === 'month')?.value
  const day = dateParts.find((part) => part.type === 'day')?.value

  return `${year}-${month}-${day}`
}

export function getNextKoreanMidnightLabel() {
  return '자정 이후 다시 뽑을 수 있어요'
}

export function isBirthInfoComplete(birthInfo: FortuneBirthInfo) {
  return Boolean(birthInfo.birthDate && (birthInfo.timeUnknown || birthInfo.birthTime))
}

export function readStoredFortune() {
  if (typeof window === 'undefined') {
    return null
  }

  const rawStoredFortune = window.localStorage.getItem(FORTUNE_STORAGE_KEY)

  if (!rawStoredFortune) {
    return null
  }

  try {
    const storedFortune = JSON.parse(rawStoredFortune) as StoredFortune

    if (!storedFortune.result?.saju) {
      window.localStorage.removeItem(FORTUNE_STORAGE_KEY)
      return null
    }

    return storedFortune
  } catch {
    window.localStorage.removeItem(FORTUNE_STORAGE_KEY)
    return null
  }
}

export function writeStoredFortune(storedFortune: StoredFortune) {
  if (typeof window === 'undefined') {
    return
  }

  window.localStorage.setItem(FORTUNE_STORAGE_KEY, JSON.stringify(storedFortune))
}

export function clearStoredFortune() {
  if (typeof window === 'undefined') {
    return
  }

  window.localStorage.removeItem(FORTUNE_STORAGE_KEY)
}

export function createFortuneGenerationPayload(birthInfo: FortuneBirthInfo): FortuneGenerationPayload {
  const saju = calculateFortuneSaju(birthInfo)

  return {
    input: saju.input,
    saju,
  }
}

export function calculateFortuneSaju(birthInfo: FortuneBirthInfo): FortuneSaju {
  const birthDate = parseBirthDate(birthInfo.birthDate)
  const birthTime = parseBirthTime(birthInfo)
  const solarDate = getSolarDate(birthInfo, birthDate)
  const lunarDate = getLunarDate(birthInfo, birthDate)
  const fourPillars = calculateFourPillars({
    year: birthDate.year,
    month: birthDate.month,
    day: birthDate.day,
    hour: birthTime.hour,
    minute: birthTime.minute,
    isLunar: birthInfo.calendarType === 'lunar',
    isLeapMonth: false,
  })

  const pillarObject = fourPillars.toObject()
  const yearPillar = createSajuPillar(pillarObject.year, fourPillars.year, fourPillars.yearElement, fourPillars.yearYinYang)
  const monthPillar = createSajuPillar(
    pillarObject.month,
    fourPillars.month,
    fourPillars.monthElement,
    fourPillars.monthYinYang,
  )
  const dayPillar = createSajuPillar(pillarObject.day, fourPillars.day, fourPillars.dayElement, fourPillars.dayYinYang)
  const hourPillar = createSajuPillar(pillarObject.hour, fourPillars.hour, fourPillars.hourElement, fourPillars.hourYinYang)

  return {
    input: {
      birthDate: birthInfo.birthDate,
      birthTime: birthTime.normalized,
      calendarType: birthInfo.calendarType,
      timeUnknown: birthInfo.timeUnknown,
      timePolicy: birthTime.policy,
      timezone: FORTUNE_TIMEZONE,
      solarDate: formatDateParts(solarDate),
      lunarDate: `${lunarDate.isLeapMonth ? 'leap-' : ''}${formatDateParts(lunarDate)}`,
    },
    sajuYear: yearPillar.ganZhi,
    sajuMonth: monthPillar.ganZhi,
    sajuDay: dayPillar.ganZhi,
    sajuHour: hourPillar.ganZhi,
    dayElemental: dayPillar.stemElemental,
    dayBranchElemental: dayPillar.branchElemental,
    dayYinYang: dayPillar.stemYinYang,
    dayBranchYinYang: dayPillar.branchYinYang,
    pillars: {
      year: yearPillar,
      month: monthPillar,
      day: dayPillar,
      hour: hourPillar,
    },
    baZiWuXing: [
      `${yearPillar.stemElemental}${yearPillar.branchElemental}`,
      `${monthPillar.stemElemental}${monthPillar.branchElemental}`,
      `${dayPillar.stemElemental}${dayPillar.branchElemental}`,
      `${hourPillar.stemElemental}${hourPillar.branchElemental}`,
    ],
    library: {
      name: 'manseryeok',
      version: MANSERYEOK_VERSION,
    },
  }
}

export function createMockFortuneResult(birthInfo: FortuneBirthInfo, issuedDateKey = getKoreanDateKey()) {
  const saju = calculateFortuneSaju(birthInfo)
  const seed = createHash(
    `${birthInfo.birthDate}-${birthInfo.birthTime}-${birthInfo.calendarType}-${saju.sajuYear}-${saju.sajuMonth}-${saju.sajuDay}-${saju.sajuHour}-${issuedDateKey}`,
  )
  const title = pickBySeed(FORTUNE_TITLES, seed)
  const postitLine = pickBySeed(FORTUNE_POSTIT_LINES, seed + 3)
  const luckyKeyword = pickBySeed(FORTUNE_KEYWORDS, seed + 7)
  const luckyColor = pickBySeed(FORTUNE_LUCKY_COLORS, seed + 11)

  return {
    id: createFortuneResultId(issuedDateKey, seed),
    issuedDateKey,
    title,
    postitLine,
    summary: createSummary(luckyKeyword),
    scores: {
      overall: createScore(seed, 0),
      love: createScore(seed, 1),
      work: createScore(seed, 2),
      money: createScore(seed, 3),
    },
    luckyColor,
    luckyKeyword,
    caution: '오늘은 서두르기보다 한 번 더 확인하고 선택하는 게 좋아요.',
    cardTheme: seed % 2 === 0 ? 'moon-paper' : 'soft-star',
    saju,
    sajuSummary: createSajuSummary(birthInfo, saju),
  } satisfies FortuneResult
}

function createFortuneResultId(issuedDateKey: string, seed: number) {
  const browserGeneratedId = globalThis.crypto?.randomUUID()

  return `fortune-${issuedDateKey}-${browserGeneratedId ?? `${seed}-${Date.now()}`}`
}

function createSummary(luckyKeyword: string) {
  return `${luckyKeyword}의 기운이 또렷한 하루예요. 해야 할 일을 작게 나누면 포포가 적어 준 메모처럼 길이 선명해집니다.`
}

function createSajuSummary(birthInfo: FortuneBirthInfo, saju: FortuneSaju) {
  const calendarLabel = birthInfo.calendarType === 'solar' ? '양력' : '음력'
  const timeLabel = birthInfo.timeUnknown ? '시간 모름(정오 기준)' : birthInfo.birthTime

  return `${calendarLabel} ${birthInfo.birthDate} ${timeLabel} · ${saju.sajuYear} ${saju.sajuMonth} ${saju.sajuDay} ${saju.sajuHour}`
}

function parseBirthDate(birthDate: string) {
  const dateMatch = /^(\d{4})-(\d{2})-(\d{2})$/.exec(birthDate)

  if (!dateMatch) {
    throw new Error('Invalid birth date format.')
  }

  return {
    year: Number(dateMatch[1]),
    month: Number(dateMatch[2]),
    day: Number(dateMatch[3]),
  }
}

function parseBirthTime(birthInfo: FortuneBirthInfo) {
  const sourceBirthTime = birthInfo.timeUnknown ? NOON_FALLBACK_TIME : birthInfo.birthTime
  const timeMatch = /^(\d{2}):(\d{2})$/.exec(sourceBirthTime)

  if (!timeMatch) {
    throw new Error('Invalid birth time format.')
  }

  const hour = Number(timeMatch[1])
  const minute = Number(timeMatch[2])

  if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
    throw new Error('Invalid birth time value.')
  }

  return {
    hour,
    minute,
    normalized: `${padNumber(hour)}:${padNumber(minute)}:00`,
    policy: birthInfo.timeUnknown ? 'NOON_FALLBACK' as const : 'EXACT' as const,
  }
}

function getSolarDate(birthInfo: FortuneBirthInfo, birthDate: ReturnType<typeof parseBirthDate>) {
  if (birthInfo.calendarType === 'solar') {
    validateSolarDate(birthDate)
    return birthDate
  }

  return lunarToSolar(birthDate.year, birthDate.month, birthDate.day, false)
}

function getLunarDate(birthInfo: FortuneBirthInfo, birthDate: ReturnType<typeof parseBirthDate>) {
  if (birthInfo.calendarType === 'lunar') {
    return {
      ...birthDate,
      isLeapMonth: false,
    }
  }

  return solarToLunar(birthDate.year, birthDate.month, birthDate.day)
}

function validateSolarDate(birthDate: { year: number; month: number; day: number }) {
  const utcDate = new Date(Date.UTC(birthDate.year, birthDate.month - 1, birthDate.day))
  const isSameDate =
    utcDate.getUTCFullYear() === birthDate.year &&
    utcDate.getUTCMonth() === birthDate.month - 1 &&
    utcDate.getUTCDate() === birthDate.day

  if (!isSameDate) {
    throw new Error('Invalid solar birth date.')
  }
}

function createSajuPillar(
  ganZhi: string,
  pillar: Pillar,
  element: FourPillarsDetail['yearElement'],
  yinYang: FourPillarsDetail['yearYinYang'],
): FortuneSajuPillar {
  return {
    ganZhi,
    heavenlyStem: pillar.heavenlyStem,
    earthlyBranch: pillar.earthlyBranch,
    stemElemental: element.stem,
    branchElemental: element.branch,
    stemYinYang: normalizeYinYang(yinYang.stem),
    branchYinYang: normalizeYinYang(yinYang.branch),
  }
}

function normalizeYinYang(value: string): FortuneSajuYinYang {
  if (value === '양' || value === '음') {
    return value
  }

  throw new Error(`Unsupported yin-yang value: ${value}`)
}

function formatDateParts(dateParts: { year: number; month: number; day: number }) {
  return `${dateParts.year}-${padNumber(dateParts.month)}-${padNumber(dateParts.day)}`
}

function padNumber(value: number) {
  return String(value).padStart(2, '0')
}

function createScore(seed: number, offset: number) {
  return 58 + ((seed + offset * 17) % 35)
}

function createHash(value: string) {
  let hash = 0

  for (let index = 0; index < value.length; index += 1) {
    hash = (hash * 31 + value.charCodeAt(index)) % 9973
  }

  return hash
}

function pickBySeed<T>(items: readonly T[], seed: number) {
  return items[seed % items.length]
}
