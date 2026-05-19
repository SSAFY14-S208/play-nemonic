export type HubGameId =
  | 'fortune-memo'
  | 'flipbook'
  | 'relay-drawing'
  | 'infinite-canvas'

export type HubGameLightingId = HubGameId | 'community-canvas'

export interface HubGame {
  accentColor: string
  description: string
  id: HubGameId
  lightingColor: string
  route: string
  title: string
}
