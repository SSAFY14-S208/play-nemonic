import type {
  InfinityFill,
  InfinityImage,
  InfinityLine,
  InfinityObject,
  InfinityShape,
  InfinityText,
} from './constants'

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function isPoint(value: unknown): value is { x: number; y: number } {
  return isRecord(value) && typeof value.x === 'number' && typeof value.y === 'number'
}

function isLine(value: unknown): value is InfinityLine {
  if (!isRecord(value)) return false
  return (
    value.type === 'line' &&
    typeof value.id === 'string' &&
    typeof value.color === 'string' &&
    typeof value.strokeWidth === 'number' &&
    Array.isArray(value.points) &&
    value.points.every(isPoint)
  )
}

function isFill(value: unknown): value is InfinityFill {
  if (!isRecord(value)) return false
  return (
    value.type === 'fill' &&
    typeof value.id === 'string' &&
    typeof value.x === 'number' &&
    typeof value.y === 'number' &&
    typeof value.width === 'number' &&
    typeof value.height === 'number' &&
    typeof value.color === 'string' &&
    typeof value.imageDataUrl === 'string'
  )
}

function isShape(value: unknown): value is InfinityShape {
  if (!isRecord(value)) return false
  return (
    (value.type === 'rect' || value.type === 'ellipse') &&
    typeof value.id === 'string' &&
    typeof value.x === 'number' &&
    typeof value.y === 'number' &&
    typeof value.width === 'number' &&
    typeof value.height === 'number' &&
    typeof value.color === 'string' &&
    typeof value.strokeWidth === 'number'
  )
}

function isText(value: unknown): value is InfinityText {
  if (!isRecord(value)) return false
  return (
    value.type === 'text' &&
    typeof value.id === 'string' &&
    typeof value.x === 'number' &&
    typeof value.y === 'number' &&
    typeof value.text === 'string' &&
    typeof value.fontSize === 'number' &&
    typeof value.color === 'string' &&
    (value.fontFamily === undefined || typeof value.fontFamily === 'string')
  )
}

function isImage(value: unknown): value is InfinityImage {
  if (!isRecord(value)) return false
  return (
    value.type === 'image' &&
    typeof value.id === 'string' &&
    typeof value.x === 'number' &&
    typeof value.y === 'number' &&
    typeof value.width === 'number' &&
    typeof value.height === 'number' &&
    typeof value.src === 'string'
  )
}

export function isInfinityObject(value: unknown): value is InfinityObject {
  return isLine(value) || isFill(value) || isShape(value) || isText(value) || isImage(value)
}

export function toInfinityObjects(elements: unknown[]): InfinityObject[] {
  return elements.filter(isInfinityObject)
}

export function getInfinityObjectLayerIndex(object: InfinityObject, fallbackIndex: number) {
  return typeof object.zIndex === 'number' && Number.isFinite(object.zIndex)
    ? object.zIndex
    : fallbackIndex
}

export function sortInfinityObjectsByLayer(objects: InfinityObject[]) {
  return objects
    .map((object, fallbackIndex) => ({ object, fallbackIndex }))
    .sort((first, second) => {
      const layerDiff =
        getInfinityObjectLayerIndex(first.object, first.fallbackIndex) -
        getInfinityObjectLayerIndex(second.object, second.fallbackIndex)

      return layerDiff === 0 ? first.fallbackIndex - second.fallbackIndex : layerDiff
    })
    .map(({ object }) => object)
}

export function normalizeInfinityObjectLayerIndexes(objects: InfinityObject[]) {
  return sortInfinityObjectsByLayer(objects).map((object, layerIndex) => {
    if (object.zIndex === layerIndex) return object
    return { ...object, zIndex: layerIndex }
  })
}

export function createClientOperationId() {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return crypto.randomUUID()
  }

  return `op-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`
}

export function stringifyInfinityObject(object: InfinityObject) {
  return JSON.stringify(object)
}
