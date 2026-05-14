export type PhoneScreenKey = 'home' | 'drawing' | 'gallery'

export type PhoneDrawingToolKey = 'pen' | 'eraser'

export type PhoneDrawingAction = 'save' | 'print'

export type PhoneGalleryItemKind =
  | 'phone'
  | 'fortune'
  | 'flipbook'
  | 'relay'
  | 'infinite'

export type PhoneGalleryFilterKey = 'all' | PhoneGalleryItemKind

export interface PhoneGalleryItem {
  id: string
  kind: PhoneGalleryItemKind
  title: string
  createdAtLabel: string
  badgeLabel?: string
  contributorLabel?: string
  imageDataUrl?: string
  isNew?: boolean
}

export interface PhoneDrawLine {
  id: string
  color: string
  strokeWidth: number
  points: number[]
  tool: PhoneDrawingToolKey
}
