'use client'

import Image from 'next/image'
import { useState } from 'react'
import { EyeOff, FileSearch, RotateCcw, Search, X } from 'lucide-react'

import type { AdminCommunityMemoDetailResponse } from '@/shared/types'
import { useAdminAuthStore } from '@/shared/stores'
import { canMutateBackoffice, formatKoreanDateTime } from '@/shared/utils'

import { AdminReadOnlyNotice } from '../components'
import {
  MemoDetailModal,
  MemoFilterBar,
  MemoPagination,
  MemoReasonModal,
  type MemoReasonAction,
  MemoStatusBadge,
} from './components'
import { useAdminCommunityMemos, useMemoDetail } from './hooks'

const SOURCE_LABEL: Record<string, string> = {
  DIRECT: '직접 작성',
  GALLERY: '갤러리',
}

function formatDate(value: string | null): string {
  return formatKoreanDateTime(value, {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function truncate(text: string | null, limit: number): string {
  if (!text) return '—'
  if (text.length <= limit) return text
  return `${text.slice(0, limit)}…`
}

interface PendingAction {
  memoId: string
  action: MemoReasonAction
}

export default function AdminCommunityCanvasPage() {
  const adminRole = useAdminAuthStore((state) => state.admin?.role ?? null)
  const canModerateMemos = canMutateBackoffice(adminRole)
  const {
    items,
    totalElements,
    page,
    totalPages,
    filter,
    inputKeyword,
    committedKeyword,
    isLoading,
    loadError,
    isMutating,
    setInputKeyword,
    commitKeyword,
    clearKeyword,
    changeFilter,
    goToPage,
    hide,
    restore,
  } = useAdminCommunityMemos()

  const [pendingAction, setPendingAction] = useState<PendingAction | null>(null)
  const [detailMemoId, setDetailMemoId] = useState<string | null>(null)
  const {
    detail,
    isLoading: isDetailLoading,
    error: detailError,
    setDetail,
  } = useMemoDetail(detailMemoId)

  const closeReasonModal = () => {
    if (isMutating) return
    setPendingAction(null)
  }

  const closeDetailModal = () => {
    if (isMutating) return
    setDetailMemoId(null)
  }

  const handleMutationSuccess = (updated: AdminCommunityMemoDetailResponse) => {
    setPendingAction(null)
    // 상세 모달이 열려 있고 같은 메모면 같이 갱신.
    if (detailMemoId === updated.memoId) {
      setDetail(updated)
    }
  }

  const submitReason = (reason: string) => {
    if (!pendingAction || !canModerateMemos) return
    const { memoId, action } = pendingAction
    if (action === 'hide') hide(memoId, reason, handleMutationSuccess)
    else restore(memoId, reason, handleMutationSuccess)
  }

  const handleSearchSubmit = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    commitKeyword()
  }

  return (
    <div className="flex flex-col gap-4">
      <header className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex flex-col gap-1">
          <h2 className="h3-b text-fg-primary">활성 메모 목록</h2>
          <p className="body-r text-fg-secondary">
            커뮤니티 캔버스에 부착된 메모 — 메타데이터·OCR로만 판단
          </p>
        </div>
        <MemoFilterBar
          value={filter}
          onChange={changeFilter}
          disabled={isMutating}
        />
      </header>

      {!canModerateMemos && <AdminReadOnlyNotice />}

      <form onSubmit={handleSearchSubmit} className="flex items-center gap-2">
        <div className="flex flex-1 items-center gap-2 rounded-[var(--radius-md)] border border-border-default bg-surface-default px-3 py-2">
          <Search className="h-4 w-4 text-fg-secondary" />
          <input
            type="text"
            value={inputKeyword}
            onChange={(event) => setInputKeyword(event.target.value)}
            placeholder="작성자 닉네임 또는 OCR 텍스트"
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
              <th className="caption-b w-20 px-4 py-3 text-left text-fg-secondary">
                썸네일
              </th>
              <th className="caption-b px-4 py-3 text-left text-fg-secondary">
                작성자
              </th>
              <th className="caption-b px-4 py-3 text-left text-fg-secondary">
                출처
              </th>
              <th className="caption-b px-4 py-3 text-left text-fg-secondary">
                OCR 텍스트
              </th>
              <th className="caption-b px-4 py-3 text-center text-fg-secondary">
                신고
              </th>
              <th className="caption-b px-4 py-3 text-left text-fg-secondary">
                상태
              </th>
              <th className="caption-b px-4 py-3 text-left text-fg-secondary">
                부착 시각
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
                  colSpan={8}
                  className="body-r px-4 py-8 text-center text-fg-secondary"
                >
                  불러오는 중…
                </td>
              </tr>
            ) : loadError ? (
              <tr>
                <td
                  colSpan={8}
                  className="body-r px-4 py-8 text-center text-red-500"
                  role="alert"
                >
                  {loadError}
                </td>
              </tr>
            ) : items.length === 0 ? (
              <tr>
                <td
                  colSpan={8}
                  className="body-r px-4 py-8 text-center text-fg-secondary"
                >
                  조건에 맞는 메모가 없습니다.
                </td>
              </tr>
            ) : (
              items.map((memo) => (
                <tr
                  key={memo.memoId}
                  className="border-b border-border-default last:border-b-0"
                >
                  <td className="px-4 py-3">
                    <Image
                      src={memo.memoThumbnailImageUrl}
                      alt={`${memo.authorNickname}의 메모 썸네일`}
                      width={56}
                      height={56}
                      unoptimized
                      className="h-14 w-14 rounded-[var(--radius-sm)] border border-border-default object-cover"
                    />
                  </td>
                  <td className="body-m px-4 py-3 text-fg-primary">
                    <div className="flex flex-col gap-0.5">
                      <span>{memo.authorNickname}</span>
                      <span className="caption-r font-mono text-fg-disabled">
                        {memo.authorUserUuid.slice(0, 8)}…
                      </span>
                    </div>
                  </td>
                  <td className="body-r px-4 py-3 text-fg-secondary">
                    <div className="flex flex-col gap-0.5">
                      <span>
                        {SOURCE_LABEL[memo.sourceType] ?? memo.sourceType}
                      </span>
                      <span className="caption-r text-fg-disabled">
                        {memo.artifactKind}
                      </span>
                    </div>
                  </td>
                  <td className="body-r px-4 py-3 text-fg-primary">
                    {truncate(memo.ocrText, 30)}
                  </td>
                  <td className="body-m px-4 py-3 text-center text-fg-primary">
                    {memo.reportCount}
                  </td>
                  <td className="px-4 py-3">
                    <MemoStatusBadge
                      isHidden={memo.isHidden}
                      hiddenReason={memo.hiddenReason}
                      moderationStatus={memo.moderationStatus}
                    />
                  </td>
                  <td className="caption-r px-4 py-3 text-fg-secondary">
                    {formatDate(memo.attachedAt)}
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex items-center justify-end gap-1.5">
                      <button
                        type="button"
                        onClick={() => setDetailMemoId(memo.memoId)}
                        disabled={isMutating}
                        className="caption-b inline-flex items-center gap-1 rounded-[var(--radius-md)] border border-border-default bg-surface-default px-3 py-1.5 text-fg-primary transition-colors hover:bg-surface-subtle disabled:opacity-50"
                      >
                        <FileSearch className="h-3.5 w-3.5" />
                        상세
                      </button>
                      {memo.isHidden ? (
                        <button
                          type="button"
                          onClick={() =>
                            setPendingAction({
                              memoId: memo.memoId,
                              action: 'restore',
                            })
                          }
                          disabled={isMutating || !canModerateMemos}
                          title={
                            canModerateMemos
                              ? undefined
                              : '뷰어 권한은 조회만 가능합니다.'
                          }
                          className="caption-b inline-flex items-center gap-1 rounded-[var(--radius-md)] border border-border-default bg-surface-default px-3 py-1.5 text-fg-primary transition-colors hover:bg-surface-subtle disabled:opacity-50"
                        >
                          <RotateCcw className="h-3.5 w-3.5" />
                          복원
                        </button>
                      ) : (
                        <button
                          type="button"
                          onClick={() =>
                            setPendingAction({
                              memoId: memo.memoId,
                              action: 'hide',
                            })
                          }
                          disabled={isMutating || !canModerateMemos}
                          title={
                            canModerateMemos
                              ? undefined
                              : '뷰어 권한은 조회만 가능합니다.'
                          }
                          className="caption-b inline-flex items-center gap-1 rounded-[var(--radius-md)] bg-red-500 px-3 py-1.5 text-fg-inverse transition-opacity hover:bg-red-600 disabled:opacity-50"
                        >
                          <EyeOff className="h-3.5 w-3.5" />
                          숨김
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      <p className="caption-r text-fg-secondary">
        ⓘ 메모의 원본/대표 이미지는 운영 판단을 위해서만 표시됩니다. 숨김 사유는 감사
        로그에 영구 기록됩니다.
      </p>

      <div className="flex items-center justify-between">
        <span className="caption-r text-fg-secondary">총 {totalElements}개</span>
        <MemoPagination
          page={page}
          totalPages={totalPages}
          onChange={goToPage}
          disabled={isLoading || isMutating}
        />
      </div>

      <MemoDetailModal
        open={detailMemoId !== null}
        detail={detail}
        isLoading={isDetailLoading}
        error={detailError}
        isMutating={isMutating}
        canModerate={canModerateMemos}
        onClose={closeDetailModal}
        onHide={(memoId) => {
          if (!canModerateMemos) return
          setPendingAction({ memoId, action: 'hide' })
        }}
        onRestore={(memoId) => {
          if (!canModerateMemos) return
          setPendingAction({ memoId, action: 'restore' })
        }}
      />

      <MemoReasonModal
        open={pendingAction !== null}
        action={pendingAction?.action ?? 'hide'}
        isSubmitting={isMutating}
        onSubmit={submitReason}
        onCancel={closeReasonModal}
      />
    </div>
  )
}
