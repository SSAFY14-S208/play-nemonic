import type { InfinityLine, InfinityObject, InfinityShape, InfinityText } from './constants'

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
    typeof value.color === 'string'
  )
}

export function isInfinityObject(value: unknown): value is InfinityObject {
  return isLine(value) || isShape(value) || isText(value)
}

export function toInfinityObjects(elements: unknown[]): InfinityObject[] {
  return elements.filter(isInfinityObject)
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
