import { api } from '@/shared/libs'
import type {
  ApiResponse,
  GalleryDeleteResponse,
  GalleryDetailResponse,
  GalleryListResponse,
} from '@/shared/types'

import { apiUnwrap } from '@/shared/utils'

interface GetGalleryListParams {
  page?: number
  size?: number
}

// GET /gallery — 내 갤러리 목록 조회
export const getGalleryList = ({ page = 0, size = 20 }: GetGalleryListParams = {}) =>
  apiUnwrap(api.get<ApiResponse<GalleryListResponse>>('gallery', { page, size }))

// GET /gallery/{galleryId} — 내 갤러리 항목 상세 조회
export const getGallery = (galleryId: string) =>
  apiUnwrap(api.get<ApiResponse<GalleryDetailResponse>>(`gallery/${galleryId}`))

// DELETE /gallery/{galleryId} — 갤러리 항목 삭제
export const deleteGallery = (galleryId: string) =>
  apiUnwrap(api.delete<ApiResponse<GalleryDeleteResponse>>(`gallery/${galleryId}`))
