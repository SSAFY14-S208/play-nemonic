import type { RelayWsEvent, RelayWsEventType } from '@/shared/types'

export type RelayEventHandler<TType extends RelayWsEventType> = (
  event: Extract<RelayWsEvent, { type: TType }>,
) => void

export type RelayEventHandlers = {
  [TType in RelayWsEventType]?: RelayEventHandler<TType>
}
