import { api } from '@/shared/libs'
import type {
  ApiResponse,
  FileConfirmResponse,
  FileDeleteResponse,
  FilePresignedUploadRequest,
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

// PUT {presignedUrl} — MinIO Presigned URL로 파일 바이너리 직접 업로드
export const putFileToPresignedUrl = async ({
  presignedUrl,
  file,
  contentType,
}: FilePresignedUploadRequest) => {
  const response = await fetch(presignedUrl, {
    method: 'PUT',
    headers: {
      'Content-Type': contentType,
    },
    body: file,
  })

  if (!response.ok) {
    const errorText = await response.text().catch(() => '')
    const uploadErrorDetail = errorText ? ` ${errorText.slice(0, 200)}` : ''

    throw new Error(`파일 업로드에 실패했습니다. (${response.status})${uploadErrorDetail}`)
  }
}

// DELETE /files/{fileId} — 파일 삭제
export const deleteFile = (fileId: string) =>
  apiUnwrap(api.delete<ApiResponse<FileDeleteResponse>>(`files/${fileId}`))
