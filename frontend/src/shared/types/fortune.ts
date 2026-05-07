export interface FortuneAvailabilityResponse {
  date: string
  canDraw: boolean
  alreadyClaimedToday: boolean
  fortuneId: string | null
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

export interface FortuneIssuedResponse {
  fortuneId: string
  date: string
  title?: string
  summary: string
  overallLuck?: number
  loveLuck?: number
  workLuck?: number
  moneyLuck?: number
  luckyColor?: string
  luckyKeyword?: string
  caution?: string | null
  postitLine?: string
  score?: number
  sections?: Record<string, string>
  luckyNumber?: number
  fortuneImageUrl?: string
}
