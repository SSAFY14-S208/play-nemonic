import { FlipbookPage } from '@/features/flipbook'

export default function FlipbookLayout({
  children,
}: Readonly<{
  children: React.ReactNode
}>) {
  return (
    <>
      <FlipbookPage />
      {children}
    </>
  )
}
