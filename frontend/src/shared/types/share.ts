// Share 도메인 (OpenAPI: tag "Share")

export interface ShareCreateRequest {
  galleryId: string
  campaign: string
}

export interface ShareCreateResponse {
  shareToken: string
  imageUrl: string
  siteUrl: string
  kakaoUrl: string
  instagramUrl: string
}
