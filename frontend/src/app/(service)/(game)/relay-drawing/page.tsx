import {
  RELAY_LEAVE_CANCEL_BUTTON_CLASS,
  RELAY_LEAVE_CONFIRM_BUTTON_CLASS,
  RelayDrawingPage,
} from '@/features/relay-drawing'
import { WorldHomeLink } from '@/shared/components'

export default function Page() {
  return (
    <>
      <WorldHomeLink
        leaveConfirmCancelButtonClassName={RELAY_LEAVE_CANCEL_BUTTON_CLASS}
        leaveConfirmConfirmButtonClassName={RELAY_LEAVE_CONFIRM_BUTTON_CLASS}
      />
      <RelayDrawingPage />
    </>
  )
}
