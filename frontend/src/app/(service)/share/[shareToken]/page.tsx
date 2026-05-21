import { redirect } from 'next/navigation'
import { getShareRedirectPath } from '../shareRedirect'

interface ShareRedirectPageProps {
  params: Promise<{
    shareToken: string
  }>
}

export default async function ShareRedirectPage({ params }: ShareRedirectPageProps) {
  const { shareToken } = await params

  redirect(getShareRedirectPath(shareToken))
}
