'use client'

import { Search } from 'lucide-react'

import { useAdminAuthStore } from '@/shared/stores'
import { canMutateBackoffice, formatKoreanDateTime } from '@/shared/utils'
import { AdminReadOnlyNotice } from '../../components'
import { useBackofficeFlipbookRooms } from '../hooks'
import type { FlipbookRoomStatusFilter } from '../hooks'

import { type RoomFilterOption, RoomFilterBar } from './RoomFilterBar'
import { RoomPagination } from './RoomPagination'
import { RoomStatusBadge } from './RoomStatusBadge'

// 플립북 필터 status는 백엔드가 WAITING/PLAYING/FINISHED만 허용한다 (FINALIZING 제외).
const STATUS_OPTIONS: RoomFilterOption<FlipbookRoomStatusFilter>[] = [
  { value: 'ALL', label: '전체' },
  { value: 'WAITING', label: '대기중' },
  { value: 'PLAYING', label: '진행중' },
  { value: 'FINISHED', label: '완료' },
]

function formatGameStartedAt(value: string | null): string {
  return formatKoreanDateTime(value, {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  })
}

function formatRound(
  current: number | null,
  total: number | null,
): React.ReactNode {
  if (current === null && total === null) return <span className="text-amber-700">대기중</span>
  if (current === null) return `—/${total ?? '?'}`
  return `R${current}/${total ?? '?'}`
}

export default function BackofficeFlipbookRoomsPage() {
  const adminRole = useAdminAuthStore((state) => state.admin?.role ?? null)
  const canManageRooms = canMutateBackoffice(adminRole)
  const {
    items,
    isFiltered,
    totalElements,
    page,
    totalPages,
    statusFilter,
    keyword,
    isLoading,
    loadError,
    isMutating,
    setKeyword,
    changeStatusFilter,
    goToPage,
    forceClose,
  } = useBackofficeFlipbookRooms()

  return (
    <div className="flex flex-col gap-4">
      <header className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex flex-col gap-1">
          <h2 className="h3-b text-fg-primary">활성 방 목록</h2>
          <p className="body-r text-fg-secondary">
            현재 진행 중인 플립북 — 메타데이터만 표시
          </p>
        </div>
        <RoomFilterBar<FlipbookRoomStatusFilter>
          value={statusFilter}
          onChange={changeStatusFilter}
          options={STATUS_OPTIONS}
          disabled={isMutating}
        />
      </header>

      {!canManageRooms && <AdminReadOnlyNotice />}

      <div className="flex items-center gap-2">
        <div className="flex flex-1 items-center gap-2 rounded-[var(--radius-md)] border border-border-default bg-surface-default px-3 py-2">
          <Search className="h-4 w-4 text-fg-secondary" />
          <input
            type="text"
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            placeholder="방 코드 검색 (예: AB3K9Q)"
            className="body-r flex-1 bg-transparent text-fg-primary placeholder:text-fg-disabled focus:outline-none"
          />
        </div>
      </div>

      <div className="overflow-x-auto rounded-[var(--radius-lg)] border border-border-default bg-surface-default">
        <table className="min-w-[720px] w-full">
          <thead>
            <tr className="border-b border-border-default bg-surface-subtle">
              <th className="caption-b px-4 py-3 text-left text-fg-secondary">
                방 코드
              </th>
              <th className="caption-b px-4 py-3 text-left text-fg-secondary">
                인원
              </th>
              <th className="caption-b px-4 py-3 text-left text-fg-secondary">
                라운드
              </th>
              <th className="caption-b px-4 py-3 text-left text-fg-secondary">
                시작 시각
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
                    : '활성 방이 없습니다.'}
                </td>
              </tr>
            ) : (
              items.map((room) => (
                <tr
                  key={room.roomCode}
                  className="border-b border-border-default last:border-b-0"
                >
                  <td className="body-m px-4 py-3 font-mono text-fg-primary">
                    /f/{room.roomCode}
                  </td>
                  <td className="body-m px-4 py-3 text-fg-primary">
                    {room.participantCount}
                  </td>
                  <td className="body-m px-4 py-3 text-fg-primary">
                    {formatRound(room.currentRound, room.totalRounds)}
                  </td>
                  <td className="body-r px-4 py-3 text-fg-secondary">
                    {formatGameStartedAt(room.gameStartedAt)}
                  </td>
                  <td className="px-4 py-3">
                    <RoomStatusBadge status={room.status} />
                  </td>
                  <td className="px-4 py-3 text-right">
                    <button
                      type="button"
                      onClick={() => forceClose(room.roomCode)}
                      disabled={isMutating || !canManageRooms}
                      title={
                        canManageRooms
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
        ⓘ 프레임별 드로잉과 완성 GIF는 어떤 경우에도 표시되지 않습니다. 강제 종료는
        메타데이터만 보고 판단합니다.
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
