'use client'

import Image from 'next/image'
import { Loader2, Play, X } from 'lucide-react'
import { useState } from 'react'

import { cn } from '@/shared/libs'
import type {
  FortuneCreateRequest,
  GmsPromptPreviewResponse,
} from '@/shared/types'

interface GmsPromptTestModalProps {
  promptName: string
  isTesting: boolean
  onRunTest: (
    sampleSaju: FortuneCreateRequest,
  ) => Promise<GmsPromptPreviewResponse | null>
  onClose: () => void
}

type SajuTextField = Exclude<keyof FortuneCreateRequest, 'calendarType'>

const DEFAULT_SAMPLE_SAJU: FortuneCreateRequest = {
  calendarType: 'solar',
  yearPillar: '갑신',
  monthPillar: '경술',
  dayPillar: '계유',
  hourPillar: '기묘',
  dayMasterElement: '수',
  dayBranchElement: '금',
  dayMasterYinYang: '음',
  dayBranchYinYang: '음',
}

const SAJU_FIELDS: { key: SajuTextField; label: string }[] = [
  { key: 'yearPillar', label: '년주' },
  { key: 'monthPillar', label: '월주' },
  { key: 'dayPillar', label: '일주' },
  { key: 'hourPillar', label: '시주' },
  { key: 'dayMasterElement', label: '일간 오행' },
  { key: 'dayBranchElement', label: '일지 오행' },
  { key: 'dayMasterYinYang', label: '일간 음양' },
  { key: 'dayBranchYinYang', label: '일지 음양' },
]

const SCORE_ITEMS = [
  { key: 'overallLuck', label: '종합운' },
  { key: 'loveLuck', label: '관계운' },
  { key: 'workLuck', label: '일운' },
  { key: 'moneyLuck', label: '금전운' },
] as const

const INPUT_CLASS =
  'body-r rounded-[var(--radius-md)] border border-border-default bg-surface-default px-3 py-2 text-fg-primary placeholder:text-fg-disabled focus:border-primary-2 focus:outline-none disabled:opacity-50'

function createPreviewImageSrc(value: string) {
  if (!value) return null
  return value.startsWith('data:') ? value : `data:image/png;base64,${value}`
}

export function GmsPromptTestModal({
  promptName,
  isTesting,
  onRunTest,
  onClose,
}: GmsPromptTestModalProps) {
  const [sampleSaju, setSampleSaju] =
    useState<FortuneCreateRequest>(DEFAULT_SAMPLE_SAJU)
  const [result, setResult] = useState<GmsPromptPreviewResponse | null>(null)

  const isFormValid = SAJU_FIELDS.every((field) =>
    sampleSaju[field.key].trim(),
  )

  const updateField = <K extends keyof FortuneCreateRequest>(
    field: K,
    value: FortuneCreateRequest[K],
  ) => {
    setSampleSaju((prev) => ({ ...prev, [field]: value }))
  }

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!isFormValid || isTesting) return
    setResult(null)
    const nextResult = await onRunTest(sampleSaju)
    if (nextResult) setResult(nextResult)
  }

  const previewImageSrc = result
    ? createPreviewImageSrc(result.previewImageBase64)
    : null

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="gms-prompt-test-title"
      className="fixed inset-0 z-[var(--z-modal)] flex items-center justify-center bg-black/40 px-4 backdrop-blur-sm"
    >
      <form
        onSubmit={handleSubmit}
        className="flex max-h-[92vh] w-full max-w-5xl flex-col overflow-hidden rounded-[var(--radius-xl)] bg-surface-default shadow-lg"
      >
        <header className="flex items-center justify-between gap-3 border-b border-border-default px-6 py-4">
          <div className="flex min-w-0 flex-col gap-1">
            <h2 id="gms-prompt-test-title" className="h3-b text-fg-primary">
              GMS 프롬프트 테스트
            </h2>
            <p className="body-r truncate text-fg-secondary">{promptName}</p>
          </div>
          <button
            type="button"
            onClick={onClose}
            disabled={isTesting}
            className="rounded-[var(--radius-md)] p-1 text-fg-secondary transition-colors hover:bg-surface-subtle disabled:opacity-50"
            aria-label="닫기"
          >
            <X className="h-5 w-5" />
          </button>
        </header>

        <div className="grid min-h-0 flex-1 grid-cols-1 overflow-y-auto lg:grid-cols-[minmax(0,380px)_minmax(0,1fr)]">
          <section className="flex flex-col gap-4 border-b border-border-default px-6 py-5 lg:border-b-0 lg:border-r">
            <label className="flex flex-col gap-1.5">
              <span className="caption-b text-fg-secondary">달력</span>
              <select
                value={sampleSaju.calendarType}
                onChange={(event) =>
                  updateField(
                    'calendarType',
                    event.target.value as FortuneCreateRequest['calendarType'],
                  )
                }
                disabled={isTesting}
                className={INPUT_CLASS}
              >
                <option value="solar">양력</option>
                <option value="lunar">음력</option>
              </select>
            </label>

            <div className="grid grid-cols-2 gap-3">
              {SAJU_FIELDS.map((field) => (
                <label key={field.key} className="flex flex-col gap-1.5">
                  <span className="caption-b text-fg-secondary">
                    {field.label}
                  </span>
                  <input
                    type="text"
                    value={sampleSaju[field.key]}
                    onChange={(event) =>
                      updateField(field.key, event.target.value)
                    }
                    disabled={isTesting}
                    className={INPUT_CLASS}
                  />
                </label>
              ))}
            </div>
          </section>

          <section className="min-h-0 overflow-y-auto px-6 py-5">
            {!result ? (
              <div className="flex h-full min-h-80 items-center justify-center rounded-[var(--radius-lg)] border border-dashed border-border-default">
                <p className="body-r text-fg-secondary">
                  테스트 결과가 여기에 표시됩니다.
                </p>
              </div>
            ) : (
              <div className="grid gap-5 xl:grid-cols-[minmax(0,1fr)_280px]">
                <div className="flex min-w-0 flex-col gap-4">
                  <div className="flex flex-col gap-2">
                    <span className="caption-b text-fg-secondary">
                      {result.featureType}
                    </span>
                    <h3 className="h4-b text-fg-primary">
                      {result.fortune.title}
                    </h3>
                    <p className="body-r whitespace-pre-wrap text-fg-primary">
                      {result.fortune.summary}
                    </p>
                  </div>

                  <div className="grid grid-cols-2 gap-3">
                    {SCORE_ITEMS.map((item) => (
                      <div
                        key={item.key}
                        className="flex flex-col gap-1 rounded-[var(--radius-md)] border border-border-default p-3"
                      >
                        <span className="caption-b text-fg-secondary">
                          {item.label}
                        </span>
                        <span className="h4-b text-fg-primary">
                          {result.fortune[item.key]}점
                        </span>
                      </div>
                    ))}
                  </div>

                  <div className="grid gap-3 md:grid-cols-2">
                    <ResultItem label="행운 색" value={result.fortune.luckyColor} />
                    <ResultItem
                      label="키워드"
                      value={result.fortune.luckyKeyword}
                    />
                    <ResultItem
                      label="포스트잇 문구"
                      value={result.fortune.postitLine}
                    />
                    <ResultItem
                      label="주의할 점"
                      value={result.fortune.caution ?? '-'}
                    />
                  </div>
                </div>

                <div className="flex flex-col gap-2">
                  <span className="caption-b text-fg-secondary">
                    카드 미리보기
                  </span>
                  {previewImageSrc ? (
                    <Image
                      src={previewImageSrc}
                      alt="GMS 프롬프트 테스트 카드 미리보기"
                      width={280}
                      height={392}
                      unoptimized
                      className="h-auto w-full rounded-[var(--radius-md)] border border-border-default object-contain"
                    />
                  ) : (
                    <div className="grid aspect-[5/7] place-items-center rounded-[var(--radius-md)] border border-dashed border-border-default">
                      <span className="caption-r text-fg-secondary">
                        미리보기 없음
                      </span>
                    </div>
                  )}
                </div>
              </div>
            )}
          </section>
        </div>

        <footer className="flex items-center justify-end gap-2 border-t border-border-default px-6 py-4">
          <button
            type="button"
            onClick={onClose}
            disabled={isTesting}
            className="body-b rounded-[var(--radius-md)] border border-border-default bg-surface-default px-4 py-2 text-fg-primary transition-colors hover:bg-surface-subtle disabled:opacity-50"
          >
            닫기
          </button>
          <button
            type="submit"
            disabled={!isFormValid || isTesting}
            className="body-b inline-flex items-center gap-1.5 rounded-[var(--radius-md)] bg-primary-1 px-4 py-2 text-fg-inverse transition-opacity hover:opacity-90 disabled:opacity-50"
          >
            {isTesting ? (
              <Loader2 className="h-4 w-4 animate-spin" />
            ) : (
              <Play className="h-4 w-4" />
            )}
            {isTesting ? '테스트 중' : '테스트 실행'}
          </button>
        </footer>
      </form>
    </div>
  )
}

interface ResultItemProps {
  label: string
  value: string
}

function ResultItem({ label, value }: ResultItemProps) {
  return (
    <div className="flex flex-col gap-1 rounded-[var(--radius-md)] border border-border-default p-3">
      <span className="caption-b text-fg-secondary">{label}</span>
      <span className={cn('body-r whitespace-pre-wrap text-fg-primary')}>
        {value}
      </span>
    </div>
  )
}
