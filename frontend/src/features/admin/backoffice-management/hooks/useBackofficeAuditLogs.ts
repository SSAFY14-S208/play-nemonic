'use client'

import { useCallback, useEffect, useMemo, useState } from 'react'

import { ApiError, postAdminLogsSearch } from '@/shared/apis'
import type {
  AdminLogsFilter,
  AdminLogsSearchHit,
} from '@/shared/types'

import {
  AUDIT_EVENT_DESCRIPTORS,
  AUDIT_RANGE_DAYS,
  type AuditLogRange,
} from '../constants'

const PAGE_SIZE = 50
const AUDIT_INDEX = 'audit-logs' as const
const SERVICE_FIELD = 'service'
const SERVICE_VALUE = 'backoffice-api'

// 감사 로그 entry — OpenSearch hit.source의 평탄화된 도메인 표현.
// 백엔드는 snake_case로 emit하지만 React 컴포넌트에서는 camelCase로 다루기 편하도록 변환한다.
export interface AuditLogEntry {
  hitId: string
  timestamp: string
  level: string
  eventName: string
  message: string | null
  traceId: string | null
  actorId: string | null
  actorRole: string | null
  actorIp: string | null
  targetType: string | null
  targetId: string | null
  action: string | null
  reason: string | null
  result: string | null
  before: Record<string, unknown> | null
  after: Record<string, unknown> | null
  raw: Record<string, unknown>
}

export interface AuditLogFilterState {
  range: AuditLogRange
  /** 대분류 카테고리. 빈 문자열이면 전체. 서버로 보내지 않고 소분류 옵션을 좁히는 데만 사용. */
  category: string
  /** 소분류 — 실제 OpenSearch `event_name` 정확 매칭. */
  eventName: string
  actorId: string
}

export const DEFAULT_AUDIT_FILTER: AuditLogFilterState = {
  range: '30d',
  category: '',
  eventName: '',
  actorId: '',
}

function pickString(
  source: Record<string, unknown>,
  ...keys: string[]
): string | null {
  for (const key of keys) {
    const value = source[key]
    if (typeof value === 'string' && value.length > 0) return value
  }
  return null
}

function pickObject(
  source: Record<string, unknown>,
  key: string,
): Record<string, unknown> | null {
  const value = source[key]
  if (value && typeof value === 'object' && !Array.isArray(value)) {
    return value as Record<string, unknown>
  }
  return null
}

function toEntry(hit: AdminLogsSearchHit): AuditLogEntry {
  const source = hit.source
  const metadata = pickObject(source, 'metadata') ?? {}
  return {
    hitId: hit.id,
    timestamp: pickString(source, '@timestamp', 'timestamp') ?? '',
    level: pickString(source, 'level') ?? 'INFO',
    eventName: pickString(source, 'event_name', 'eventName') ?? '',
    message: pickString(source, 'message'),
    traceId: pickString(source, 'trace_id', 'traceId'),
    actorId: pickString(metadata, 'actor_id', 'actorId'),
    actorRole: pickString(metadata, 'actor_role', 'actorRole'),
    actorIp: pickString(metadata, 'actor_ip', 'actorIp'),
    targetType: pickString(metadata, 'target_type', 'targetType'),
    targetId: pickString(metadata, 'target_id', 'targetId'),
    action: pickString(metadata, 'action'),
    reason: pickString(metadata, 'reason'),
    result: pickString(metadata, 'result'),
    before: pickObject(metadata, 'before'),
    after: pickObject(metadata, 'after'),
    raw: source,
  }
}

function buildTimeRange(range: AuditLogRange): { from: string; to: string } {
  const now = new Date()
  const past = new Date(now)
  past.setDate(now.getDate() - AUDIT_RANGE_DAYS[range])
  return { from: past.toISOString(), to: now.toISOString() }
}

function buildFilters(filter: AuditLogFilterState): AdminLogsFilter[] {
  const filters: AdminLogsFilter[] = [
    { field: SERVICE_FIELD, value: SERVICE_VALUE },
  ]
  if (filter.eventName) {
    filters.push({ field: 'event_name', value: filter.eventName })
  }
  const trimmedActorId = filter.actorId.trim()
  if (trimmedActorId) {
    filters.push({ field: 'metadata.actor_id', value: trimmedActorId })
  }
  return filters
}

export function useBackofficeAuditLogs() {
  const [filter, setFilter] = useState<AuditLogFilterState>(DEFAULT_AUDIT_FILTER)
  const [entries, setEntries] = useState<AuditLogEntry[]>([])
  const [total, setTotal] = useState(0)
  const [tookMs, setTookMs] = useState(0)
  const [nextSearchAfter, setNextSearchAfter] = useState<
    [string, string] | null
  >(null)
  const [isLoading, setIsLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [isLoadingMore, setIsLoadingMore] = useState(false)
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      if (!cancelled) setIsLoading(true)
      try {
        const response = await postAdminLogsSearch({
          index: AUDIT_INDEX,
          filters: buildFilters(filter),
          timeRange: buildTimeRange(filter.range),
          size: PAGE_SIZE,
        })
        if (cancelled) return
        setEntries(response.hits.map(toEntry))
        setTotal(response.total)
        setTookMs(response.tookMs)
        setNextSearchAfter(response.nextSearchAfter)
        setLoadError(null)
      } catch (caughtError) {
        if (cancelled) return
        const message =
          caughtError instanceof ApiError
            ? caughtError.message
            : caughtError instanceof Error
              ? caughtError.message
              : '감사 로그를 불러오지 못했어요'
        setLoadError(message)
        setEntries([])
        setTotal(0)
        setNextSearchAfter(null)
      } finally {
        if (!cancelled) setIsLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [filter, reloadKey])

  const loadMore = useCallback(async () => {
    if (!nextSearchAfter || isLoadingMore) return
    setIsLoadingMore(true)
    try {
      const response = await postAdminLogsSearch({
        index: AUDIT_INDEX,
        filters: buildFilters(filter),
        timeRange: buildTimeRange(filter.range),
        size: PAGE_SIZE,
        searchAfter: nextSearchAfter,
      })
      setEntries((prev) => [...prev, ...response.hits.map(toEntry)])
      setNextSearchAfter(response.nextSearchAfter)
      setTookMs(response.tookMs)
    } catch (caughtError) {
      const message =
        caughtError instanceof ApiError
          ? caughtError.message
          : caughtError instanceof Error
            ? caughtError.message
            : '추가 데이터를 불러오지 못했어요'
      setLoadError(message)
    } finally {
      setIsLoadingMore(false)
    }
  }, [filter, isLoadingMore, nextSearchAfter])

  const refresh = useCallback(() => {
    setReloadKey((prev) => prev + 1)
  }, [])

  const updateFilter = useCallback(
    (patch: Partial<AuditLogFilterState>) => {
      setFilter((prev) => ({ ...prev, ...patch }))
    },
    [],
  )

  const resetFilter = useCallback(() => {
    setFilter(DEFAULT_AUDIT_FILTER)
  }, [])

  const canLoadMore = useMemo(() => nextSearchAfter !== null, [nextSearchAfter])

  // 카테고리(분류)는 서버 쿼리로 보낼 수 없다 — 현 AdminLogsFilter가 단일 값 정확 매칭만 지원해
  // OR/IN 쿼리로 카테고리에 속한 이벤트 집합을 한 번에 보낼 수 없기 때문이다.
  // 그래서 서버는 service 필터까지만 좁히고, 카테고리는 응답을 받은 뒤 화이트리스트 기반으로
  // 클라이언트에서 추가 필터링한다. 페이지네이션은 서버 cursor 기준 그대로 유지된다.
  const eventsInCategory = useMemo(() => {
    if (!filter.category) return null
    return new Set(
      AUDIT_EVENT_DESCRIPTORS.filter(
        (descriptor) => descriptor.category === filter.category,
      ).map((descriptor) => descriptor.value),
    )
  }, [filter.category])

  const displayedEntries = useMemo(() => {
    if (!eventsInCategory) return entries
    return entries.filter((entry) => eventsInCategory.has(entry.eventName))
  }, [entries, eventsInCategory])

  return {
    filter,
    entries: displayedEntries,
    total,
    tookMs,
    isLoading,
    loadError,
    canLoadMore,
    isLoadingMore,
    updateFilter,
    resetFilter,
    refresh,
    loadMore,
  }
}
