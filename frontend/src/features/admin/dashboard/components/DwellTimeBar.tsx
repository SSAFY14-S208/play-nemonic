'use client'

import {
  Bar,
  BarChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import type { Formatter } from 'recharts/types/component/DefaultTooltipContent'

import { CHART_STATUS_COLORS } from '../constants'
import type { AnalyticsDrillDownState, AnalyticsKpiState } from '../types'
import type { I9Bucket } from '../hooks/useAnalyticsCharts'

import { ChartFrame } from './ChartFrame'

type Props = {
  state: AnalyticsKpiState<I9Bucket[]>
  onRetry: () => void
  onDrillDown: (next: AnalyticsDrillDownState) => void
}

export function DwellTimeBar({ state, onRetry, onDrillDown }: Props) {
  const data = (state.data ?? []).slice(0, 15)
  const isEmpty = data.length === 0

  return (
    <ChartFrame
      isLoading={state.isLoading}
      errorMessage={state.errorMessage}
      isEmpty={isEmpty}
      onRetry={onRetry}
    >
      <ResponsiveContainer width="100%" height="100%">
        <BarChart
          data={data}
          layout="vertical"
          margin={{ top: 8, right: 24, bottom: 24, left: 8 }}
          barCategoryGap="22%"
        >
          <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border-default)" />
          <XAxis
            type="number"
            stroke="var(--color-fg-secondary)"
            tick={{ fontSize: 11 }}
            tickFormatter={(value: number) => `${value.toFixed(0)}초`}
          />
          <YAxis
            type="category"
            dataKey="path"
            width={180}
            stroke="var(--color-fg-secondary)"
            tick={{ fontSize: 11 }}
            // 데이터가 많아도 모든 path 라벨을 강제 노출 — 자동 thinning으로 막대만 보이고
            // 라벨이 잘려 같은 path가 중복된 것처럼 오해되는 문제를 막는다.
            interval={0}
          />
          <Tooltip
            formatter={
              ((value, name, item) => {
                if (name === 'avgSec') {
                  const samples =
                    (item?.payload as { samples?: number } | undefined)?.samples ?? 0
                  const avgSec = typeof value === 'number' ? value : Number(value ?? 0)
                  return [
                    `${avgSec.toFixed(1)}초 · ${samples.toLocaleString('ko-KR')} 샘플`,
                    '평균 체류',
                  ]
                }
                return [String(value ?? ''), name ?? '']
              }) satisfies Formatter
            }
          />
          <Bar
            dataKey="avgSec"
            fill={CHART_STATUS_COLORS.accent}
            radius={[0, 4, 4, 0]}
            maxBarSize={18}
            cursor="pointer"
            onClick={(entry) => {
              if (!entry || !entry.payload) return
              const path = entry.payload.path as string
              // 표시된 path는 동적 segment(`:room` 등)가 정규화된 형태라
              // exact match로는 매칭되지 않음. 첫 의미 있는 segment만 추출해
              // wildcard substring 매칭으로 변환.
              // 예: `/flipbook/result/:room` → `*result*` 등.
              const segments = path.split('/').filter((segment) => segment && !segment.startsWith(':'))
              const keyword = segments[segments.length - 1] ?? ''
              const pathQuery = keyword ? ` AND path:*${keyword}*` : ''
              onDrillDown({
                vizId: 'I9',
                chartLabel: `체류 시간: ${path}`,
                dimensionFilters: [],
                extraQuery: `event_name:page_leave${pathQuery}`,
              })
            }}
          />
        </BarChart>
      </ResponsiveContainer>
    </ChartFrame>
  )
}
