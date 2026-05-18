import { FlipbookPage } from '@/features/flipbook'

export default function FlipbookLayout({
  children,
}: Readonly<{
  children: React.ReactNode
}>) {
  return (
    <div data-flipbook-primary-scope="true" className="min-h-screen">
      <FlipbookPage />
      {children}
    </div>
  )
}
