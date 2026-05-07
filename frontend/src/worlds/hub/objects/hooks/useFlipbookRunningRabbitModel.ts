import { useEffect, useMemo } from 'react'
import { useAnimations, useGLTF } from '@react-three/drei'
import { clone as cloneSkeleton } from 'three/examples/jsm/utils/SkeletonUtils.js'
import {
  LoopRepeat,
} from 'three'
import { HUB_FLIPBOOK_RUNNING_RABBIT_HEIGHT } from '../../constants'
import {
  disableMeshShadows,
  isAnimationAction,
  isMesh,
  prepareScaledGltfModel,
} from '../../utils'

const FLIPBOOK_RUNNING_RABBIT_MODEL_URL = '/models/runrabbit.glb'
const RABBIT_ANIMATION_FADE_SECONDS = 0.12
const RABBIT_RUN_SPEED = 1.25

export function useFlipbookRunningRabbitModel() {
  const gltf = useGLTF(FLIPBOOK_RUNNING_RABBIT_MODEL_URL)
  const model = useMemo(
    () => prepareScaledGltfModel({
      source: gltf.scene,
      name: 'FlipbookRunningRabbit',
      targetSize: HUB_FLIPBOOK_RUNNING_RABBIT_HEIGHT,
      clone: cloneSkeleton,
      scaleReference: 'height',
      onTraverse: (child) => {
        if (!isMesh(child)) return
        disableMeshShadows(child)
      },
    }),
    [gltf.scene],
  )
  const { actions } = useAnimations(gltf.animations, model)

  useEffect(() => {
    const animationActions = Object.values(actions).filter(isAnimationAction)

    animationActions.forEach((action) => {
      action.reset()
      action.setLoop(LoopRepeat, Infinity)
      action.timeScale = RABBIT_RUN_SPEED
      action.fadeIn(RABBIT_ANIMATION_FADE_SECONDS).play()
    })

    return () => {
      animationActions.forEach((action) => {
        action.fadeOut(RABBIT_ANIMATION_FADE_SECONDS)
        action.stop()
      })
    }
  }, [actions])

  return model
}

export function preloadFlipbookRunningRabbitModel() {
  useGLTF.preload(FLIPBOOK_RUNNING_RABBIT_MODEL_URL)
}
