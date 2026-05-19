import { PhoneLauncher } from '@/features/phone'
import { WorldHomeLink } from '@/shared/components'
import LandingLoader from '@/worlds/landing/LandingLoader'

export default function Page() {
  return (
    <>
      <WorldHomeLink />
      <LandingLoader />
      <PhoneLauncher />
    </>
  )
}
