import { useCallback, useMemo, useState } from 'react'

export const ROOM_PREVIEW_HUB_HITBOX_TARGETS = [
  'nemonic',
  'monitorScreen',
  'monitorPrevious',
  'monitorNext',
  'monitorStart',
  'communityBoard',
] as const

export type RoomPreviewHubHitboxTarget =
  (typeof ROOM_PREVIEW_HUB_HITBOX_TARGETS)[number]

export type RoomPreviewHitboxVectorField = 'position' | 'rotation' | 'size'
export type RoomPreviewHitboxVectorAxis = 'x' | 'y' | 'z'

export interface RoomPreviewHubHitboxConfig {
  position: [number, number, number]
  rotation: [number, number, number]
  size: [number, number, number]
}

export type RoomPreviewHubHitboxConfigs = Record<
  RoomPreviewHubHitboxTarget,
  RoomPreviewHubHitboxConfig
>

export const ROOM_PREVIEW_HUB_HITBOX_LABELS: Record<
  RoomPreviewHubHitboxTarget,
  string
> = {
  communityBoard: 'Community board',
  monitorNext: 'Monitor next',
  monitorPrevious: 'Monitor previous',
  monitorScreen: 'Monitor screen',
  monitorStart: 'Monitor start',
  nemonic: 'Nemonic',
}

export const DEFAULT_ROOM_PREVIEW_HUB_HITBOX_CONFIGS: RoomPreviewHubHitboxConfigs =
  {
    communityBoard: {
      position: [0, 0, 0.025],
      rotation: [0, 0, 0],
      size: [0.43, 0.29, 0.06],
    },
    monitorNext: {
      position: [1.08, 0, 0.13],
      rotation: [0, 0, 0],
      size: [0.36, 1.08, 0.08],
    },
    monitorPrevious: {
      position: [-1.08, 0, 0.13],
      rotation: [0, 0, 0],
      size: [0.36, 1.08, 0.08],
    },
    monitorScreen: {
      position: [0, 0, 0.08],
      rotation: [0, 0, 0],
      size: [2.58, 1.4, 0.08],
    },
    monitorStart: {
      position: [0, -0.43, 0.13],
      rotation: [0, 0, 0],
      size: [1.1, 0.36, 0.08],
    },
    nemonic: {
      position: [-0.41, 0.34, -0.499],
      rotation: [0, -135, 0],
      size: [0.06, 0.06, 0.06],
    },
  }

function cloneHitboxConfig(config: RoomPreviewHubHitboxConfig) {
  return {
    position: [...config.position] as [number, number, number],
    rotation: [...config.rotation] as [number, number, number],
    size: [...config.size] as [number, number, number],
  } satisfies RoomPreviewHubHitboxConfig
}

function cloneHitboxConfigs(configs: RoomPreviewHubHitboxConfigs) {
  return ROOM_PREVIEW_HUB_HITBOX_TARGETS.reduce<RoomPreviewHubHitboxConfigs>(
    (clonedConfigs, target) => {
      clonedConfigs[target] = cloneHitboxConfig(configs[target])
      return clonedConfigs
    },
    {} as RoomPreviewHubHitboxConfigs,
  )
}

function getAxisIndex(axis: RoomPreviewHitboxVectorAxis) {
  if (axis === 'x') return 0
  if (axis === 'y') return 1

  return 2
}

export function useRoomPreviewHubHitboxCalibration() {
  const [configs, setConfigs] = useState<RoomPreviewHubHitboxConfigs>(() =>
    cloneHitboxConfigs(DEFAULT_ROOM_PREVIEW_HUB_HITBOX_CONFIGS),
  )
  const [selectedTarget, setSelectedTarget] =
    useState<RoomPreviewHubHitboxTarget>('nemonic')

  const selectedConfig = configs[selectedTarget]
  const snapshot = useMemo(() => JSON.stringify(configs, null, 2), [configs])

  const updateHitboxValue = useCallback(
    ({
      axis,
      field,
      target,
      value,
    }: {
      axis: RoomPreviewHitboxVectorAxis
      field: RoomPreviewHitboxVectorField
      target: RoomPreviewHubHitboxTarget
      value: number
    }) => {
      if (!Number.isFinite(value)) return

      setConfigs((previousConfigs) => {
        const nextConfigs = cloneHitboxConfigs(previousConfigs)
        const nextVector = [...nextConfigs[target][field]] as [
          number,
          number,
          number,
        ]

        nextVector[getAxisIndex(axis)] = value
        nextConfigs[target] = {
          ...nextConfigs[target],
          [field]: nextVector,
        }

        return nextConfigs
      })
    },
    [],
  )

  const resetHitbox = useCallback((target: RoomPreviewHubHitboxTarget) => {
    setConfigs((previousConfigs) => ({
      ...previousConfigs,
      [target]: cloneHitboxConfig(
        DEFAULT_ROOM_PREVIEW_HUB_HITBOX_CONFIGS[target],
      ),
    }))
  }, [])

  const resetAllHitboxes = useCallback(() => {
    setConfigs(cloneHitboxConfigs(DEFAULT_ROOM_PREVIEW_HUB_HITBOX_CONFIGS))
  }, [])

  return {
    configs,
    resetAllHitboxes,
    resetHitbox,
    selectedConfig,
    selectedTarget,
    setSelectedTarget,
    snapshot,
    updateHitboxValue,
  }
}
