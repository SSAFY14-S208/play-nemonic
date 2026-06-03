'use client'

import { useState } from 'react'
import { ChevronDown, ChevronRight } from 'lucide-react'

import { cn } from '@/shared/libs'
import { formatKoreanDateTime } from '@/shared/utils'

import { AUDIT_EVENT_LABEL_MAP } from '..'
import { useBackofficeAuditLogs, type AuditLogEntry } from '../hooks'

import { AuditLogFilterBar } from './AuditLogFilterBar'
import { AuditLogResultBadge } from './AuditLogResultBadge'

function formatTimestamp(value: string): string {
  if (!value) return '—'
  return formatKoreanDateTime(value, {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  })
}

function formatJson(value: Record<string, unknown> | null): string {
  if (!value) return '—'
  try {
    return JSON.stringify(value, null, 2)
  } catch {
    return String(value)
  }
}

/**
 * actor_id(숫자 PK) → 관리자 식별 정보 매핑.
 * AdminAuditLogger가 metadata에 닉네임을 emit하지 않으므로, 페이지가 보유한 관리자 목록을
 * lookup 테이블로 만들어 전달한다. 매핑이 없으면 actor_id를 그대로 노출한다.
 */
export interface AuditActorInfo {
  nickname: string
  loginId: string
}

export type AuditActorLookup = Readonly<Record<string, AuditActorInfo>>

interface AuditLogSectionProps {
  actorLookup?: AuditActorLookup
}

export function AuditLogSection({ actorLookup }: AuditLogSectionProps = {}) {
  const {
    filter,
    entries,
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
  } = useBackofficeAuditLogs()

  return (
    <section className="flex flex-col gap-4">
      <header className="flex flex-col gap-1">
        <h3 className="h4-b text-fg-primary">감사 로그</h3>
        <p className="caption-r text-fg-secondary">
          OpenSearch `audit-logs` 인덱스의 백오피스 운영 이력 — 영구 보존되며 후속
          소급 검증·법적 대응 근거로 사용됩니다.
        </p>
      </header>

      <AuditLogFilterBar
        filter={filter}
        isLoading={isLoading}
        onChange={updateFilter}
        onReset={resetFilter}
        onRefresh={refresh}
      />

      <div className="overflow-hidden rounded-[var(--radius-lg)] border border-border-default bg-surface-default">
        <table className="w-full table-fixed">
          <thead>
            <tr className="border-b border-border-default bg-surface-subtle">
              <th className="caption-b w-8 px-2 py-3" />
              <th className="caption-b w-44 px-4 py-3 text-left text-fg-secondary">
                시각
              </th>
              <th className="caption-b w-48 px-4 py-3 text-left text-fg-secondary">
                이벤트
              </th>
              <th className="caption-b w-48 px-4 py-3 text-left text-fg-secondary">
                관리자
              </th>
              <th className="caption-b px-4 py-3 text-left text-fg-secondary">
                대상
              </th>
              <th className="caption-b w-24 px-4 py-3 text-left text-fg-secondary">
                결과
              </th>
              <th className="caption-b w-32 px-4 py-3 text-left text-fg-secondary">
                IP
              </th>
            </tr>
          </thead>
          <tbody>
            {isLoading ? (
              <tr>
                <td
                  colSpan={7}
                  className="body-r px-4 py-8 text-center text-fg-secondary"
                >
                  불러오는 중…
                </td>
              </tr>
            ) : loadError ? (
              <tr>
                <td
                  colSpan={7}
                  className="body-r px-4 py-8 text-center text-red-500"
                  role="alert"
                >
                  {loadError}
                </td>
              </tr>
            ) : entries.length === 0 ? (
              <tr>
                <td
                  colSpan={7}
                  className="body-r px-4 py-8 text-center text-fg-secondary"
                >
                  조건에 맞는 감사 로그가 없습니다.
                </td>
              </tr>
            ) : (
              entries.map((entry) => (
                <AuditLogRow
                  key={entry.hitId}
                  entry={entry}
                  actorLookup={actorLookup}
                />
              ))
            )}
          </tbody>
        </table>
      </div>

      <div className="flex items-center justify-between">
        <span className="caption-r text-fg-secondary">
          {isLoading
            ? '집계 중…'
            : `총 ${total.toLocaleString('ko-KR')}건 · 검색 ${tookMs}ms · 표시 ${entries.length}건`}
        </span>
        {canLoadMore && (
          <button
            type="button"
            onClick={loadMore}
            disabled={isLoading || isLoadingMore}
            className="caption-b rounded-[var(--radius-md)] border border-border-default bg-surface-default px-4 py-2 text-fg-primary transition-colors hover:bg-surface-subtle disabled:opacity-50"
          >
            {isLoadingMore ? '불러오는 중…' : '더 보기'}
          </button>
        )}
      </div>
    </section>
  )
}

interface AuditLogRowProps {
  entry: AuditLogEntry
  actorLookup?: AuditActorLookup
}

function AuditLogRow({ entry, actorLookup }: AuditLogRowProps) {
  const [isExpanded, setIsExpanded] = useState(false)
  const eventLabel = AUDIT_EVENT_LABEL_MAP[entry.eventName] ?? entry.eventName
  const actorInfo =
    entry.actorId && actorLookup ? actorLookup[entry.actorId] : undefined
  const hasDetail =
    entry.reason !== null ||
    entry.before !== null ||
    entry.after !== null ||
    entry.message !== null

  return (
    <>
      <tr
        className={cn(
          'border-b border-border-default last:border-b-0',
          hasDetail && 'cursor-pointer hover:bg-surface-subtle',
        )}
        onClick={() => {
          if (hasDetail) setIsExpanded((prev) => !prev)
        }}
      >
        <td className="px-2 py-3 align-top">
          {hasDetail ? (
            isExpanded ? (
              <ChevronDown className="h-4 w-4 text-fg-secondary" />
            ) : (
              <ChevronRight className="h-4 w-4 text-fg-secondary" />
            )
          ) : null}
        </td>
        <td className="body-r px-4 py-3 align-top text-fg-primary tabular-nums">
          {formatTimestamp(entry.timestamp)}
        </td>
        <td className="body-m px-4 py-3 align-top text-fg-primary">
          <div className="flex min-w-0 flex-col gap-0.5">
            <span className="truncate" title={eventLabel}>
              {eventLabel}
            </span>
            <span
              className="caption-r truncate font-mono text-fg-disabled"
              title={entry.eventName || ''}
            >
              {entry.eventName || '—'}
            </span>
          </div>
        </td>
        <td className="body-r px-4 py-3 align-top text-fg-primary">
          <div className="flex min-w-0 flex-col gap-0.5">
            <span
              className="body-m truncate"
              title={actorInfo?.nickname ?? entry.actorId ?? ''}
            >
              {actorInfo?.nickname ?? entry.actorId ?? '—'}
            </span>
            <span className="caption-r truncate text-fg-secondary">
              {[actorInfo?.loginId, entry.actorRole].filter(Boolean).join(' · ') ||
                (entry.actorId ? `#${entry.actorId}` : '')}
            </span>
          </div>
        </td>
        <td className="body-r px-4 py-3 align-top text-fg-primary">
          <div className="flex min-w-0 flex-col gap-0.5">
            <span className="truncate">
              {entry.targetType ?? '—'}
              {entry.action ? (
                <span className="caption-r text-fg-secondary"> · {entry.action}</span>
              ) : null}
            </span>
            {entry.targetId && (
              <span
                className="caption-r truncate font-mono text-fg-disabled"
                title={entry.targetId}
              >
                {entry.targetId}
              </span>
            )}
          </div>
        </td>
        <td className="px-4 py-3 align-top">
          <AuditLogResultBadge result={entry.result} />
        </td>
        <td className="body-r px-4 py-3 align-top font-mono text-fg-secondary">
          {entry.actorIp ?? '—'}
        </td>
      </tr>
      {isExpanded && hasDetail && (
        <tr className="border-b border-border-default bg-surface-subtle last:border-b-0">
          <td colSpan={7} className="px-4 py-3">
            <AuditLogDetail entry={entry} />
          </td>
        </tr>
      )}
    </>
  )
}

function AuditLogDetail({ entry }: { entry: AuditLogEntry }) {
  return (
    <div className="flex flex-col gap-3">
      {entry.message && (
        <DetailRow label="메시지" value={entry.message} />
      )}
      {entry.reason && <DetailRow label="사유" value={entry.reason} />}
      {entry.traceId && (
        <DetailRow label="trace_id" value={entry.traceId} monospace />
      )}
      {(entry.before || entry.after) && (
        <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
          <DetailBlock label="before" value={formatJson(entry.before)} />
          <DetailBlock label="after" value={formatJson(entry.after)} />
        </div>
      )}
    </div>
  )
}

interface DetailRowProps {
  label: string
  value: string
  monospace?: boolean
}

function DetailRow({ label, value, monospace }: DetailRowProps) {
  return (
    <div className="flex flex-col gap-0.5">
      <span className="caption-b text-fg-secondary">{label}</span>
      <span
        className={cn(
          'body-r whitespace-pre-wrap text-fg-primary',
          monospace && 'font-mono',
        )}
      >
        {value}
      </span>
    </div>
  )
}

function DetailBlock({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex flex-col gap-1">
      <span className="caption-b text-fg-secondary">{label}</span>
      <pre className="caption-r overflow-x-auto rounded-[var(--radius-sm)] border border-border-default bg-surface-default p-3 font-mono text-fg-primary">
        {value}
      </pre>
    </div>
  )
}
