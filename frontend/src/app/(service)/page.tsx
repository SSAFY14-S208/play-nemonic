import { redirect } from 'next/navigation'
import { HubOverlay } from '@/features/hub'
import { PhoneLauncher } from '@/features/phone'
import HubLoader from '@/worlds/hub/HubLoader'
import {
  getShareRedirectPath,
  getShareTokenFromSearchParams,
  type ShareSearchParams,
} from './share/shareRedirect'

interface ServiceHomePageProps {
  searchParams?: Promise<ShareSearchParams>
}

export default async function Page({ searchParams }: ServiceHomePageProps) {
  const resolvedSearchParams = searchParams ? await searchParams : {}
  const shareToken = getShareTokenFromSearchParams(resolvedSearchParams)

  if (shareToken) {
    redirect(getShareRedirectPath(shareToken))
  }

  return (
    <main className="relative min-h-screen overflow-hidden bg-hub-room-background">
      <HubLoader />
      <HubOverlay />
      <PhoneLauncher />
    </main>
  )
}
