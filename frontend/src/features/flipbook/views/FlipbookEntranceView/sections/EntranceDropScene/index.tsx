import Image from 'next/image'
import { motion } from 'motion/react'

import { DROP_LAYERS, DROP_SPRING_TRANSITION, FLIPBOOK_SCENE_IMAGES } from '../../constants'

interface EntranceDropSceneProps {
  shouldInstantCompleteIntro: boolean
}

export function EntranceDropScene({ shouldInstantCompleteIntro }: EntranceDropSceneProps) {
  return (
    <div aria-hidden className="pointer-events-none absolute inset-0">
      {DROP_LAYERS.map((layer) => (
        <motion.div
          key={`${layer.key}-${shouldInstantCompleteIntro ? 'done' : 'drop'}`}
          className={`absolute overflow-hidden ${layer.className}`}
          initial={{
            opacity: 0,
            y: -layer.fallDistance,
            scale: 0.98,
          }}
          animate={
            shouldInstantCompleteIntro
              ? {
                  opacity: 1,
                  y: 0,
                  scale: 1,
                  transition: { duration: 0 },
                }
              : {
                  opacity: 1,
                  y: 0,
                  scale: 1,
                  transition: {
                    y: {
                      ...DROP_SPRING_TRANSITION,
                      delay: layer.delay,
                    },
                    opacity: {
                      delay: layer.delay,
                      duration: 0.18,
                      ease: 'easeOut',
                    },
                    scale: {
                      delay: layer.delay,
                      duration: 0.36,
                      ease: [0.22, 0.8, 0.22, 1],
                    },
                  },
                }
          }
        >
          <div
            className="absolute inset-0"
            style={{ rotate: `${layer.rotate}deg` }}
          >
            <Image
              src={FLIPBOOK_SCENE_IMAGES.furnitureSprite}
              alt=""
              width={1536}
              height={1024}
              loading="eager"
              sizes="100vw"
              className={`absolute max-w-none ${layer.imageClassName}`}
            />
          </div>
        </motion.div>
      ))}
    </div>
  )
}
