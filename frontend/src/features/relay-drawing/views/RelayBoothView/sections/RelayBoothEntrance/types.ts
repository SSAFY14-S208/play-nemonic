export type ChoreographyPhase =
  | 'intro-1'
  | 'intro-2'
  | 'intro-3'
  | 'settling'
  | 'fanning'

export interface FanTarget {
  rotate: number
  x: number
}

export interface Measurement {
  centerOffset: { x: number; y: number }
  introScale: number
  fanRight: FanTarget
  fanLeft: FanTarget
  partWidth: number
  partHeight: number
}
