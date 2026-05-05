import { cn } from '@/shared/libs'
import { PHONE_STATUS_BAR_LAYOUT } from '../constants'

interface PhoneStatusBarProps {
  variant: 'light' | 'dark'
}

export function PhoneStatusBar({ variant }: PhoneStatusBarProps) {
  const isLight = variant === 'light'

  return (
    <div className="pointer-events-none absolute inset-0 z-30">
      <span
        className={cn(
          'body-l-b absolute text-[0.98rem] leading-none',
          isLight ? 'text-white' : 'text-[#010101]',
        )}
        style={PHONE_STATUS_BAR_LAYOUT.time}
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
            className={cn(
              'w-[18.2%] rounded-[0.3px]',
              isLight ? 'bg-white' : 'bg-[#010101]',
            )}
            style={{ height: `${heightPercent}%` }}
          />
        ))}
      </div>
      <div
        className={cn(
          'absolute rounded-[0.36rem]',
          isLight ? 'bg-white' : 'bg-[#010101]',
        )}
        style={PHONE_STATUS_BAR_LAYOUT.battery}
      />
    </div>
  )
}
