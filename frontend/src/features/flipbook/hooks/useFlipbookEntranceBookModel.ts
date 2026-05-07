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

const FLIPBOOK_BOOK_MODEL_URL = '/models/flipbook_bunny_asset.glb'
const BOOK_MAX_HORIZONTAL_SIZE = 3.4
const BOOK_ANIMATION_FADE_SECONDS = 0.18
const BOOK_ANIMATION_SPEED = 1.2

function isMesh(object: Object3D): object is Mesh {
  return (object as Mesh).isMesh === true
}

function isAnimationAction(action: AnimationAction | null): action is AnimationAction {
  return action !== null
}

function isEmbeddedBunnyObject(object: Object3D) {
  return object.name.startsWith('Animated_Bunny_')
}

function shouldPlayFlipbookAction(actionName: string) {
  return actionName.startsWith('Flip_Page_')
}

function prepareFlipbookEntranceBookModel(source: Object3D) {
  const model = cloneSkeleton(source)
  model.name = 'FlipbookEntranceBook'
  model.updateMatrixWorld(true)

  const bounds = new Box3().setFromObject(model)
  const size = new Vector3()
  const center = new Vector3()
  bounds.getSize(size)
  bounds.getCenter(center)

  const horizontalSize = Math.max(size.x, size.z)
  const modelScale = BOOK_MAX_HORIZONTAL_SIZE / horizontalSize
  model.scale.setScalar(modelScale)
  model.position.set(
    -center.x * modelScale,
    -bounds.min.y * modelScale,
    -center.z * modelScale,
  )

  model.traverse((child) => {
    if (isEmbeddedBunnyObject(child)) {
      child.visible = false
    }

    if (!isMesh(child)) return

    child.castShadow = false
    child.receiveShadow = false
  })

  return model
}

export function useFlipbookEntranceBookModel() {
  const gltf = useGLTF(FLIPBOOK_BOOK_MODEL_URL)
  const model = useMemo(() => prepareFlipbookEntranceBookModel(gltf.scene), [gltf.scene])
  const { actions } = useAnimations(gltf.animations, model)

  useEffect(() => {
    const animationActions = Object.entries(actions)
      .filter(([actionName]) => shouldPlayFlipbookAction(actionName))
      .map(([, action]) => action)
      .filter(isAnimationAction)

    animationActions.forEach((action) => {
      action.reset()
      action.setLoop(LoopRepeat, Infinity)
      action.timeScale = BOOK_ANIMATION_SPEED
      action.fadeIn(BOOK_ANIMATION_FADE_SECONDS).play()
    })

    return () => {
      animationActions.forEach((action) => {
        action.fadeOut(BOOK_ANIMATION_FADE_SECONDS)
        action.stop()
      })
    }
  }, [actions])

  return model
}

export function preloadFlipbookEntranceBookModel() {
  useGLTF.preload(FLIPBOOK_BOOK_MODEL_URL)
}
