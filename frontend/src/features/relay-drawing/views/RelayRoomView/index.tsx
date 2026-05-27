'use client'

import { WorldHomeLink } from '@/shared/components'

import {
  RelayDismissalModal,
  RelayFloatingControls,
} from '@/features/relay-drawing/components'
import {
  RELAY_LEAVE_CANCEL_BUTTON_CLASS,
  RELAY_LEAVE_CONFIRM_BUTTON_CLASS,
} from '@/features/relay-drawing/constants'
import { useRelayRoomViewState } from './hooks'
import GameStartOverlay from './sections/GameStartOverlay'
import RelayRoomBackground from './sections/RelayRoomBackground'
import RelayRoomStatusSection from './sections/RelayRoomStatusSection'
import RoomErrorState from './sections/RoomErrorState'
import RoomLoadingState from './sections/RoomLoadingState'

export default function RelayRoomView() {
  const roomViewState = useRelayRoomViewState()

  if (roomViewState.isHydrating) return <RoomLoadingState />

  if (roomViewState.hydrationError) {
    return (
      <RoomErrorState
        message={roomViewState.hydrationError}
        onGoHome={roomViewState.handleGoRelayDrawingHome}
      />
    )
  }

  return (
    <div className="font-paperlogy relative min-h-screen overflow-x-hidden bg-relay-background">
      <RelayRoomBackground />

      {roomViewState.showWorldHomeLink && (
        <WorldHomeLink
          leaveConfirmCancelButtonClassName={RELAY_LEAVE_CANCEL_BUTTON_CLASS}
          leaveConfirmConfirmButtonClassName={RELAY_LEAVE_CONFIRM_BUTTON_CLASS}
        />
      )}

      <RelayRoomStatusSection
        roomStatus={roomViewState.roomStatus}
        gameStartPhase={roomViewState.gameStartPhase}
      />

      <GameStartOverlay
        gameStartPhase={roomViewState.gameStartPhase}
        onImageShown={roomViewState.handleGameStartImageShown}
      />

      {roomViewState.showFloatingControls && (
        <RelayFloatingControls className="hidden lg:flex" />
      )}

      {roomViewState.dismissalReason && (
        <RelayDismissalModal
          reason={roomViewState.dismissalReason}
          onConfirm={roomViewState.handleDismissalConfirm}
        />
      )}
    </div>
  )
}
