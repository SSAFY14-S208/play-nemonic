import Image from 'next/image'

interface FlipbookBoothViewProps {
  onCreateRoom: () => void
  onEnterRoom: () => void
}

export default function FlipbookBoothView({
  onCreateRoom,
  onEnterRoom,
}: FlipbookBoothViewProps) {
  return (
    <section className="relative min-h-[900px] overflow-hidden bg-flipbook-background">
      <div className="relative mx-auto h-[900px] w-full max-w-[1440px] overflow-hidden">
        <div className="absolute left-[7.3%] top-[22.8%] h-[51.7%] w-[36.6%] rotate-[-2deg] rounded-[6px] bg-flipbook-light shadow-[0_10px_18px_var(--color-flipbook-shadow)]">
          <span className="absolute bottom-0 right-0 h-[28%] w-[34%] rounded-tl-[90px] bg-flipbook-primary/45" />
        </div>

        <div className="absolute left-[9.7%] top-[28.6%] w-[33%]">
          <span className="body-b inline-flex min-h-[43px] items-center rounded-full bg-flipbook-result-soft px-5 text-flipbook-deep">
            2~12명
          </span>
          <h1
            className="mt-4 whitespace-nowrap text-flipbook-ink"
            style={{ fontSize: '49px', fontWeight: 700, lineHeight: '78px' }}
          >
            플립북
          </h1>
          <div className="body-l-r mt-6 text-flipbook-muted">
            <p>주어진 주제로 한 명씩 한 페이지를 그리고</p>
            <p>다음 사람에게 넘겨요. 마지막엔 책장이</p>
            <p>넘어가는 GIF가 완성됩니다.</p>
          </div>
          <div className="mt-11 flex gap-3">
            <button
              type="button"
              onClick={onCreateRoom}
              className="body-b min-h-[56px] rounded-[16px] bg-flipbook-primary px-8 text-flipbook-ink shadow-[0_6px_16px_var(--color-flipbook-shadow)]"
            >
              방 만들기 →
            </button>
            <button
              type="button"
              onClick={onEnterRoom}
              className="body-b min-h-[56px] rounded-[16px] border-2 border-flipbook-light bg-flipbook-paper px-7 text-flipbook-primary shadow-[0_6px_16px_var(--color-flipbook-shadow)]"
            >
              방 입장
            </button>
          </div>
        </div>

        <Image
          src="/images/flipbook-hero.png"
          alt="검은 플립북 책장 사이로 분홍 토끼들이 뛰어가는 디오라마"
          width={1056}
          height={693}
          priority
          className="absolute left-[37.3%] top-[12.9%] h-[77%] w-[73.3%] object-contain"
        />

      </div>
    </section>
  )
}
