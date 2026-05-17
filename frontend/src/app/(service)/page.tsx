import { redirect } from 'next/navigation'
import { PhoneLauncher } from '@/features/phone'
import LandingLoader from '@/worlds/landing/LandingLoader'
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
    <>
      <LandingLoader />
      <PhoneLauncher />
    </>
  )
}
