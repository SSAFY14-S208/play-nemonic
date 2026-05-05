import { PHONE_COLORS, PHONE_STATUS_BAR_LAYOUT } from '../constants'

interface PhoneStatusBarProps {
  variant: 'light' | 'dark'
}

export function PhoneStatusBar({ variant }: PhoneStatusBarProps) {
  const isLight = variant === 'light'

  return (
    <div className="pointer-events-none absolute inset-0 z-30">
      <span
        className="phone-status-time absolute"
        style={{
          ...PHONE_STATUS_BAR_LAYOUT.time,
          color: isLight ? PHONE_COLORS.white : PHONE_COLORS.statusDark,
        }}
      >
        9:41
      </span>
      <div
        className="absolute flex items-end justify-between"
        style={PHONE_STATUS_BAR_LAYOUT.signal}
      >
        {[40, 60, 80, 100].map((heightPercent) => (
          <span
            key={heightPercent}
            className="w-[18.2%] rounded-[0.3px]"
            style={{
              background: isLight ? PHONE_COLORS.white : PHONE_COLORS.statusDark,
              height: `${heightPercent}%`,
            }}
          />
        ))}
      </div>
      <div
        className="absolute rounded-[0.36rem]"
        style={{
          ...PHONE_STATUS_BAR_LAYOUT.battery,
          background: isLight ? PHONE_COLORS.white : PHONE_COLORS.statusDark,
        }}
      />
    </div>
  )
}
