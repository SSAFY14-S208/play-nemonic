'use client'

import { Search } from 'lucide-react'

import { useAdminAuthStore } from '@/shared/stores'
import { canMutateBackoffice, formatKoreanDateTime } from '@/shared/utils'
import { AdminReadOnlyNotice } from '../../components'
import { useBackofficeInfiniteCanvases } from '../hooks'

import { RoomPagination } from './RoomPagination'
import { RoomStatusBadge } from './RoomStatusBadge'

function formatCanvasDateTime(value: string | null | undefined): string {
  return formatKoreanDateTime(value, {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  })
}

export default function BackofficeInfiniteCanvasesPage() {
  const adminRole = useAdminAuthStore((state) => state.admin?.role ?? null)
  const canManageCanvases = canMutateBackoffice(adminRole)
  const {
    items,
    isFiltered,
    totalElements,
    page,
    totalPages,
    keyword,
    isLoading,
    loadError,
    isMutating,
    setKeyword,
    goToPage,
    forceClose,
  } = useBackofficeInfiniteCanvases()

  return (
    <div className="flex flex-col gap-4">
      <header className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex flex-col gap-1">
          <h2 className="h3-b text-fg-primary">활성 캔버스 목록</h2>
          <p className="body-r text-fg-secondary">
            현재 활성화된 무한 캔버스 — 내부 요소와 이미지는 표시하지 않음
          </p>
        </div>
      </header>

      {!canManageCanvases && <AdminReadOnlyNotice />}

      <div className="flex items-center gap-2">
        <div className="flex flex-1 items-center gap-2 rounded-[var(--radius-md)] border border-border-default bg-surface-default px-3 py-2">
          <Search className="h-4 w-4 text-fg-secondary" />
          <input
            type="text"
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            placeholder="캔버스 코드 검색 (예: AB3K9Q)"
            className="body-r flex-1 bg-transparent text-fg-primary placeholder:text-fg-disabled focus:outline-none"
          />
        </div>
      </div>

      <div className="overflow-x-auto rounded-[var(--radius-lg)] border border-border-default bg-surface-default">
        <table className="min-w-[840px] w-full">
          <thead>
            <tr className="border-b border-border-default bg-surface-subtle">
              <th className="caption-b px-4 py-3 text-left text-fg-secondary">
                URL
              </th>
              <th className="caption-b px-4 py-3 text-left text-fg-secondary">
                접속자 수
              </th>
              <th className="caption-b px-4 py-3 text-left text-fg-secondary">
                생성 시각
              </th>
              <th className="caption-b px-4 py-3 text-left text-fg-secondary">
                마지막 활동
              </th>
              <th className="caption-b px-4 py-3 text-left text-fg-secondary">
                상태
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
                  colSpan={6}
                  className="body-r px-4 py-8 text-center text-fg-secondary"
                >
                  불러오는 중…
                </td>
              </tr>
            ) : loadError ? (
              <tr>
                <td
                  colSpan={6}
                  className="body-r px-4 py-8 text-center text-red-500"
                  role="alert"
                >
                  {loadError}
                </td>
              </tr>
            ) : items.length === 0 ? (
              <tr>
                <td
                  colSpan={6}
                  className="body-r px-4 py-8 text-center text-fg-secondary"
                >
                  {isFiltered
                    ? '검색 결과가 없습니다.'
                    : '활성 캔버스가 없습니다.'}
                </td>
              </tr>
            ) : (
              items.map((canvas) => (
                <tr
                  key={canvas.roomCode}
                  className="border-b border-border-default last:border-b-0"
                >
                  <td className="body-m px-4 py-3 font-mono text-fg-primary">
                    /infinite-canvas/{canvas.roomCode}
                  </td>
                  <td className="body-m px-4 py-3 text-fg-primary">
                    {canvas.connectedParticipantCount}
                  </td>
                  <td className="body-r px-4 py-3 text-fg-secondary">
                    {formatCanvasDateTime(canvas.createdAt)}
                  </td>
                  <td className="body-r px-4 py-3 text-fg-secondary">
                    {formatCanvasDateTime(canvas.updatedAt)}
                  </td>
                  <td className="px-4 py-3">
                    <RoomStatusBadge status={canvas.status} />
                  </td>
                  <td className="px-4 py-3 text-right">
                    <button
                      type="button"
                      onClick={() => forceClose(canvas.roomCode)}
                      disabled={isMutating || !canManageCanvases}
                      title={
                        canManageCanvases
                          ? undefined
                          : '뷰어 권한은 조회만 가능합니다.'
                      }
                      className="caption-b rounded-[var(--radius-md)] bg-red-500 px-3 py-1.5 text-fg-inverse transition-opacity hover:bg-red-600 disabled:opacity-50"
                    >
                      강제 종료
                    </button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      <p className="caption-r text-fg-secondary">
        ⓘ 무한 캔버스 내부 드로잉/요소는 어떤 경우에도 표시하지 않습니다. 강제 종료는
        URL과 접속 메타데이터만 보고 판단합니다.
      </p>

      <div className="flex items-center justify-between">
        <span className="caption-r text-fg-secondary">
          총 {totalElements}개
        </span>
        <RoomPagination
          page={page}
          totalPages={totalPages}
          onChange={goToPage}
          disabled={isLoading || isMutating}
        />
      </div>
    </div>
  )
}
