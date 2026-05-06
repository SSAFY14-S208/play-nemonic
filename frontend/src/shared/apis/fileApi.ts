import { api } from '@/shared/libs'
import type {
  ApiResponse,
  FileConfirmResponse,
  FileDeleteResponse,
  FilePresignRequest,
  FilePresignResponse,
} from '@/shared/types'

import { apiUnwrap } from '@/shared/utils'

// POST /files/presign — 이미지 업로드 Presigned URL 발급
export const postFilePresign = (userUuid: string, payload: FilePresignRequest) =>
  apiUnwrap(
    api.post<ApiResponse<FilePresignResponse>>('files/presign', {
      ...payload,
      userUuid,
    }),
  )

// POST /files/{fileId}/confirm — 파일 업로드 완료 확인
export const postFileConfirm = (fileId: string, userUuid: string) =>
  apiUnwrap(
    api.post<ApiResponse<FileConfirmResponse>>(`files/${fileId}/confirm`, { userUuid }),
  )

// DELETE /files/{fileId} — 파일 삭제
export const deleteFile = (fileId: string, userUuid: string) =>
  apiUnwrap(api.delete<ApiResponse<FileDeleteResponse>>(`files/${fileId}`, { userUuid }))
