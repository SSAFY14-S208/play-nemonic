import { PhoneLauncher } from '@/features/phone'

export default function ServiceLayout({
  children,
}: Readonly<{
  children: React.ReactNode
}>) {
  return (
    <>
      {children}
      <PhoneLauncher />
    </>
  )
}
