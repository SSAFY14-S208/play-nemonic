export default function RoomLoadingState() {
  return (
    <section className="font-paperlogy grid min-h-screen place-items-center bg-relay-background text-relay-ink">
      <div className="flex flex-col items-center gap-4">
        <span
          aria-hidden
          className="size-10 animate-spin rounded-full border-4 border-relay-line border-t-relay-accent"
        />
        <p className="body-l-r">방 정보를 불러오는 중입니다.</p>
      </div>
    </section>
  )
}
