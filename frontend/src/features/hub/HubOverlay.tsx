'use client'

import Image from 'next/image'
import { useRouter } from 'next/navigation'
import {
  Gamepad2,
  HelpCircle,
  Home,
  LayoutDashboard,
  Music2,
  MousePointerClick,
  Printer,
  Volume2,
  VolumeX,
} from 'lucide-react'
import { cn } from '@/shared/libs'
import { useHubOnboardingStore, useHubRoomStore } from '@/shared/stores'
import type { HubFocusKey } from '@/shared/types'
import { motion } from 'motion/react'
import HubOnboardingTour from './HubOnboardingTour'
import MonitorGameInfoCard from './MonitorGameInfoCard'
import { useHubBgm } from './useHubBgm'
import './HubOverlay.css'

const NEMONIC_ROOM_PATH = '/nemonic'

const FOCUS_BUTTONS: Array<{
  focusKey: HubFocusKey
  icon: typeof Home
  label: string
  iconOnly?: boolean
}> = [
  { focusKey: 'overview', icon: Home, label: '홈', iconOnly: true },
  { focusKey: 'monitor', icon: Gamepad2, label: '게임 선택' },
  { focusKey: 'communityBoard', icon: LayoutDashboard, label: '커뮤니티 보드' },
  { focusKey: 'printer', icon: Printer, label: '네모닉' },
]

const GLASS_BUTTON_BASE =
  'inline-flex items-center justify-center border border-[rgb(255_247_235/74%)] rounded-[0.8rem] bg-[rgb(255_250_242/72%)] shadow-[0_0.8rem_2rem_rgb(54_45_80/12%)] text-[#5b4a82] cursor-pointer backdrop-blur-[16px] transition-[background,color,transform] duration-[160ms] ease-[ease] hover:-translate-y-px active:scale-[0.96]'

export default function HubOverlay({
  disableBgm = false,
}: {
  disableBgm?: boolean
}) {
  const router = useRouter()
  const focusKey = useHubRoomStore((state) => state.focusKey)
  const setFocus = useHubRoomStore((state) => state.setFocus)
  const reopenOnboarding = useHubOnboardingStore(
    (state) => state.reopenOnboarding,
  )
  const { isBgmEnabled, isBgmPlaying, toggleHubBgm } = useHubBgm({
    disabled: disableBgm,
  })
  const BgmIcon = isBgmEnabled ? Volume2 : VolumeX
  const bgmToggleLabel = isBgmEnabled ? '허브 음악 끄기' : '허브 음악 켜기'

  return (
    <>
      <header className="fixed top-6 left-6 z-30 text-[#362d50] pointer-events-none [@media(max-width:860px)]:top-4 [@media(max-width:860px)]:left-4">
        <div className="block w-[8.75rem] drop-shadow-[0_0.55rem_1.15rem_rgb(0_0_0/18%)] [@media(max-width:520px)]:w-[7.5rem]">
          <Image
            src="/images/play-nemonic-logo-v2.png"
            alt="Play! Nemonic"
            width={2716}
            height={1222}
            priority
            draggable={false}
            className="block w-full h-auto select-none"
          />
        </div>
      </header>

      <div className="fixed top-[1.4rem] right-[1.4rem] z-30 inline-flex items-center gap-2 [@media(max-width:860px)]:top-4 [@media(max-width:860px)]:right-4 [@media(max-width:860px)]:gap-[0.4rem]">
        <button
          type="button"
          aria-label="허브 사용법 다시 보기"
          title="허브 사용법 다시 보기"
          className={cn(
            GLASS_BUTTON_BASE,
            'h-[2.72rem] w-[2.72rem] hover:bg-[rgb(255_252_247/88%)]',
            '[@media(max-width:860px)]:h-[2.55rem] [@media(max-width:860px)]:w-[2.55rem]',
          )}
          onClick={reopenOnboarding}
        >
          <HelpCircle className="h-4 w-4" strokeWidth={2.35} />
        </button>
        {!disableBgm && (
          <button
            type="button"
            aria-label={bgmToggleLabel}
            aria-pressed={isBgmEnabled}
            title={`Pastel Puzzle Room · ${bgmToggleLabel}`}
            className={cn(
              GLASS_BUTTON_BASE,
              'h-[2.72rem] w-[4.35rem] gap-[0.34rem]',
              'transition-[background,color,opacity,transform]',
              'hover:bg-[rgb(255_252_247/86%)]',
              '[@media(max-width:860px)]:h-[2.55rem] [@media(max-width:860px)]:w-[3.9rem]',
              !isBgmEnabled && 'bg-[rgb(255_250_242/56%)] text-[#746a85] opacity-[0.82]',
            )}
            onClick={toggleHubBgm}
          >
            <Music2
              className={cn(
                'h-4 w-4 shrink-0 text-[#7d63bc]',
                isBgmPlaying && 'animate-[hubMusicPulse_1.45s_ease-in-out_infinite]',
              )}
              strokeWidth={2.35}
            />
            <BgmIcon className="h-4 w-4" strokeWidth={2.35} />
          </button>
        )}
      </div>

      <nav
        className="fixed left-1/2 bottom-[1.4rem] z-30 flex items-center gap-2 max-w-[calc(100vw-8rem)] overflow-x-auto border border-[rgb(255_247_235/74%)] rounded-[0.95rem] bg-[rgb(255_250_242/76%)] shadow-[0_0.9rem_2.2rem_rgb(54_45_80/14%)] p-[0.48rem] -translate-x-1/2 backdrop-blur-[18px] [@media(max-width:860px)]:bottom-4 [@media(max-width:860px)]:max-w-[calc(100vw-6.5rem)] [@media(max-width:860px)]:rounded-[0.8rem]"
        aria-label="허브 카메라 포커스"
      >
        {FOCUS_BUTTONS.map(({ focusKey: buttonFocusKey, icon: Icon, label, iconOnly }) => {
          const isActive = focusKey === buttonFocusKey

          return (
            <button
              key={buttonFocusKey}
              type="button"
              aria-pressed={isActive}
              aria-label={label}
              className={cn(
                'inline-flex h-[2.35rem] items-center justify-center gap-[0.38rem]',
                'border-0 rounded-[0.68rem] bg-transparent px-[0.78rem]',
                'text-[#5f5578] font-[inherit] text-[0.8rem] font-extrabold cursor-pointer',
                'transition-[background,color,transform] duration-[160ms] ease-[ease]',
                'hover:-translate-y-px hover:bg-[rgb(255_255_255/66%)]',
                '[@media(max-width:520px)]:px-[0.68rem]',
                isActive && [
                  'text-white',
                  'shadow-[0_0.4rem_1rem_rgb(91_77_155/20%),inset_0_1px_0_rgb(255_255_255/34%)]',
                ],
              )}
              style={
                isActive
                  ? { background: 'linear-gradient(135deg, #a98bed, #8ed2ef)' }
                  : undefined
              }
              onClick={() => setFocus(buttonFocusKey)}
            >
              <Icon className="h-4 w-4" strokeWidth={2.35} />
              {!iconOnly && (
                <span className="[@media(max-width:860px)]:hidden">{label}</span>
              )}
            </button>
          )
        })}
      </nav>

      {focusKey === 'printer' && (
        <motion.div
          className="fixed left-1/2 bottom-[5.05rem] z-[32] flex flex-col items-center gap-[0.46rem] pointer-events-none -translate-x-1/2 [@media(max-width:860px)]:bottom-[4.65rem]"
          initial={{ opacity: 0, y: '0.45rem' }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.22, ease: 'easeOut' }}
          aria-live="polite"
        >
          <button
            type="button"
            className="inline-flex min-h-[2.6rem] items-center justify-center gap-[0.44rem] border border-[rgb(255_247_235/82%)] rounded-[0.8rem] bg-[rgb(255_250_242/86%)] shadow-[0_0.75rem_1.8rem_rgb(54_45_80/16%),inset_0_1px_0_rgb(255_255_255/58%)] text-[#4e426c] cursor-pointer font-[inherit] text-[0.82rem] font-black px-[0.85rem] pointer-events-auto backdrop-blur-[18px] transition-[background,color,transform] duration-[160ms] ease-[ease] hover:bg-[rgb(255_252_247/94%)] hover:text-[#312747] hover:-translate-y-px active:scale-[0.97] [@media(max-width:520px)]:min-h-[2.45rem] [@media(max-width:520px)]:px-[0.76rem]"
            onClick={() => router.push(NEMONIC_ROOM_PATH)}
          >
            <MousePointerClick
              className="h-4 w-4 text-[#8e6ce0] animate-[hubPrinterPromptTap_1.28s_ease-in-out_infinite]"
              strokeWidth={2.35}
            />
            <span>네모닉 체험하기</span>
          </button>
          <span
            className="h-[2.25rem] w-px rounded-full opacity-[0.82] animate-[hubPrinterPromptBeam_1.28s_ease-in-out_infinite]"
            style={{
              background:
                'linear-gradient(to top, rgb(142 108 224 / 0), rgb(142 108 224 / 56%), rgb(142 108 224 / 0))',
            }}
            aria-hidden
          />
        </motion.div>
      )}

      <MonitorGameInfoCard />
      <HubOnboardingTour />
    </>
  )
}
