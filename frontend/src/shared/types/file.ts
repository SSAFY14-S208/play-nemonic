// File 도메인 (OpenAPI: tag "File")

export type FilePurpose =
  | 'RELAY_DRAWING'
  | 'FLIPBOOK'
  | 'PHONE_DRAWING'
  | 'COMMUNITY_MEMO_ORIGINAL'
  | 'COMMUNITY_MEMO_THUMBNAIL'

export type FileUploadStatus = 'PENDING' | 'UPLOADED' | 'FAILED'

export interface FilePresignRequest {
  fileName: string
  contentType: string
  purpose: FilePurpose
  byteSize: number
}

export interface FilePresignResponse {
  fileId: string
  presignedUrl: string
  expiresIn: number
}

export interface FileConfirmResponse {
  fileId: string
  status: FileUploadStatus
  uploadedAt: string
}

export interface FileViewUrlResponse {
  fileId: string
  viewUrl: string
  expiresIn: number
}

export interface FileDeleteResponse {
  fileId: string
  deletedAt: string
}

export interface FilePresignedUploadRequest {
  presignedUrl: string
  file: Blob
  contentType: string
}
