export interface FortuneAvailabilityResponse {
  available: boolean
  fortuneDate: string
  todayFortuneId: string | null
  createdAt: string | null
  nextAvailableAt: string | null
}

export interface FortuneCreateRequest {
  calendarType: 'solar' | 'lunar'
  yearPillar: string
  monthPillar: string
  dayPillar: string
  hourPillar: string
  dayMasterElement: string
  dayBranchElement: string
  dayMasterYinYang: string
  dayBranchYinYang: string
}

export interface FortuneCreateFortuneSection {
  title: string
  summary: string
  overallLuck: number
  loveLuck: number
  workLuck: number
  moneyLuck: number
  luckyColor: string
  luckyKeyword: string
  luckyDirection: string
  caution: string | null
  postitLine: string
}

export interface FortuneCreateSajuSection {
  calendarType: string
  yearPillar: string
  monthPillar: string
  dayPillar: string
  hourPillar: string
  dayMasterElement: string
  dayBranchElement: string
  dayMasterYinYang: string
  dayBranchYinYang: string
}

export interface FortuneCreateDesignSection {
  cardTheme: string | null
  bgColor: string | null
  accentColor: string | null
  iconKey: string | null
}

export interface FortuneCreateResponse {
  fortuneId: string
  date: string
  fortune: FortuneCreateFortuneSection
  saju: FortuneCreateSajuSection
  design: FortuneCreateDesignSection
}
