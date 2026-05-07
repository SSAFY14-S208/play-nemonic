// File 도메인 (OpenAPI: tag "File")

export interface FilePresignRequest {
  fileName: string
  contentType: string
  purpose: string
  byteSize?: number
}

export interface FilePresignResponse {
  fileId: string
  presignedUrl: string
  expiresIn: number
}

export interface FileConfirmResponse {
  fileId: string
  status: string
}

export interface FileDeleteResponse {
  fileId: string
  status: string
}
