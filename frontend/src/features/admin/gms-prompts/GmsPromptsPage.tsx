'use client'

import { Plus, Trash2 } from 'lucide-react'

import { cn } from '@/shared/libs'
import type { GmsFeatureType } from '@/shared/types'

import { type FeatureFilter, useGmsPrompts } from './useGmsPrompts'

const FEATURE_LABEL: Record<FeatureFilter, string> = {
  ALL: '전체',
  fortune: '오늘의 운세',
  sticker: '무한캔버스',
}

const FILTER_OPTIONS: FeatureFilter[] = ['ALL', 'fortune', 'sticker']
const EDIT_FEATURE_OPTIONS: GmsFeatureType[] = ['fortune', 'sticker']

const INPUT_CLASS =
  'body-r rounded-[var(--radius-md)] border border-border-default bg-surface-default px-3 py-2 text-fg-primary placeholder:text-fg-disabled focus:border-primary-2 focus:outline-none disabled:opacity-50'

export default function GmsPromptsPage() {
  const {
    isLoading,
    loadError,
    filteredPrompts,
    featureFilter,
    setFeatureFilter,
    selectedId,
    isCreating,
    editor,
    setEditorField,
    isDirty,
    select,
    startCreate,
    cancelCreate,
    save,
    remove,
    isMutating,
  } = useGmsPrompts()

  return (
    <div className="flex h-full min-h-0 gap-6">
      <aside className="flex w-72 shrink-0 flex-col gap-3 rounded-[var(--radius-lg)] border border-border-default bg-surface-default p-4">
        <div className="flex items-center justify-between">
          <h2 className="h4-b text-fg-primary">프롬프트 목록</h2>
          <button
            type="button"
            onClick={startCreate}
            disabled={isMutating}
            className="flex items-center gap-1 rounded-[var(--radius-md)] bg-primary-1 px-2.5 py-1 caption-b text-fg-inverse transition-opacity disabled:opacity-50"
          >
            <Plus className="h-3.5 w-3.5" />
            새로 만들기
          </button>
        </div>

        <div className="flex flex-wrap gap-1">
          {FILTER_OPTIONS.map((option) => (
            <button
              key={option}
              type="button"
              onClick={() => setFeatureFilter(option)}
              className={cn(
                'caption-b rounded-full px-2.5 py-1 transition-colors',
                featureFilter === option
                  ? 'bg-primary-1 text-fg-inverse'
                  : 'bg-surface-subtle text-fg-secondary hover:bg-primary-5',
              )}
            >
              {FEATURE_LABEL[option]}
            </button>
          ))}
        </div>

        <ul className="flex min-h-0 flex-1 flex-col gap-1 overflow-y-auto">
          {filteredPrompts.map((prompt) => (
            <li key={prompt.id}>
              <button
                type="button"
                onClick={() => select(prompt.id)}
                className={cn(
                  'flex w-full flex-col gap-1 rounded-[var(--radius-md)] p-3 text-left transition-colors',
                  selectedId === prompt.id
                    ? 'bg-primary-5'
                    : 'hover:bg-surface-subtle',
                )}
              >
                <p className="body-m truncate text-fg-primary">{prompt.name}</p>
                <span className="caption-b inline-block w-fit rounded-[var(--radius-sm)] bg-surface-subtle px-1.5 py-0.5 text-fg-secondary">
                  {FEATURE_LABEL[prompt.featureType]}
                </span>
              </button>
            </li>
          ))}
          {filteredPrompts.length === 0 && !isLoading && (
            <li className="caption-r p-3 text-fg-secondary">프롬프트가 없습니다.</li>
          )}
        </ul>
      </aside>

      <section className="flex min-h-0 flex-1 flex-col gap-4 rounded-[var(--radius-lg)] border border-border-default bg-surface-default p-6">
        {isLoading ? (
          <p className="body-r text-fg-secondary">불러오는 중…</p>
        ) : loadError ? (
          <p role="alert" className="body-r text-red-500">
            {loadError}
          </p>
        ) : selectedId === null && !isCreating ? (
          <p className="body-r text-fg-secondary">
            왼쪽에서 프롬프트를 선택하거나 새로 만드세요.
          </p>
        ) : (
          <>
            <header className="flex items-start justify-between gap-4">
              <div className="flex min-w-0 flex-1 flex-col gap-2">
                <span className="caption-b text-fg-secondary">
                  {isCreating ? '새 프롬프트' : `ID ${selectedId}`}
                </span>
                <input
                  type="text"
                  value={editor.name}
                  onChange={(event) => setEditorField('name', event.target.value)}
                  placeholder="프롬프트 이름"
                  disabled={isMutating}
                  className={cn(INPUT_CLASS, 'body-l-b w-full')}
                />
              </div>
              <div className="flex shrink-0 items-center gap-2 pt-7">
                {isCreating ? (
                  <button
                    type="button"
                    onClick={cancelCreate}
                    disabled={isMutating}
                    className="rounded-[var(--radius-md)] border border-border-default bg-surface-default px-4 py-2 body-b text-fg-primary transition-colors hover:bg-surface-subtle disabled:opacity-50"
                  >
                    취소
                  </button>
                ) : (
                  <button
                    type="button"
                    onClick={remove}
                    disabled={isMutating}
                    className="flex items-center gap-1.5 rounded-[var(--radius-md)] border border-border-default bg-surface-default px-4 py-2 body-b text-fg-primary transition-colors hover:bg-surface-subtle disabled:opacity-50"
                  >
                    <Trash2 className="h-4 w-4" />
                    삭제
                  </button>
                )}
                <button
                  type="button"
                  onClick={save}
                  disabled={isMutating || !isDirty}
                  className="rounded-[var(--radius-md)] bg-primary-1 px-4 py-2 body-b text-fg-inverse transition-opacity disabled:opacity-50"
                >
                  {isMutating ? '저장 중…' : isCreating ? '생성' : '저장'}
                </button>
              </div>
            </header>

            <label className="flex w-48 flex-col gap-1">
              <span className="caption-b text-fg-secondary">기능</span>
              <select
                value={editor.featureType}
                onChange={(event) =>
                  setEditorField('featureType', event.target.value as GmsFeatureType)
                }
                disabled={isMutating}
                className={INPUT_CLASS}
              >
                {EDIT_FEATURE_OPTIONS.map((feature) => (
                  <option key={feature} value={feature}>
                    {FEATURE_LABEL[feature]}
                  </option>
                ))}
              </select>
            </label>

            <label className="flex min-h-0 flex-1 flex-col gap-1">
              <span className="caption-b text-fg-secondary">본문</span>
              <textarea
                value={editor.content}
                onChange={(event) => setEditorField('content', event.target.value)}
                placeholder="프롬프트 본문을 입력하세요"
                disabled={isMutating}
                className={cn(
                  INPUT_CLASS,
                  'min-h-[320px] flex-1 resize-none whitespace-pre font-mono',
                )}
              />
            </label>
          </>
        )}
      </section>
    </div>
  )
}
