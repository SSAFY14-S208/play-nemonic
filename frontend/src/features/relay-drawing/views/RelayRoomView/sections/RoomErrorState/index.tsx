import { RelayButton } from '@/features/relay-drawing/components'

interface RoomErrorStateProps {
  message: string
  onGoHome: () => void
}

export default function RoomErrorState({
  message,
  onGoHome,
}: RoomErrorStateProps) {
  return (
    <section className="font-paperlogy grid min-h-screen place-items-center bg-relay-background text-relay-ink">
      <div className="flex max-w-sm flex-col items-center gap-4 text-center">
        <p className="body-l-r">{message}</p>
        <RelayButton onClick={onGoHome}>돌아가기</RelayButton>
      </div>
    </section>
  )
}
