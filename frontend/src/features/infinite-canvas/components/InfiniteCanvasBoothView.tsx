'use client'

import Image from 'next/image'
import { useState } from 'react'

import { INFINITE_CANVAS_COLOR_OPTIONS } from '../constants'
import InfiniteCanvasActionButton from './InfiniteCanvasActionButton'
import InfiniteCanvasColorPicker from './InfiniteCanvasColorPicker'

const DEFAULT_SELECTED_COLOR = INFINITE_CANVAS_COLOR_OPTIONS[4].value

export default function InfiniteCanvasBoothView() {
  const [selectedColor, setSelectedColor] = useState(DEFAULT_SELECTED_COLOR)

  return (
    <main className="infinite-canvas-page">
      <Image
        src="/images/infinite-canvas/background.png"
        alt=""
        aria-hidden
        fill
        priority
        draggable={false}
        sizes="100vw"
        className="infinite-canvas-page__background"
      />
      <div className="infinite-canvas-page__shade" aria-hidden />
      <section className="infinite-canvas-shell" aria-labelledby="infinite-canvas-title">
        <h1 id="infinite-canvas-title" className="sr-only">
          무한 캔버스
        </h1>

        <div className="infinite-canvas-logo-wrap">
          <Image
            src="/images/infinite-canvas/logo.png"
            alt="무한 캔버스"
            width={1885}
            height={656}
            priority
            draggable={false}
            className="infinite-canvas-logo"
          />
        </div>

        <div className="infinite-canvas-card">
          <div className="infinite-canvas-card__shine" aria-hidden />
          <p className="infinite-canvas-card__label">색 고르기</p>

          <div className="infinite-canvas-palette-wrap">
            <Image
              src="/images/infinite-canvas/palette.png"
              alt=""
              aria-hidden
              width={2080}
              height={756}
              draggable={false}
              className="infinite-canvas-palette"
            />
            <InfiniteCanvasColorPicker
              options={INFINITE_CANVAS_COLOR_OPTIONS}
              selectedColor={selectedColor}
              onSelectColor={setSelectedColor}
            />
          </div>

          <div className="infinite-canvas-actions">
            <InfiniteCanvasActionButton
              imageSrc="/images/infinite-canvas/enter-room-button.png"
              label="초대코드로 입장하기"
              onClick={() => undefined}
            />
            <InfiniteCanvasActionButton
              imageSrc="/images/infinite-canvas/create-card.png"
              label="방 만들기"
              onClick={() => undefined}
            />
          </div>
        </div>
      </section>
    </main>
  )
}
