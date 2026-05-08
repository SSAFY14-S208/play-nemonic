'use client'

// roomStatus === 'FINALIZING' 동안 표시. 가이드 §4·§24 ALL_PARTS_COMPLETED 수신
// 이후 RESULT_CREATED가 올 때까지의 서버 합성 대기 화면.
export default function RelayFinalizingView() {
  return (
    <section className="grid min-h-screen place-items-center bg-relay-background text-relay-ink">
      <div className="flex flex-col items-center gap-4">
        <span
          aria-hidden
          className="size-10 animate-spin rounded-full border-4 border-relay-line border-t-relay-accent"
        />
        <p className="body-l-r">결과를 만들고 있어요…</p>
      </div>
    </section>
  )
}
