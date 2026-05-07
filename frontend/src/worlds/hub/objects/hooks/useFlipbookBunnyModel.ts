import { useEffect, useMemo } from 'react'
import { useAnimations, useGLTF } from '@react-three/drei'
import { clone as cloneSkeleton } from 'three/examples/jsm/utils/SkeletonUtils.js'
import {
  LoopRepeat,
  type Object3D,
} from 'three'
import { HUB_FLIPBOOK_BUNNY_MAX_SIZE } from '../../constants'
import {
  disableMeshShadows,
  isAnimationAction,
  isMesh,
  prepareScaledGltfModel,
} from '../../utils'

const FLIPBOOK_BUNNY_MODEL_URL = '/models/flipbook_bunny_asset.glb'
const FLIPBOOK_ANIMATION_FADE_SECONDS = 0.18
const FLIPBOOK_ANIMATION_SPEED = 1.2

function isEmbeddedBunnyObject(object: Object3D) {
  return object.name.startsWith('Animated_Bunny_')
}

function shouldPlayFlipbookAction(actionName: string) {
  return actionName.startsWith('Flip_Page_')
}

export function useFlipbookBunnyModel() {
  const gltf = useGLTF(FLIPBOOK_BUNNY_MODEL_URL)
  const model = useMemo(
    () => prepareScaledGltfModel({
      source: gltf.scene,
      name: 'FlipbookBunnyAsset',
      targetSize: HUB_FLIPBOOK_BUNNY_MAX_SIZE,
      clone: cloneSkeleton,
      scaleReference: 'horizontal',
      onTraverse: (child) => {
        if (isEmbeddedBunnyObject(child)) {
          child.visible = false
        }
        if (!isMesh(child)) return
        disableMeshShadows(child)
      },
    }),
    [gltf.scene],
  )
  const { actions } = useAnimations(gltf.animations, model)

  useEffect(() => {
    const animationActions = Object.entries(actions)
      .filter(([actionName]) => shouldPlayFlipbookAction(actionName))
      .map(([, action]) => action)
      .filter(isAnimationAction)

    animationActions.forEach((action) => {
      action.reset()
      action.setLoop(LoopRepeat, Infinity)
      action.timeScale = FLIPBOOK_ANIMATION_SPEED
      action.fadeIn(FLIPBOOK_ANIMATION_FADE_SECONDS).play()
    })

    return () => {
      animationActions.forEach((action) => {
        action.fadeOut(FLIPBOOK_ANIMATION_FADE_SECONDS)
        action.stop()
      })
    }
  }, [actions])

  return model
}

export function preloadFlipbookBunnyModel() {
  useGLTF.preload(FLIPBOOK_BUNNY_MODEL_URL)
}
