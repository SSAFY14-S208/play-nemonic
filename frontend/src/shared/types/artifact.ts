// Artifact 도메인 (OpenAPI: tag "Artifact")

export type ArtifactType =
  | 'fortune'
  | 'relay_drawing'
  | 'flipbook'
  | 'infinite_canvas'
  | 'phone'
  | 'community_memo'

export interface ArtifactContentUrlResponse {
  type: string
  url: string
}

export interface ArtifactImageUrlResponse {
  artifactId: string
  kind: ArtifactType | string
  thumbnailUrl: string | null
  contents: ArtifactContentUrlResponse[]
}

export interface ArtifactShareResponse {
  shareUrl: string
  imageUrl: string
}
