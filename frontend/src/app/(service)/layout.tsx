import { WorldHomeLink } from '@/shared/components'

export default function ServiceLayout({
  children,
}: Readonly<{
  children: React.ReactNode
}>) {
  return (
    <>
      <WorldHomeLink />
      {children}
    </>
  )
}
