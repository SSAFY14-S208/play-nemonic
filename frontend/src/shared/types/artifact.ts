// Artifact 도메인 (OpenAPI: tag "Artifact")

export type ArtifactType = 'RELAY' | 'FLIPBOOK' | 'FORTUNE' | 'PHONE'

export interface ArtifactImageUrlResponse {
  artifactId: string
  thumbnailUrl: string
  originalUrl: string | null
  gifUrl: string | null
  type: ArtifactType
}

export interface ArtifactShareResponse {
  shareUrl: string
  imageUrl: string
}
