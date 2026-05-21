'use client'

import { useState } from 'react'
import { FileSearch, Search, X } from 'lucide-react'

import type {
  AdminInquiryReplyRequest,
  AdminInquiryReplyResponse,
  AdminInquiryStatus,
  AdminInquiryStatusUpdateResponse,
  AdminInquiryType,
} from '@/shared/types'
import { useAdminAuthStore } from '@/shared/stores'
import { canMutateBackoffice, formatKoreanDateTime } from '@/shared/utils'

import { AdminReadOnlyNotice } from '../components'
import {
  InquiryDetailModal,
  InquiryFilterBar,
  InquiryPagination,
  InquiryReplyModal,
  InquiryStatusBadge,
} from './components'
import { useAdminInquiries, useInquiryDetail } from './hooks'

function formatDate(value: string | null): string {
  return formatKoreanDateTime(value, {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

const INQUIRY_TYPE_LABELS: Record<AdminInquiryType, string> = {
  error: '오류/버그',
  feature_request: '기능 제안',
  content_report: '콘텐츠 신고',
  other: '기타',
}

interface PendingReply {
  inquiryId: number
  defaultSubject: string
}

export default function AdminCsInquiriesPage() {
  const adminRole = useAdminAuthStore((state) => state.admin?.role ?? null)
  const canHandleInquiries = canMutateBackoffice(adminRole)
  const {
    items,
    totalElements,
    page,
    totalPages,
    filter,
    typeFilter,
    inputKeyword,
    committedKeyword,
    isLoading,
    loadError,
    isMutating,
    setInputKeyword,
    commitKeyword,
    clearKeyword,
    changeFilter,
    changeTypeFilter,
    goToPage,
    changeStatus,
    reply,
  } = useAdminInquiries()

  const [detailInquiryId, setDetailInquiryId] = useState<number | null>(null)
  const [pendingReply, setPendingReply] = useState<PendingReply | null>(null)
  const {
    detail,
    isLoading: isDetailLoading,
    error: detailError,
    setDetail,
  } = useInquiryDetail(detailInquiryId)

  const closeDetailModal = () => {
    if (isMutating) return
    setDetailInquiryId(null)
  }

  const closeReplyModal = () => {
    if (isMutating) return
    setPendingReply(null)
  }

  const handleStatusSuccess = (response: AdminInquiryStatusUpdateResponse) => {
    setDetail((prev) =>
      prev && prev.id === response.id
        ? { ...prev, status: response.status, updatedAt: response.updatedAt }
        : prev,
    )
  }

  const handleReplySuccess = (response: AdminInquiryReplyResponse) => {
    setDetail((prev) =>
      prev && prev.id === response.id
        ? {
            ...prev,
            status: response.status,
            respondedAt: response.respondedAt,
            updatedAt: response.respondedAt,
          }
        : prev,
    )
    setPendingReply(null)
  }

  const submitReply = (payload: AdminInquiryReplyRequest) => {
    if (!pendingReply || !canHandleInquiries) return
    reply(pendingReply.inquiryId, payload, handleReplySuccess)
  }

  const handleSearchSubmit = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    commitKeyword()
  }

  return (
    <div className="flex flex-col gap-4">
      <header className="flex flex-wrap items-center justify-end gap-3">
        <InquiryFilterBar
          statusValue={filter}
          typeValue={typeFilter}
          onStatusChange={changeFilter}
          onTypeChange={changeTypeFilter}
          disabled={isMutating}
        />
      </header>

      {!canHandleInquiries && <AdminReadOnlyNotice />}

      <form onSubmit={handleSearchSubmit} className="flex items-center gap-2">
        <div className="flex flex-1 items-center gap-2 rounded-[var(--radius-md)] border border-border-default bg-surface-default px-3 py-2">
          <Search className="h-4 w-4 text-fg-secondary" />
          <input
            type="text"
            value={inputKeyword}
            onChange={(event) => setInputKeyword(event.target.value)}
            placeholder="제목·내용·이메일 검색어"
            className="body-r flex-1 bg-transparent text-fg-primary placeholder:text-fg-disabled focus:outline-none"
          />
          {committedKeyword && (
            <button
              type="button"
              onClick={clearKeyword}
              className="text-fg-secondary hover:text-fg-primary"
              aria-label="검색어 지우기"
            >
              <X className="h-4 w-4" />
            </button>
          )}
        </div>
        <button
          type="submit"
          className="rounded-[var(--radius-md)] bg-primary-1 px-4 py-2 body-b text-fg-inverse transition-opacity hover:opacity-90"
        >
          검색
        </button>
      </form>

      <div className="overflow-hidden rounded-[var(--radius-lg)] border border-border-default bg-surface-default">
        <table className="w-full">
          <thead>
            <tr className="border-b border-border-default bg-surface-subtle">
              <th className="caption-b w-16 px-4 py-3 text-left text-fg-secondary">
                ID
              </th>
              <th className="caption-b px-4 py-3 text-left text-fg-secondary">
                유형
              </th>
              <th className="caption-b px-4 py-3 text-left text-fg-secondary">
                제목
              </th>
              <th className="caption-b px-4 py-3 text-left text-fg-secondary">
                이메일
              </th>
              <th className="caption-b px-4 py-3 text-left text-fg-secondary">
                상태
              </th>
              <th className="caption-b px-4 py-3 text-left text-fg-secondary">
                생성 시각
              </th>
              <th className="caption-b px-4 py-3 text-right text-fg-secondary">
                액션
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
            ) : items.length === 0 ? (
              <tr>
                <td
                  colSpan={7}
                  className="body-r px-4 py-8 text-center text-fg-secondary"
                >
                  조건에 맞는 문의가 없습니다.
                </td>
              </tr>
            ) : (
              items.map((inquiry) => (
                <tr
                  key={inquiry.id}
                  className="border-b border-border-default last:border-b-0"
                >
                  <td className="body-m px-4 py-3 text-fg-primary">
                    #{inquiry.id}
                  </td>
                  <td className="body-r px-4 py-3 text-fg-secondary">
                    {INQUIRY_TYPE_LABELS[inquiry.type] ?? inquiry.type}
                  </td>
                  <td className="body-m px-4 py-3 text-fg-primary">
                    {inquiry.title}
                  </td>
                  <td className="body-r px-4 py-3 text-fg-secondary">
                    {inquiry.email}
                  </td>
                  <td className="px-4 py-3">
                    <InquiryStatusBadge status={inquiry.status} />
                  </td>
                  <td className="caption-r px-4 py-3 text-fg-secondary">
                    {formatDate(inquiry.createdAt)}
                  </td>
                  <td className="px-4 py-3 text-right">
                    <button
                      type="button"
                      onClick={() => setDetailInquiryId(inquiry.id)}
                      disabled={isMutating}
                      className="caption-b inline-flex items-center gap-1 rounded-[var(--radius-md)] border border-border-default bg-surface-default px-3 py-1.5 text-fg-primary transition-colors hover:bg-surface-subtle disabled:opacity-50"
                    >
                      <FileSearch className="h-3.5 w-3.5" />
                      상세
                    </button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      <div className="flex items-center justify-between">
        <span className="caption-r text-fg-secondary">총 {totalElements}개</span>
        <InquiryPagination
          page={page}
          totalPages={totalPages}
          onChange={goToPage}
          disabled={isLoading || isMutating}
        />
      </div>

      <InquiryDetailModal
        open={detailInquiryId !== null}
        detail={detail}
        isLoading={isDetailLoading}
        error={detailError}
        isMutating={isMutating}
        canHandle={canHandleInquiries}
        onClose={closeDetailModal}
        onChangeStatus={(inquiryId: number, next: AdminInquiryStatus) => {
          if (!canHandleInquiries) return
          changeStatus(inquiryId, next, handleStatusSuccess)
        }}
        onRequestReply={(inquiryId: number, title: string) => {
          if (!canHandleInquiries) return
          setPendingReply({ inquiryId, defaultSubject: title })
        }}
      />

      <InquiryReplyModal
        open={pendingReply !== null}
        defaultSubject={pendingReply?.defaultSubject}
        isSubmitting={isMutating}
        onSubmit={submitReply}
        onCancel={closeReplyModal}
      />
    </div>
  )
}
