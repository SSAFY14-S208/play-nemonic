'use client'

import { ArrowLeft } from 'lucide-react'
import { PHONE_INQUIRY_TYPE_OPTIONS } from '../constants'
import { usePhoneInquiry } from '../hooks'
import { usePhoneStore } from '../phoneStore'

const CONTENT_MAX_LENGTH = 500

export function PhoneInquiryScreen() {
  const goHome = usePhoneStore((state) => state.goHome)
  const {
    type,
    setType,
    title,
    setTitle,
    content,
    setContent,
    email,
    setEmail,
    isSubmitting,
    fieldErrors,
    clearFieldError,
    submit,
  } = usePhoneInquiry()

  const handleSubmit = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    void submit()
  }

  return (
    <div className="flex h-full flex-col bg-white pt-[3.35rem]">
      <header className="shrink-0 border-b border-gray-200 px-5 pb-3">
        <div className="flex h-12 items-center justify-between">
          <button
            type="button"
            onClick={goHome}
            disabled={isSubmitting}
            aria-label="홈으로 돌아가기"
            className="grid size-11 place-items-center rounded-full text-gray-600 transition hover:bg-gray-100 disabled:opacity-50"
          >
            <ArrowLeft className="size-5" />
          </button>
          <h2 className="phone-home-body-m text-gray-900">고객 문의</h2>
          <div className="size-11" aria-hidden />
        </div>
      </header>

      <form
        onSubmit={handleSubmit}
        className="flex flex-1 flex-col gap-4 overflow-y-auto px-4 py-4"
      >
        <fieldset className="flex flex-col gap-1">
          <label
            htmlFor="inquiry-type"
            className="phone-home-caption-m text-gray-600"
          >
            문의 유형
          </label>
          <select
            id="inquiry-type"
            value={type}
            onChange={(event) =>
              setType(event.target.value as typeof type)
            }
            disabled={isSubmitting}
            className="phone-home-body-m rounded-lg border border-gray-300 bg-white px-3 py-2 text-gray-900 focus:border-amber-500 focus:outline-none disabled:opacity-50"
          >
            {PHONE_INQUIRY_TYPE_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </fieldset>

        <fieldset className="flex flex-col gap-1">
          <label
            htmlFor="inquiry-title"
            className="phone-home-caption-m text-gray-600"
          >
            제목 <span className="text-red-500">*</span>
          </label>
          <input
            id="inquiry-title"
            type="text"
            value={title}
            onChange={(event) => {
              setTitle(event.target.value)
              clearFieldError('title')
            }}
            placeholder="문의 제목을 입력해주세요"
            disabled={isSubmitting}
            className="phone-home-body-m rounded-lg border border-gray-300 bg-white px-3 py-2 text-gray-900 placeholder:text-gray-400 focus:border-amber-500 focus:outline-none disabled:opacity-50"
          />
          {fieldErrors.title && (
            <span className="phone-home-caption-m text-red-500">
              {fieldErrors.title}
            </span>
          )}
        </fieldset>

        <fieldset className="flex flex-col gap-1">
          <label
            htmlFor="inquiry-content"
            className="phone-home-caption-m text-gray-600"
          >
            내용 <span className="text-red-500">*</span>
          </label>
          <textarea
            id="inquiry-content"
            value={content}
            onChange={(event) => {
              if (event.target.value.length <= CONTENT_MAX_LENGTH) {
                setContent(event.target.value)
                clearFieldError('content')
              }
            }}
            placeholder="문의 내용을 입력해주세요"
            rows={5}
            disabled={isSubmitting}
            className="phone-home-body-m resize-none rounded-lg border border-gray-300 bg-white px-3 py-2 text-gray-900 placeholder:text-gray-400 focus:border-amber-500 focus:outline-none disabled:opacity-50"
          />
          <div className="flex items-center justify-between">
            {fieldErrors.content ? (
              <span className="phone-home-caption-m text-red-500">
                {fieldErrors.content}
              </span>
            ) : (
              <span />
            )}
            <span className="phone-home-caption-m text-gray-400">
              {content.length}/{CONTENT_MAX_LENGTH}
            </span>
          </div>
        </fieldset>

        <fieldset className="flex flex-col gap-1">
          <label
            htmlFor="inquiry-email"
            className="phone-home-caption-m text-gray-600"
          >
            이메일 (선택)
          </label>
          <input
            id="inquiry-email"
            type="email"
            value={email}
            onChange={(event) => {
              setEmail(event.target.value)
              clearFieldError('email')
            }}
            placeholder="답변 받을 이메일을 입력해주세요"
            disabled={isSubmitting}
            className="phone-home-body-m rounded-lg border border-gray-300 bg-white px-3 py-2 text-gray-900 placeholder:text-gray-400 focus:border-amber-500 focus:outline-none disabled:opacity-50"
          />
          {fieldErrors.email && (
            <span className="phone-home-caption-m text-red-500">
              {fieldErrors.email}
            </span>
          )}
        </fieldset>

        <div className="mt-auto pt-2 pb-4">
          <button
            type="submit"
            disabled={isSubmitting}
            className="phone-home-body-m flex w-full items-center justify-center gap-2 rounded-lg bg-amber-500 py-3 text-white transition-opacity hover:opacity-90 disabled:opacity-50"
          >
            {isSubmitting && (
              <span
                aria-hidden
                className="size-4 animate-spin rounded-full border-2 border-white/40 border-t-white"
              />
            )}
            문의 접수하기
          </button>
        </div>
      </form>
    </div>
  )
}
