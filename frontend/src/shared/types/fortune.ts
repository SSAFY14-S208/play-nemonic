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
  hourPillar?: string
  dayMasterElement: string
  dayBranchElement: string
  dayMasterYinYang: string
  dayBranchYinYang: string
}

export interface FortuneCreateResponse {
  fortuneId: string
  date: string
  title: string
  summary: string
  overallLuck: number
  loveLuck: number
  workLuck: number
  moneyLuck: number
  luckyColor: string
  luckyKeyword: string
  caution: string | null
  postitLine: string
}
