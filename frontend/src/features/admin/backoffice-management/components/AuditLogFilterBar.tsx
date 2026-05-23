'use client'

import { useMemo } from 'react'
import { RotateCw, Search } from 'lucide-react'

import { AUDIT_CATEGORIES, AUDIT_EVENT_DESCRIPTORS, AUDIT_RANGE_OPTIONS, type AuditLogRange } from '..'
import type { AuditLogFilterState } from '../hooks'

interface AuditLogFilterBarProps {
  filter: AuditLogFilterState
  isLoading: boolean
  onChange: (patch: Partial<AuditLogFilterState>) => void
  onReset: () => void
  onRefresh: () => void
}

export function AuditLogFilterBar({
  filter,
  isLoading,
  onChange,
  onReset,
  onRefresh,
}: AuditLogFilterBarProps) {
  // 소분류 옵션 — 대분류가 선택돼 있으면 그 카테고리의 이벤트만 노출.
  const eventOptions = useMemo(() => {
    if (!filter.category) return AUDIT_EVENT_DESCRIPTORS
    return AUDIT_EVENT_DESCRIPTORS.filter(
      (descriptor) => descriptor.category === filter.category,
    )
  }, [filter.category])

  // 대분류를 바꿨을 때 기존 소분류가 새 대분류에 속하지 않으면 함께 초기화.
  const handleCategoryChange = (nextCategory: string) => {
    if (!filter.eventName || !nextCategory) {
      onChange({ category: nextCategory })
      return
    }
    const currentDescriptor = AUDIT_EVENT_DESCRIPTORS.find(
      (descriptor) => descriptor.value === filter.eventName,
    )
    if (currentDescriptor && currentDescriptor.category !== nextCategory) {
      onChange({ category: nextCategory, eventName: '' })
    } else {
      onChange({ category: nextCategory })
    }
  }

  return (
    <div className="flex flex-wrap items-end gap-3 rounded-[var(--radius-md)] border border-border-default bg-surface-default p-4">
      <FilterField label="기간">
        <select
          value={filter.range}
          onChange={(event) =>
            onChange({ range: event.target.value as AuditLogRange })
          }
          className="body-r min-w-[8rem] rounded-[var(--radius-md)] border border-border-default bg-surface-default px-3 py-2 text-fg-primary focus:outline-none"
        >
          {AUDIT_RANGE_OPTIONS.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
      </FilterField>

      <FilterField label="분류">
        <select
          value={filter.category}
          onChange={(event) => handleCategoryChange(event.target.value)}
          className="body-r min-w-[10rem] rounded-[var(--radius-md)] border border-border-default bg-surface-default px-3 py-2 text-fg-primary focus:outline-none"
        >
          <option value="">전체</option>
          {AUDIT_CATEGORIES.map((category) => (
            <option key={category} value={category}>
              {category}
            </option>
          ))}
        </select>
      </FilterField>

      <FilterField label="이벤트">
        <select
          value={filter.eventName}
          onChange={(event) => onChange({ eventName: event.target.value })}
          className="body-r min-w-[14rem] rounded-[var(--radius-md)] border border-border-default bg-surface-default px-3 py-2 text-fg-primary focus:outline-none"
        >
          <option value="">전체</option>
          {eventOptions.map((descriptor) => (
            <option key={descriptor.value} value={descriptor.value}>
              {descriptor.label}
            </option>
          ))}
        </select>
      </FilterField>

      <FilterField label="관리자 ID">
        <div className="flex items-center gap-2 rounded-[var(--radius-md)] border border-border-default bg-surface-default px-3 py-2">
          <Search className="h-4 w-4 text-fg-secondary" />
          <input
            type="text"
            value={filter.actorId}
            onChange={(event) => onChange({ actorId: event.target.value })}
            placeholder="actor_id 정확 일치"
            className="body-r w-44 bg-transparent text-fg-primary placeholder:text-fg-disabled focus:outline-none"
          />
        </div>
      </FilterField>

      <div className="ml-auto flex items-center gap-2">
        <button
          type="button"
          onClick={onReset}
          disabled={isLoading}
          className="caption-b rounded-[var(--radius-md)] border border-border-default bg-surface-default px-3 py-2 text-fg-primary transition-colors hover:bg-surface-subtle disabled:opacity-50"
        >
          필터 초기화
        </button>
        <button
          type="button"
          onClick={onRefresh}
          disabled={isLoading}
          className="caption-b inline-flex items-center gap-1.5 rounded-[var(--radius-md)] bg-primary-1 px-3 py-2 text-fg-inverse transition-opacity hover:opacity-90 disabled:opacity-50"
        >
          <RotateCw className="h-3.5 w-3.5" />
          새로고침
        </button>
      </div>
    </div>
  )
}

function FilterField({
  label,
  children,
}: {
  label: string
  children: React.ReactNode
}) {
  return (
    <div className="flex flex-col gap-1">
      <span className="caption-b text-fg-secondary">{label}</span>
      {children}
    </div>
  )
}
