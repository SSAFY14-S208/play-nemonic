import { cn } from '@/shared/libs'
import { PHONE_COLORS } from '../constants'

interface PhoneStatusBarProps {
  variant: 'light' | 'dark'
}

export function PhoneStatusBar({ variant }: PhoneStatusBarProps) {
  const isLight = variant === 'light'

  return (
    <div
      className={cn(
        'pointer-events-none absolute inset-x-0 top-0 z-20 flex h-12 items-center justify-between px-7 pt-1',
        isLight ? 'text-white' : 'text-fg-primary',
      )}
    >
      <span className="body-b">9:41</span>
      <div
        className="absolute left-1/2 top-[0.62rem] h-7 w-[6.875rem] -translate-x-1/2 rounded-b-[1.2rem]"
        style={{ background: PHONE_COLORS.black }}
      >
        <span
          className="absolute left-1/2 top-2 h-3 w-3 -translate-x-1/2 rounded-full border border-[#324a72] shadow-[inset_0_0_0_2px_rgba(80,122,188,0.5)]"
          style={{ background: PHONE_COLORS.cameraLens }}
        />
      </div>
      <div className="flex items-center gap-1.5">
        <div className="flex h-3 items-end gap-0.5">
          {[4, 6, 8, 10].map((height) => (
            <span
              key={height}
              className={cn(
                'w-1 rounded-[1px]',
                isLight ? 'bg-white' : 'bg-fg-primary',
              )}
              style={{ height }}
            />
          ))}
        </div>
        <span
          className={cn(
            'h-3 w-5 rounded-[0.25rem]',
            isLight ? 'bg-white' : 'bg-fg-primary',
          )}
        />
      </div>
    </div>
  )
}
