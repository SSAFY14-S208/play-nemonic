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
export const postFilePresign = (payload: FilePresignRequest) =>
  apiUnwrap(api.post<ApiResponse<FilePresignResponse>>('files/presign', payload))

// POST /files/{fileId}/confirm — 파일 업로드 완료 확인
export const postFileConfirm = (fileId: string) =>
  apiUnwrap(api.post<ApiResponse<FileConfirmResponse>>(`files/${fileId}/confirm`))

// DELETE /files/{fileId} — 파일 삭제
export const deleteFile = (fileId: string) =>
  apiUnwrap(api.delete<ApiResponse<FileDeleteResponse>>(`files/${fileId}`))
