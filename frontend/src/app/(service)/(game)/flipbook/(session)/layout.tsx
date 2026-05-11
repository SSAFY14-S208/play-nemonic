import { FlipbookPage } from '@/features/flipbook'

export default function FlipbookSessionLayout({
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
