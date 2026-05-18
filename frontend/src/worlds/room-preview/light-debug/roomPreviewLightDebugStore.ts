import { create } from 'zustand'

export type RoomPreviewLightDebugMultiplierKey =
  | 'directMultiplier'
  | 'pointMultiplier'
  | 'spillMultiplier'

export type RoomPreviewLightDebugShapeKey =
  | 'heightMultiplier'
  | 'pointRangeMultiplier'
  | 'widthMultiplier'

export type RoomPreviewLightDebugGlobalKey =
  | 'areaDirectMaster'
  | 'areaSpillMaster'
  | 'globalFillMultiplier'
  | 'pointMaster'

export type RoomPreviewLightDebugAxis = 'x' | 'y' | 'z'

export type RoomPreviewLightDebugVector3 = [number, number, number]

export type RoomPreviewLightDebugOverride = {
  directMultiplier?: number
  enabled?: boolean
  heightMultiplier?: number
  pointMultiplier?: number
  pointRangeMultiplier?: number
  positionOffset?: RoomPreviewLightDebugVector3
  rotationOffset?: RoomPreviewLightDebugVector3
  spillMultiplier?: number
  widthMultiplier?: number
}

type RoomPreviewLightDebugState = {
  areaDirectMaster: number
  areaSpillMaster: number
  globalFillMultiplier: number
  isDebugEnabled: boolean
  isFrustumCullingEnabled: boolean
  lightOverrides: Record<string, RoomPreviewLightDebugOverride>
  pointMaster: number
  selectedLightName: null | string
  showHelpers: boolean
  soloLightName: null | string
  resetAll: () => void
  resetLight: (lightName: string) => void
  selectLight: (lightName: string) => void
  setDebugEnabled: (isDebugEnabled: boolean) => void
  toggleFrustumCulling: () => void
  setGlobalMultiplier: (
    key: RoomPreviewLightDebugGlobalKey,
    value: number,
  ) => void
  setLightEnabled: (lightName: string, enabled: boolean) => void
  setLightMultiplier: (
    lightName: string,
    key: RoomPreviewLightDebugMultiplierKey,
    value: number,
  ) => void
  setLightPositionOffset: (
    lightName: string,
    axis: RoomPreviewLightDebugAxis,
    value: number,
  ) => void
  setLightRotationOffset: (
    lightName: string,
    axis: RoomPreviewLightDebugAxis,
    value: number,
  ) => void
  setLightShapeMultiplier: (
    lightName: string,
    key: RoomPreviewLightDebugShapeKey,
    value: number,
  ) => void
  soloLight: (lightName: string) => void
  toggleLight: (lightName: string, defaultEnabled: boolean) => void
  toggleShowHelpers: () => void
}

const DEFAULT_AREA_DIRECT_MASTER = 1
const DEFAULT_AREA_SPILL_MASTER = 1
const DEFAULT_GLOBAL_FILL_MULTIPLIER = 1
const DEFAULT_POINT_MASTER = 1
const DEFAULT_VECTOR3: RoomPreviewLightDebugVector3 = [0, 0, 0]
const VECTOR_AXIS_INDEX: Record<RoomPreviewLightDebugAxis, number> = {
  x: 0,
  y: 1,
  z: 2,
}

const clampMultiplier = (value: number, min = 0, max = 6) => {
  if (!Number.isFinite(value)) {
    return 1
  }

  return Math.min(Math.max(value, min), max)
}

const clampOffset = (value: number) => {
  if (!Number.isFinite(value)) {
    return 0
  }

  return Math.min(Math.max(value, -1.5), 1.5)
}

const clampRotation = (value: number) => {
  if (!Number.isFinite(value)) {
    return 0
  }

  return Math.min(Math.max(value, -180), 180)
}

const setVectorAxisValue = (
  vector: RoomPreviewLightDebugVector3 | undefined,
  axis: RoomPreviewLightDebugAxis,
  value: number,
  clampValue: (nextValue: number) => number,
): RoomPreviewLightDebugVector3 => {
  const nextVector: RoomPreviewLightDebugVector3 = [
    ...(vector ?? DEFAULT_VECTOR3),
  ] as RoomPreviewLightDebugVector3
  nextVector[VECTOR_AXIS_INDEX[axis]] = clampValue(value)

  return nextVector
}

const FRUSTUM_CULLING_STORAGE_KEY = 'room-preview-frustum-culling-enabled'

const readPersistedFrustumCulling = (): boolean => {
  if (typeof window === 'undefined') return true
  const stored = window.sessionStorage.getItem(FRUSTUM_CULLING_STORAGE_KEY)
  return stored === null ? true : stored === '1'
}

const writePersistedFrustumCulling = (next: boolean) => {
  if (typeof window === 'undefined') return
  window.sessionStorage.setItem(FRUSTUM_CULLING_STORAGE_KEY, next ? '1' : '0')
}

export const useRoomPreviewLightDebugStore =
  create<RoomPreviewLightDebugState>((set) => ({
    areaDirectMaster: DEFAULT_AREA_DIRECT_MASTER,
    areaSpillMaster: DEFAULT_AREA_SPILL_MASTER,
    globalFillMultiplier: DEFAULT_GLOBAL_FILL_MULTIPLIER,
    isDebugEnabled: false,
    isFrustumCullingEnabled: readPersistedFrustumCulling(),
    lightOverrides: {},
    pointMaster: DEFAULT_POINT_MASTER,
    selectedLightName: null,
    showHelpers: true,
    soloLightName: null,
    resetAll: () =>
      set({
        areaDirectMaster: DEFAULT_AREA_DIRECT_MASTER,
        areaSpillMaster: DEFAULT_AREA_SPILL_MASTER,
        globalFillMultiplier: DEFAULT_GLOBAL_FILL_MULTIPLIER,
        lightOverrides: {},
        pointMaster: DEFAULT_POINT_MASTER,
        showHelpers: true,
        soloLightName: null,
      }),
    resetLight: (lightName) =>
      set((state) => {
        const nextOverrides = { ...state.lightOverrides }
        delete nextOverrides[lightName]

        return {
          lightOverrides: nextOverrides,
          soloLightName:
            state.soloLightName === lightName ? null : state.soloLightName,
        }
      }),
    selectLight: (lightName) => set({ selectedLightName: lightName }),
    setDebugEnabled: (isDebugEnabled) => set({ isDebugEnabled }),
    setGlobalMultiplier: (key, value) =>
      set({
        [key]: clampMultiplier(
          value,
          0,
          key === 'globalFillMultiplier' ? 2 : 6,
        ),
      }),
    setLightEnabled: (lightName, enabled) =>
      set((state) => ({
        lightOverrides: {
          ...state.lightOverrides,
          [lightName]: {
            ...state.lightOverrides[lightName],
            enabled,
          },
        },
      })),
    setLightMultiplier: (lightName, key, value) =>
      set((state) => ({
        lightOverrides: {
          ...state.lightOverrides,
          [lightName]: {
            ...state.lightOverrides[lightName],
            [key]: clampMultiplier(value),
          },
        },
      })),
    setLightPositionOffset: (lightName, axis, value) =>
      set((state) => {
        const currentOverride = state.lightOverrides[lightName]

        return {
          lightOverrides: {
            ...state.lightOverrides,
            [lightName]: {
              ...currentOverride,
              positionOffset: setVectorAxisValue(
                currentOverride?.positionOffset,
                axis,
                value,
                clampOffset,
              ),
            },
          },
        }
      }),
    setLightRotationOffset: (lightName, axis, value) =>
      set((state) => {
        const currentOverride = state.lightOverrides[lightName]

        return {
          lightOverrides: {
            ...state.lightOverrides,
            [lightName]: {
              ...currentOverride,
              rotationOffset: setVectorAxisValue(
                currentOverride?.rotationOffset,
                axis,
                value,
                clampRotation,
              ),
            },
          },
        }
      }),
    setLightShapeMultiplier: (lightName, key, value) =>
      set((state) => ({
        lightOverrides: {
          ...state.lightOverrides,
          [lightName]: {
            ...state.lightOverrides[lightName],
            [key]: clampMultiplier(value, 0.05, 6),
          },
        },
      })),
    soloLight: (lightName) =>
      set((state) => {
        if (state.soloLightName === lightName) {
          return { soloLightName: null }
        }

        return {
          lightOverrides: {
            ...state.lightOverrides,
            [lightName]: {
              ...state.lightOverrides[lightName],
              enabled: true,
            },
          },
          selectedLightName: lightName,
          soloLightName: lightName,
        }
      }),
    toggleLight: (lightName, defaultEnabled) =>
      set((state) => {
        const currentEnabled =
          state.lightOverrides[lightName]?.enabled ?? defaultEnabled

        return {
          lightOverrides: {
            ...state.lightOverrides,
            [lightName]: {
              ...state.lightOverrides[lightName],
              enabled: !currentEnabled,
            },
          },
        }
      }),
    toggleFrustumCulling: () =>
      set((state) => {
        const next = !state.isFrustumCullingEnabled
        writePersistedFrustumCulling(next)
        return { isFrustumCullingEnabled: next }
      }),
    toggleShowHelpers: () =>
      set((state) => ({ showHelpers: !state.showHelpers })),
  }))
