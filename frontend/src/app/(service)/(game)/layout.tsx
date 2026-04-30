import { GameHeader } from '@/shared/layouts'

export default function GameLayout({
  children,
}: Readonly<{
  children: React.ReactNode
}>) {
  return (
    <div className="min-h-screen bg-relay-background text-relay-ink">
      <GameHeader />
      {children}
    </div>
  )
}
