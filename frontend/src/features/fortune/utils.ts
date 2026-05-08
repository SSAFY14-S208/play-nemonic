import { HTTPError } from 'ky'
import {
  calculateFourPillars,
  lunarToSolar,
  solarToLunar,
  type FourPillarsDetail,
  type Pillar,
} from 'manseryeok'

import {
  ApiError,
  getFortuneTodayAvailability,
  patchAnonymousBirthInfo,
  postAnonymousBirthInfo,
  postFortune,
} from '@/shared/apis'
import { runtime } from '@/shared/config'
import { useUserStore } from '@/shared/stores'
import type {
  AnonymousUserBirthInfoRequest,
  AnonymousUserProfileResponse,
  FortuneCreateRequest,
  FortuneCreateResponse,
} from '@/shared/types'

import {
  FORTUNE_KEYWORDS,
  FORTUNE_LUCKY_COLORS,
  FORTUNE_NOON_FALLBACK_BIRTH_TIME,
  FORTUNE_POSTIT_LINES,
  FORTUNE_STORAGE_KEY,
  FORTUNE_TITLES,
  FORTUNE_USER_NOT_READY_ERROR,
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

export function createFortuneCreateRequest(birthInfo: FortuneBirthInfo): FortuneCreateRequest {
  const saju = calculateFortuneSaju(birthInfo)

  return {
    calendarType: birthInfo.calendarType,
    yearPillar: saju.sajuYear,
    monthPillar: saju.sajuMonth,
    dayPillar: saju.sajuDay,
    ...(birthInfo.timeUnknown ? {} : { hourPillar: saju.sajuHour }),
    dayMasterElement: saju.dayElemental,
    dayBranchElement: saju.dayBranchElemental,
    dayMasterYinYang: saju.dayYinYang,
    dayBranchYinYang: saju.dayBranchYinYang,
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

export function createFortuneResultFromCreateResponse(
  createdFortune: FortuneCreateResponse,
  birthInfo: FortuneBirthInfo,
): FortuneResult {
  const saju = calculateFortuneSaju(birthInfo)
  const luckyColor = normalizeLuckyColor(createdFortune.luckyColor, createdFortune.fortuneId)

  return {
    id: createdFortune.fortuneId,
    issuedDateKey: createdFortune.date,
    title: createdFortune.title,
    postitLine: createdFortune.postitLine,
    summary: createdFortune.summary,
    scores: {
      overall: createdFortune.overallLuck,
      love: createdFortune.loveLuck,
      work: createdFortune.workLuck,
      money: createdFortune.moneyLuck,
    },
    luckyColor,
    luckyKeyword: createdFortune.luckyKeyword,
    caution: createdFortune.caution ?? '오늘은 작은 선택도 한 번 더 확인하면 좋아요.',
    cardTheme: pickCardTheme(createdFortune.fortuneId),
    saju,
    sajuSummary: createSajuSummary(birthInfo, saju),
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

function normalizeLuckyColor(luckyColorName: string | undefined, seedSource: string) {
  if (!luckyColorName) {
    return pickBySeed(FORTUNE_LUCKY_COLORS, createHash(seedSource))
  }

  const matchingColor = FORTUNE_LUCKY_COLORS.find((color) => color.name === luckyColorName)

  if (matchingColor) {
    return matchingColor
  }

  const knownColorHex = KOREAN_LUCKY_COLOR_HEX[luckyColorName]

  return {
    name: luckyColorName,
    hex: knownColorHex ?? pickBySeed(FORTUNE_LUCKY_COLORS, createHash(`${seedSource}-${luckyColorName}`)).hex,
  }
}

function pickCardTheme(seedSource: string) {
  return createHash(seedSource) % 2 === 0 ? 'moon-paper' : 'soft-star'
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

const KOREAN_LUCKY_COLOR_HEX: Record<string, string> = {
  은회색: '#c0c0c0',
  노랑: '#f4d35e',
  노란색: '#f4d35e',
  보라: '#a281d0',
  보라색: '#a281d0',
  초록: '#8ccf92',
  초록색: '#8ccf92',
  파랑: '#82b9e6',
  파란색: '#82b9e6',
  분홍: '#ef9aa7',
  분홍색: '#ef9aa7',
  흰색: '#f8f6ef',
  검정: '#2f2a33',
  검은색: '#2f2a33',
}

export function createBirthInfoRequest(birthInfo: FortuneBirthInfo): AnonymousUserBirthInfoRequest {
  return {
    birthday: birthInfo.birthDate,
    birthtime: birthInfo.timeUnknown ? FORTUNE_NOON_FALLBACK_BIRTH_TIME : `${birthInfo.birthTime}:00`,
    isLunar: birthInfo.calendarType === 'lunar',
  }
}

export function createBirthInfoFromProfile(
  profile: AnonymousUserProfileResponse,
): FortuneBirthInfo | null {
  if (!profile.birthday || !profile.birthtime || profile.isLunar === null) {
    return null
  }

  return {
    birthDate: profile.birthday,
    birthTime: profile.birthtime.slice(0, 5),
    calendarType: profile.isLunar ? 'lunar' : 'solar',
    timeUnknown: false,
  }
}

export function canUseLocalFortuneFallback(error: unknown) {
  if (!runtime.isDev) {
    return false
  }

  if (error instanceof Error && error.message === FORTUNE_USER_NOT_READY_ERROR) {
    return true
  }

  if (error instanceof ApiError) {
    return false
  }

  if (error instanceof HTTPError) {
    return error.response.status === 404 || error.response.status === 405 || error.response.status >= 500
  }

  return true
}

export function isFortuneConflictError(error: unknown) {
  if (error instanceof HTTPError) {
    return error.response.status === 409
  }

  return error instanceof ApiError && error.message.includes('이미')
}

export function resolveBirthInfoErrorMessage(error: unknown) {
  if (error instanceof ApiError || error instanceof HTTPError) {
    return '생년월일 정보를 저장하지 못했어요. 잠시 후 다시 시도해 주세요.'
  }

  return '사용자 정보를 준비하는 중이에요. 잠시 후 다시 시도해 주세요.'
}

export function resolveFortuneErrorMessage(error: unknown) {
  if (error instanceof HTTPError && error.response.status === 412) {
    return '생년월일 등록이 필요해요. 정보를 다시 확인해 주세요.'
  }

  if (error instanceof HTTPError && error.response.status === 502) {
    return '운세 생성 서비스에 일시적 장애가 발생했어요. 잠시 후 다시 시도해 주세요.'
  }

  if (error instanceof ApiError) {
    return error.message
  }

  return '운세를 가져오지 못했어요. 잠시 후 다시 시도해 주세요.'
}

export async function saveBirthInfo(birthInfo: FortuneBirthInfo, hasServerBirthInfo: boolean) {
  if (!useUserStore.getState().userUuid) {
    throw new Error(FORTUNE_USER_NOT_READY_ERROR)
  }

  const payload = createBirthInfoRequest(birthInfo)

  if (hasServerBirthInfo) {
    await patchAnonymousBirthInfo(payload)
    return
  }

  try {
    await postAnonymousBirthInfo(payload)
  } catch (error) {
    if (!isFortuneConflictError(error)) {
      throw error
    }

    await patchAnonymousBirthInfo(payload)
  }
}

export async function issueNewFortune(birthInfo: FortuneBirthInfo) {
  if (!useUserStore.getState().userUuid) {
    throw new Error(FORTUNE_USER_NOT_READY_ERROR)
  }

  const createdFortune = await postFortune(createFortuneCreateRequest(birthInfo))

  return createFortuneResultFromCreateResponse(createdFortune, birthInfo)
}

export async function getTodayFortuneResult(birthInfo: FortuneBirthInfo | null) {
  const availability = await getFortuneTodayAvailability()

  if (availability.available || !availability.todayFortuneId || !birthInfo) {
    return null
  }

  // 백엔드는 GET /fortune/{id}를 아직 제공하지 않으므로 같은 디바이스 localStorage 매칭에 의존한다.
  // 다른 기기에서 발급한 운세 본문 표시는 BE 추가 시점에 연결한다.
  const stored = readStoredFortune()
  if (stored && stored.result.id === availability.todayFortuneId) {
    return stored.result
  }

  return null
}

export async function resolveAlreadyIssuedResult(error: unknown, birthInfo: FortuneBirthInfo) {
  if (!isFortuneConflictError(error)) {
    return null
  }

  try {
    return await getTodayFortuneResult(birthInfo)
  } catch {
    return null
  }
}
