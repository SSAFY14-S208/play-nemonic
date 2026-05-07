import HubLoader from '@/worlds/hub/HubLoader'
import { HubOverlay } from '@/features/hub'
import { PhoneLauncher } from '@/features/phone'

export default function Page() {
  return (
    <main className="relative min-h-screen overflow-hidden bg-[#b8e8ff]">
      <HubLoader />
      <HubOverlay />
      <PhoneLauncher />
    </main>
  )
}
