import Image from 'next/image'

import { nemonicDrawingLobbyBg } from '@/features/relay-drawing/assets'

export default function RelayRoomBackground() {
  return (
    <>
      <Image
        src={nemonicDrawingLobbyBg}
        alt=""
        fill
        priority
        sizes="100vw"
        className="pointer-events-none object-cover"
        aria-hidden
      />
      <div
        className="absolute inset-0 bg-[radial-gradient(circle_at_52%_40%,rgb(255_255_255/36%),transparent_42%)]"
        aria-hidden
      />
    </>
  )
}
