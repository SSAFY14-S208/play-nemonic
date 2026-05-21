import type { Material, Object3D } from 'three'
import type { HubPerformanceMode } from '@/shared/types'

type CounterBucket = Record<string, number>

const IS_DEVELOPMENT = process.env.NODE_ENV === 'development'
const DOUBLE_SIDE = 2
const loggedMaterialScenes = new WeakSet<Object3D>()

let diagnosticsEnabled = false

const counters: Record<
  'controls' | 'frames' | 'invalidates' | 'stores',
  CounterBucket
> = {
  controls: {},
  frames: {},
  invalidates: {},
  stores: {},
}

function isDiagnosticsMode(performanceMode: HubPerformanceMode) {
  return IS_DEVELOPMENT && performanceMode === 'diagnostic'
}

function incrementCounter(bucket: CounterBucket, key: string) {
  bucket[key] = (bucket[key] ?? 0) + 1
}

function resetCounters() {
  Object.values(counters).forEach((bucket) => {
    Object.keys(bucket).forEach((key) => {
      delete bucket[key]
    })
  })
}

function hasCounterValues() {
  return Object.values(counters).some((bucket) => Object.keys(bucket).length > 0)
}

export function isHubPerformanceDiagnosticsEnabled(
  performanceMode: HubPerformanceMode,
) {
  return isDiagnosticsMode(performanceMode)
}

export function isHubPerfOverlayEnabled(): boolean {
  if (typeof window === 'undefined') return false
  if (IS_DEVELOPMENT) return true
  return new URLSearchParams(window.location.search).get('perfOverlay') === '1'
}

export function startHubPerformanceDiagnostics(
  performanceMode: HubPerformanceMode,
) {
  diagnosticsEnabled = isDiagnosticsMode(performanceMode)
  resetCounters()

  if (!diagnosticsEnabled || typeof window === 'undefined') {
    return () => undefined
  }

  console.info('[hub:perf] diagnostic counters enabled')

  const intervalId = window.setInterval(() => {
    if (!hasCounterValues()) {
      console.info('[hub:perf] idle window: no canvas invalidates/store updates')
      return
    }

    console.groupCollapsed('[hub:perf] 5s counters')
    console.table({
      controls: { ...counters.controls },
      frames: { ...counters.frames },
      invalidates: { ...counters.invalidates },
      stores: { ...counters.stores },
    })
    console.groupEnd()
    resetCounters()
  }, 5000)

  return () => {
    window.clearInterval(intervalId)
    diagnosticsEnabled = false
  }
}

export function trackHubControlEvent(eventName: string) {
  if (!diagnosticsEnabled) return
  incrementCounter(counters.controls, eventName)
}

export function trackHubFrame(source: string) {
  if (!diagnosticsEnabled) return
  incrementCounter(counters.frames, source)
}

export function trackHubInvalidate(source: string) {
  if (!diagnosticsEnabled) return
  incrementCounter(counters.invalidates, source)
}

export function trackHubStoreUpdate(storeName: string, actionName: string) {
  if (!diagnosticsEnabled) return
  incrementCounter(counters.stores, `${storeName}.${actionName}`)
}

function getMaterials(material: Material | Material[] | undefined) {
  if (!material) return []
  return Array.isArray(material) ? material : [material]
}

export function logHubMaterialStats(
  scene: Object3D,
  performanceMode: HubPerformanceMode,
  label: string,
) {
  if (!isDiagnosticsMode(performanceMode) || loggedMaterialScenes.has(scene)) {
    return
  }

  loggedMaterialScenes.add(scene)

  const uniqueMaterials = new Set<Material>()
  const materialTypes: CounterBucket = {}
  const stats = {
    alphaMap: 0,
    doubleSide: 0,
    envMapIntensity: 0,
    materialCount: 0,
    meshCount: 0,
    normalMap: 0,
    transparent: 0,
    transmission: 0,
  }

  scene.traverse((child) => {
    const maybeMesh = child as Object3D & {
      isMesh?: boolean
      material?: Material | Material[]
    }

    if (!maybeMesh.isMesh) return

    stats.meshCount += 1

    getMaterials(maybeMesh.material).forEach((material) => {
      if (uniqueMaterials.has(material)) return

      uniqueMaterials.add(material)
      stats.materialCount += 1
      incrementCounter(materialTypes, material.type)

      const materialWithMaps = material as Material & {
        alphaMap?: unknown
        envMapIntensity?: number
        normalMap?: unknown
        transmission?: number
      }

      if (material.transparent) stats.transparent += 1
      if (material.side === DOUBLE_SIDE) stats.doubleSide += 1
      if (materialWithMaps.alphaMap) stats.alphaMap += 1
      if (materialWithMaps.normalMap) stats.normalMap += 1
      if ((materialWithMaps.transmission ?? 0) > 0) stats.transmission += 1
      if ((materialWithMaps.envMapIntensity ?? 0) > 0) {
        stats.envMapIntensity += 1
      }
    })
  })

  console.groupCollapsed(`[hub:perf] ${label} GLB material stats`)
  console.table(stats)
  console.table(materialTypes)
  console.groupEnd()
}
