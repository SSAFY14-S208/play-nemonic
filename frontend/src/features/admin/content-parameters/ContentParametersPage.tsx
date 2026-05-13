'use client'

import type { SystemParameterValue } from '@/shared/types'

import {
  CATEGORY_BADGE,
  CONTENT_PARAMETERS,
  type ParameterMeta,
  UNIT_LABEL,
} from './constants'
import { ParameterCard } from './ParameterCard'
import { useContentParameters } from './useContentParameters'

const NUMBER_INPUT_CLASS =
  'body-r w-24 rounded-[var(--radius-md)] border border-border-default bg-surface-default px-3 py-2 text-fg-primary focus:border-primary-2 focus:outline-none disabled:opacity-50'

export default function ContentParametersPage() {
  const {
    isLoading,
    loadError,
    parametersByKey,
    draft,
    hasChanges,
    setDraftField,
    setEnumBounds,
    resetDraft,
    save,
    isSaving,
  } = useContentParameters()

  return (
    <div className="flex flex-col gap-6">
      <header className="flex items-center justify-between gap-4">
        <p className="body-r text-fg-secondary">
          서비스 운영에 필요한 주요 파라미터를 백오피스에서 동적으로 변경할 수 있습니다. 변경
          사항은 저장 버튼을 눌러야 적용되며, 변경 이력은 감사 로그에 기록됩니다.
        </p>
        <div className="flex shrink-0 items-center gap-2">
          <button
            type="button"
            onClick={resetDraft}
            disabled={!hasChanges || isSaving}
            className="rounded-[var(--radius-md)] border border-border-default bg-surface-default px-4 py-2 body-b text-fg-primary transition-colors hover:bg-surface-subtle disabled:opacity-50"
          >
            초기화
          </button>
          <button
            type="button"
            onClick={save}
            disabled={!hasChanges || isSaving}
            className="rounded-[var(--radius-md)] bg-primary-1 px-4 py-2 body-b text-fg-inverse transition-opacity disabled:opacity-50"
          >
            {isSaving ? '저장 중…' : '변경 사항 저장'}
          </button>
        </div>
      </header>

      {isLoading && (
        <p className="body-r text-fg-secondary">파라미터를 불러오는 중…</p>
      )}
      {loadError && (
        <p role="alert" className="body-r text-red-500">
          {loadError}
        </p>
      )}

      {!isLoading && !loadError && (
        <div className="flex flex-col gap-3">
          {CONTENT_PARAMETERS.map((parameter) => (
            <ContentParameterCard
              key={parameter.id}
              parameter={parameter}
              draft={draft}
              parametersByKey={parametersByKey}
              isSaving={isSaving}
              onChangeField={setDraftField}
              onChangeEnumBounds={setEnumBounds}
            />
          ))}
        </div>
      )}
    </div>
  )
}

interface ContentParameterCardProps {
  parameter: ParameterMeta
  draft: Record<string, SystemParameterValue>
  parametersByKey: Map<string, { serverValue: SystemParameterValue }>
  isSaving: boolean
  onChangeField: <K extends keyof SystemParameterValue>(
    backendKey: string,
    field: K,
    value: SystemParameterValue[K],
  ) => void
  onChangeEnumBounds: (
    backendKey: string,
    bounds: { min?: number; max?: number },
  ) => void
}

function formatOrDash(value: number | undefined): string {
  return value === undefined ? '—' : String(value)
}

function ContentParameterCard({
  parameter,
  draft,
  parametersByKey,
  isSaving,
  onChangeField,
  onChangeEnumBounds,
}: ContentParameterCardProps) {
  const badge = CATEGORY_BADGE[parameter.category]
  const unitLabel = UNIT_LABEL[parameter.unit]
  const draftValue = draft[parameter.backendKey] ?? {}
  const serverValue = parametersByKey.get(parameter.backendKey)?.serverValue

  if (parameter.type === 'integer') {
    return (
      <ParameterCard
        categoryLabel={badge.label}
        categoryChipClass={badge.chipClass}
        title={parameter.title}
        description={parameter.description}
        originalValueLabel={`기존값 ${formatOrDash(serverValue?.value)} ${unitLabel}`}
      >
        <div className="flex items-center gap-2">
          <input
            type="number"
            inputMode="numeric"
            value={draftValue.value ?? ''}
            onChange={(event) =>
              onChangeField(parameter.backendKey, 'value', Number(event.target.value))
            }
            disabled={isSaving}
            className={NUMBER_INPUT_CLASS}
          />
          <span className="body-r text-fg-secondary">{unitLabel}</span>
        </div>
      </ParameterCard>
    )
  }

  if (parameter.type === 'range') {
    return (
      <ParameterCard
        categoryLabel={badge.label}
        categoryChipClass={badge.chipClass}
        title={parameter.title}
        description={parameter.description}
        originalValueLabel={`기존값 ${formatOrDash(serverValue?.min)} ~ ${formatOrDash(serverValue?.max)} ${unitLabel}`}
      >
        <div className="flex items-center gap-2">
          <input
            type="number"
            inputMode="numeric"
            value={draftValue.min ?? ''}
            onChange={(event) =>
              onChangeField(parameter.backendKey, 'min', Number(event.target.value))
            }
            disabled={isSaving}
            className={NUMBER_INPUT_CLASS}
            aria-label="최소"
          />
          <span className="body-r text-fg-secondary">~</span>
          <input
            type="number"
            inputMode="numeric"
            value={draftValue.max ?? ''}
            onChange={(event) =>
              onChangeField(parameter.backendKey, 'max', Number(event.target.value))
            }
            disabled={isSaving}
            className={NUMBER_INPUT_CLASS}
            aria-label="최대"
          />
          <span className="body-r text-fg-secondary">{unitLabel}</span>
        </div>
      </ParameterCard>
    )
  }

  // enum — admin이 [최소, 최대] 두 값만 입력 → allowed/default를 hook이 자동 산출.
  //   allowed = [min, floor((min+max)/2), max]
  //   default = min
  const draftAllowed = draftValue.allowed
  const draftMin = draftAllowed?.[0]
  const draftMax =
    draftAllowed && draftAllowed.length > 0
      ? draftAllowed[draftAllowed.length - 1]
      : undefined
  const serverAllowed = serverValue?.allowed
  return (
    <ParameterCard
      categoryLabel={badge.label}
      categoryChipClass={badge.chipClass}
      title={parameter.title}
      description={parameter.description}
      originalValueLabel={
        serverAllowed && serverAllowed.length > 0
          ? `기존 허용 ${serverAllowed.join(' / ')} ${unitLabel} · 기본 ${formatOrDash(serverValue?.default)}`
          : `기존값 — ${unitLabel}`
      }
    >
      <div className="flex flex-col items-end gap-1.5">
        <div className="flex items-center gap-2">
          <input
            type="number"
            inputMode="numeric"
            value={draftMin ?? ''}
            onChange={(event) =>
              onChangeEnumBounds(parameter.backendKey, {
                min: Number(event.target.value),
              })
            }
            disabled={isSaving}
            className={NUMBER_INPUT_CLASS}
            aria-label="최소 시간"
          />
          <span className="body-r text-fg-secondary">~</span>
          <input
            type="number"
            inputMode="numeric"
            value={draftMax ?? ''}
            onChange={(event) =>
              onChangeEnumBounds(parameter.backendKey, {
                max: Number(event.target.value),
              })
            }
            disabled={isSaving}
            className={NUMBER_INPUT_CLASS}
            aria-label="최대 시간"
          />
          <span className="body-r text-fg-secondary">{unitLabel}</span>
        </div>
        {draftAllowed && draftAllowed.length === 3 && (
          <span className="caption-r text-fg-secondary">
            허용 {draftAllowed.join(' / ')} · 기본 {draftAllowed[0]}
          </span>
        )}
      </div>
    </ParameterCard>
  )
}
