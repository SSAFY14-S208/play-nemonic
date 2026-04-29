import HubLoader from '@/worlds/hub/HubLoader'
import { HubOverlay } from '@/features/hub'

export default function Page() {
  return (
    <main className="relative min-h-screen overflow-hidden bg-[linear-gradient(180deg,#111329_0%,#1b1a3b_48%,#33284f_76%,#493962_100%)]">
      <div className="pointer-events-none absolute inset-[16%_12%_14%] z-0 rounded-full bg-[radial-gradient(circle_at_center,rgba(255,244,226,0.42),rgba(255,244,226,0)_64%)] blur-[28px] max-[800px]:inset-[24%_2%_16%]" />
      <HubLoader />
      <HubOverlay />
    </main>
  )
}
