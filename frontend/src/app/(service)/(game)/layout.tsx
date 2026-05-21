export default function GameLayout({
  children,
}: Readonly<{
  children: React.ReactNode
}>) {
  return <div className="min-h-screen bg-relay-background text-relay-ink">{children}</div>
}
