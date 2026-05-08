export type FortuneStep = 'intro' | 'birthInfo' | 'draw' | 'printing' | 'result' | 'limit' | 'error'

export type FortuneCalendarType = 'solar' | 'lunar'

export interface FortuneBirthInfo {
  birthDate: string
  birthTime: string
  calendarType: FortuneCalendarType
  timeUnknown: boolean
}

export type FortuneSajuYinYang = '양' | '음'

export type FortuneBirthTimePolicy = 'EXACT' | 'NOON_FALLBACK'

export interface FortuneSajuPillar {
  ganZhi: string
  heavenlyStem: string
  earthlyBranch: string
  stemElemental: string
  branchElemental: string
  stemYinYang: FortuneSajuYinYang
  branchYinYang: FortuneSajuYinYang
}

export interface FortuneSajuInput {
  birthDate: string
  birthTime: string
  calendarType: FortuneCalendarType
  timeUnknown: boolean
  timePolicy: FortuneBirthTimePolicy
  timezone: 'Asia/Seoul'
  solarDate: string
  lunarDate: string
}

export interface FortuneSaju {
  input: FortuneSajuInput
  sajuYear: string
  sajuMonth: string
  sajuDay: string
  sajuHour: string
  dayElemental: string
  dayBranchElemental: string
  dayYinYang: FortuneSajuYinYang
  dayBranchYinYang: FortuneSajuYinYang
  pillars: {
    year: FortuneSajuPillar
    month: FortuneSajuPillar
    day: FortuneSajuPillar
    hour: FortuneSajuPillar
  }
  baZiWuXing: string[]
  library: {
    name: 'manseryeok'
    version: string
  }
}

export interface FortuneGenerationPayload {
  input: FortuneSajuInput
  saju: FortuneSaju
}

export interface FortuneScoreSet {
  overall: number
  love: number
  work: number
  money: number
}

export interface FortuneLuckyColor {
  name: string
  hex: string
}

export interface FortuneResult {
  id: string
  issuedDateKey: string
  fortuneImageUrl?: string
  title: string
  postitLine: string
  summary: string
  scores: FortuneScoreSet
  luckyColor: FortuneLuckyColor
  luckyKeyword: string
  caution: string
  cardTheme: string
  saju: FortuneSaju
  sajuSummary: string
}

export interface StoredFortune {
  dateKey: string
  birthInfo: FortuneBirthInfo
  result: FortuneResult
  issuedAt: string
}
