export type HubGameId = 'fortune-memo' | 'flipbook' | 'relay-drawing'

export interface HubGame {
  accentColor: string
  description: string
  id: HubGameId
  route: string
  title: string
}
