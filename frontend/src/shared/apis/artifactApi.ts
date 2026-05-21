import { api } from '@/shared/libs'
import type {
  ApiResponse,
  ArtifactImageUrlResponse,
  ArtifactShareResponse,
} from '@/shared/types'

import { apiUnwrap } from '@/shared/utils'

// POST /artifacts/{artifactId}/share — 산출물 공유 URL 생성
export const postArtifactShare = (artifactId: string) =>
  apiUnwrap(api.post<ApiResponse<ArtifactShareResponse>>(`artifacts/${artifactId}/share`))

// GET /artifacts/{artifactId}/image-urls — 산출물 이미지 URL 조회
export const getArtifactImageUrls = (artifactId: string) =>
  apiUnwrap(
    api.get<ApiResponse<ArtifactImageUrlResponse>>(`artifacts/${artifactId}/image-urls`),
  )

// GET /artifacts/{artifactId}/download — 산출물 파일 다운로드 (binary)
// JSON 봉투 없는 raw binary 응답이므로 api.getBlob을 사용한다.
export const getArtifactDownload = (artifactId: string) =>
  api.getBlob(`artifacts/${artifactId}/download`)
