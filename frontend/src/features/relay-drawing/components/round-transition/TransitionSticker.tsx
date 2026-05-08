'use client'

import Image from 'next/image'
import { motion } from 'motion/react'
import { RELAY_ROUND_RULES, type RelayRoundKey } from '../../constants'

const FRAME_HEIGHT = 640
const SLOT_HEIGHT = FRAME_HEIGHT / 3

const STICKER_BG = '#FFF3C4'

/** 물결 모양 clip-path — 상단을 ~ 형태로 잘라 라벨지 느낌 */
const WAVE_CLIP_PATH = [
  '0% 16%',
  '4% 8%',
  '8% 13%',
  '12% 4%',
  '17% 10%',
  '22% 2%',
  '27% 8%',
  '32% 0%',
  '37% 6%',
  '42% 12%',
  '47% 3%',
  '52% 9%',
  '57% 1%',
  '62% 7%',
  '67% 13%',
  '72% 4%',
  '77% 9%',
  '82% 1%',
  '87% 7%',
  '92% 14%',
  '97% 5%',
  '100% 10%',
  '100% 100%',
  '0% 100%',
].join(', ')

interface TransitionStickerProps {
  roundKey: RelayRoundKey
  imageUrl: string
  isLanding: boolean
  slotIndex: number
}

export default function TransitionSticker({
  roundKey,
  imageUrl,
  isLanding,
  slotIndex,
}: TransitionStickerProps) {
  const roundRule = RELAY_ROUND_RULES[roundKey]
  const roundLabel = roundRule.label

  const slotCenterY = slotIndex * SLOT_HEIGHT + SLOT_HEIGHT / 2
  const frameCenterY = FRAME_HEIGHT / 2
  const centerOffsetY = frameCenterY - slotCenterY

  return (
    <div style={{ perspective: 800 }} className="relative w-full">
      <motion.div
        className="relative w-full"
        initial={isLanding ? {
          scale: 2.2,
          y: centerOffsetY,
          opacity: 0,
          rotateX: -30,
          rotateZ: 3,
        } : false}
        animate={{ scale: 1, y: 0, opacity: 1, rotateX: 0, rotateZ: 0 }}
        transition={{
          scale: { type: 'spring', stiffness: 160, damping: 18, mass: 1.2 },
          y: { type: 'spring', stiffness: 160, damping: 18, mass: 1.2 },
          rotateX: { type: 'spring', stiffness: 180, damping: 14 },
          rotateZ: { type: 'spring', stiffness: 120, damping: 12 },
          opacity: { duration: 0.15 },
        }}
        style={{ transformOrigin: 'center bottom' }}
      >
        {/* 라벨 탭 */}
        <div
          className="rounded-t-[10px] px-3 py-1.5 caption-b"
          style={{ backgroundColor: '#F6D55C', color: '#5C4813' }}
        >
          {roundLabel}
        </div>

        {/* 이미지 영역 — 라벨지 배경 */}
        <div
          className="relative overflow-hidden rounded-b-[10px] shadow-md"
          style={{ backgroundColor: STICKER_BG }}
        >
          <Image
            src={imageUrl}
            alt=""
            width={848}
            height={720}
            unoptimized
            className="w-full blur-[8px]"
          />
          {roundRule.outgoingHintArea && (
            <div
              className="absolute bottom-0 left-0 right-0 overflow-hidden"
              style={{ height: '16.67%' }}
            >
              <Image
                src={imageUrl}
                alt=""
                width={848}
                height={720}
                unoptimized
                className="absolute bottom-0 left-0 w-full"
                style={{ height: '600%' }}
              />
            </div>
          )}
        </div>

        {/* 착지 후 하단 펄럭임 — skew + rotate 조합 */}
        {isLanding && (
          <div
            className="absolute bottom-0 left-0 right-0"
            style={{ height: '80%', perspective: 500, transformStyle: 'preserve-3d' }}
          >
            <motion.div
              className="h-full w-full overflow-hidden rounded-b-[10px]"
              style={{
                transformOrigin: '30% 0%',
                clipPath: `polygon(${WAVE_CLIP_PATH})`,
                boxShadow: '0 6px 20px rgba(0,0,0,0.12), 4px 4px 12px rgba(0,0,0,0.08)',
              }}
              initial={{ skewX: 0, skewY: 0, scaleX: 1, rotateY: 0, rotateZ: 0 }}
              animate={{
                skewX: [0, -8, 5, -3, 1.5, 0],
                skewY: [0, 3, -2, 1, 0],
                scaleX: [1, 0.92, 1.04, 0.97, 1],
                rotateY: [0, 12, -8, 4, -1.5, 0],
                rotateZ: [0, 2.5, -1.8, 0.8, 0],
              }}
              transition={{
                skewX: { delay: 0.3, duration: 1.1, ease: [0.22, 1, 0.36, 1] },
                skewY: { delay: 0.35, duration: 1.0, ease: [0.22, 1, 0.36, 1] },
                scaleX: { delay: 0.32, duration: 1.0, ease: [0.22, 1, 0.36, 1] },
                rotateY: { delay: 0.35, duration: 1.0, ease: [0.22, 1, 0.36, 1] },
                rotateZ: { delay: 0.38, duration: 0.85, ease: 'easeOut' },
              }}
            >
              <Image
                src={imageUrl}
                alt=""
                width={848}
                height={720}
                unoptimized
                className="absolute bottom-0 left-0 w-full blur-[8px]"
                style={{ height: '125%' }}
              />
              {roundRule.outgoingHintArea && (
                <div
                  className="absolute bottom-0 left-0 right-0 overflow-hidden"
                  style={{ height: '20.84%' }}
                >
                  <Image
                    src={imageUrl}
                    alt=""
                    width={848}
                    height={720}
                    unoptimized
                    className="absolute bottom-0 left-0 w-full"
                    style={{ height: '600%' }}
                  />
                </div>
              )}
            </motion.div>
          </div>
        )}
      </motion.div>
    </div>
  )
}
