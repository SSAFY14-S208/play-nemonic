import type { ReactNode } from 'react'
import Image from 'next/image'
import {
  phoneDeviceFrame,
  phoneScreen,
  phoneSpeakerCamera,
} from '@/shared/assets'
import { PHONE_COLORS, PHONE_FRAME_LAYOUT } from '../constants'
import { PhoneStatusBar } from './PhoneStatusBar'

interface PhoneFrameProps {
  children: ReactNode
  statusBarVariant: 'light' | 'dark'
}

export function PhoneFrame({ children, statusBarVariant }: PhoneFrameProps) {
  return (
    <div
      className="relative"
      style={{
        aspectRatio: PHONE_FRAME_LAYOUT.aspectRatio,
        width: PHONE_FRAME_LAYOUT.deviceMaxWidth,
      }}
    >
      <div
        className="pointer-events-none absolute"
        style={PHONE_FRAME_LAYOUT.deviceFrame}
      >
        <Image
          src={phoneDeviceFrame}
          alt=""
          aria-hidden
          fill
          priority
          className="object-fill"
          sizes="393px"
        />
      </div>

      <div
        className="pointer-events-none absolute"
        style={PHONE_FRAME_LAYOUT.screen}
      >
        <Image
          src={phoneScreen}
          alt=""
          aria-hidden
          fill
          priority
          className="object-fill"
          sizes="393px"
        />
      </div>

      <div
        className="absolute overflow-hidden bg-surface-default"
        style={PHONE_FRAME_LAYOUT.display}
      >
        {children}
      </div>

      <div
        className="pointer-events-none absolute z-20"
        style={PHONE_FRAME_LAYOUT.speakerCamera}
      >
        <Image
          src={phoneSpeakerCamera}
          alt=""
          aria-hidden
          fill
          priority
          className="object-fill"
          sizes="160px"
        />
      </div>

      <PhoneStatusBar variant={statusBarVariant} />

      <div
        className="pointer-events-none absolute z-20 rounded-full"
        style={{
          ...PHONE_FRAME_LAYOUT.homeIndicator,
          background: PHONE_COLORS.screenHandle,
        }}
      />
    </div>
  )
}
