export type HubGameId =
  | 'fortune-memo'
  | 'flipbook'
  | 'relay-drawing'
  | 'infinite-canvas'

export interface HubGame {
  accentColor: string
  description: string
  id: HubGameId
  route: string
  title: string
}
