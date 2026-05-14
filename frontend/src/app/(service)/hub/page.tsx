import HubLoader from '@/worlds/hub/HubLoader'
import { HubOverlay } from '@/features/hub'
import { PhoneLauncher } from '@/features/phone'

export default function Page() {
  return (
    <main className="relative min-h-screen overflow-hidden bg-hub-room-background">
      <HubLoader />
      <HubOverlay />
      <PhoneLauncher />
    </main>
  )
}
