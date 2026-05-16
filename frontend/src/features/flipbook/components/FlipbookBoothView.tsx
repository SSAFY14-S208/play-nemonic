'use client'

import Image from 'next/image'
import { PostItNote } from '@/shared/components'

interface FlipbookBoothViewProps {
  onCreateRoom: () => void
  onEnterRoom: () => void
}

export default function FlipbookBoothView({
  onCreateRoom,
  onEnterRoom,
}: FlipbookBoothViewProps) {
  return (
    <section className="relative min-h-screen overflow-hidden bg-flipbook-background px-4 py-10 sm:px-6 lg:p-0">
      <div className="relative mx-auto grid min-h-[calc(100svh-5rem)] w-full max-w-[1440px] items-center gap-8 lg:block lg:h-[900px] lg:min-h-0 lg:overflow-hidden">
        <PostItNote className="pointer-events-none absolute left-[5.8%] top-[18%] hidden h-[61.5%] w-[40.3%] text-flipbook-primary lg:block" />

        <div className="relative z-10 w-full rounded-[24px] bg-flipbook-deep/92 p-6 shadow-[0_12px_28px_var(--color-flipbook-shadow)] sm:p-8 lg:absolute lg:left-[9.7%] lg:top-[28.6%] lg:w-[33%] lg:rounded-none lg:bg-transparent lg:p-0 lg:shadow-none">
          <span className="body-b inline-flex min-h-[43px] items-center rounded-full bg-flipbook-result-soft px-5 text-flipbook-deep">
            2~12명
          </span>
          <h1 className="h1-b mt-4 text-fg-inverse">
            플립북
          </h1>
          <div className="body-l-r mt-6 text-fg-inverse/80">
            <p>주어진 주제로 한 명씩 한 페이지를 그리고</p>
            <p>다음 사람에게 넘겨요. 마지막엔 책장이</p>
            <p>넘어가는 GIF가 완성됩니다.</p>
          </div>
          <div className="mt-8 flex flex-col gap-3 sm:flex-row lg:mt-11">
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
          className="relative z-0 mx-auto h-auto w-full max-w-[720px] object-contain lg:absolute lg:left-[41.5%] lg:top-[13.5%] lg:h-[76%] lg:w-[68%] lg:max-w-none"
        />
      </div>
    </section>
  )
}
