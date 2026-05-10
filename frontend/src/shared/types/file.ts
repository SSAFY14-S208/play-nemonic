// File 도메인 (OpenAPI: tag "File")

export type FilePurpose = 'FLIPBOOK' | 'PHONE'

export type FileUploadStatus = 'UPLOADED'

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
}

export interface FileDeleteResponse {
  fileId: string
  status: string
}

export interface FilePresignedUploadRequest {
  presignedUrl: string
  file: Blob
  contentType: string
}
