'use client'

import { useEffect, useMemo } from 'react'
import { useAnimations, useGLTF } from '@react-three/drei'
import { clone as cloneSkeleton } from 'three/examples/jsm/utils/SkeletonUtils.js'
import {
  Box3,
  LoopRepeat,
  Mesh,
  Vector3,
  type AnimationAction,
  type Object3D,
} from 'three'

const RUNNING_RABBIT_MODEL_URL = '/models/runrabbit.glb'
const RUNNING_RABBIT_HEIGHT = 0.72
const RUNNING_RABBIT_ANIMATION_FADE_SECONDS = 0.12
const RUNNING_RABBIT_SPEED = 1.25

function isMesh(object: Object3D): object is Mesh {
  return (object as Mesh).isMesh === true
}

function isAnimationAction(action: AnimationAction | null): action is AnimationAction {
  return action !== null
}

function prepareFlipbookEntranceRunningRabbitModel(source: Object3D) {
  const model = cloneSkeleton(source)
  model.name = 'FlipbookEntranceRunningRabbit'
  model.updateMatrixWorld(true)

  const bounds = new Box3().setFromObject(model)
  const size = new Vector3()
  const center = new Vector3()
  bounds.getSize(size)
  bounds.getCenter(center)

  const modelScale = RUNNING_RABBIT_HEIGHT / size.y
  model.scale.setScalar(modelScale)
  model.position.set(
    -center.x * modelScale,
    -bounds.min.y * modelScale,
    -center.z * modelScale,
  )

  model.traverse((child) => {
    if (!isMesh(child)) return

    child.castShadow = false
    child.receiveShadow = false
  })

  return model
}

export function useFlipbookEntranceRunningRabbitModel() {
  const gltf = useGLTF(RUNNING_RABBIT_MODEL_URL)
  const model = useMemo(() => prepareFlipbookEntranceRunningRabbitModel(gltf.scene), [gltf.scene])
  const { actions } = useAnimations(gltf.animations, model)

  useEffect(() => {
    const animationActions = Object.values(actions).filter(isAnimationAction)

    animationActions.forEach((action) => {
      action.reset()
      action.setLoop(LoopRepeat, Infinity)
      action.timeScale = RUNNING_RABBIT_SPEED
      action.fadeIn(RUNNING_RABBIT_ANIMATION_FADE_SECONDS).play()
    })

    return () => {
      animationActions.forEach((action) => {
        action.fadeOut(RUNNING_RABBIT_ANIMATION_FADE_SECONDS)
        action.stop()
      })
    }
  }, [actions])

  return model
}

export function preloadFlipbookEntranceRunningRabbitModel() {
  useGLTF.preload(RUNNING_RABBIT_MODEL_URL)
}
