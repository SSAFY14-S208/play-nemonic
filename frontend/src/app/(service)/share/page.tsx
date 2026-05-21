import { redirect } from 'next/navigation'
import {
  getShareRedirectPath,
  getShareTokenFromSearchParams,
  type ShareSearchParams,
} from './shareRedirect'

interface ShareRedirectQueryPageProps {
  searchParams?: Promise<ShareSearchParams>
}

export default async function ShareRedirectQueryPage({
  searchParams,
}: ShareRedirectQueryPageProps) {
  const resolvedSearchParams = searchParams ? await searchParams : {}
  const shareToken = getShareTokenFromSearchParams(resolvedSearchParams)

  redirect(getShareRedirectPath(shareToken))
}
