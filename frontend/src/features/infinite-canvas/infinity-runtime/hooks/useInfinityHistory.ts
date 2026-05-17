'use client'

import { useRef, useState } from 'react'

import type { InfinityObject } from '../constants'

interface InfinitySnapshot {
  objects: InfinityObject[]
  selectedIds: string[]
}

const INITIAL_SNAPSHOT: InfinitySnapshot = { objects: [], selectedIds: [] }

function createObjectMap(objects: InfinityObject[]) {
  return new Map(objects.map((object) => [object.id, object]))
}

function objectMapValues(objectMap: Map<string, InfinityObject>) {
  return [...objectMap.values()]
}

export function useInfinityHistory() {
  const [objects, setObjects] = useState<InfinityObject[]>([])
  const [selectedIds, setSelectedIds] = useState<string[]>([])
  const [historyCursor, setHistoryCursor] = useState({ index: 0, length: 1 })

  const historyRef = useRef<{ snapshots: InfinitySnapshot[]; index: number }>({
    snapshots: [INITIAL_SNAPSHOT],
    index: 0,
  })
  const objectsRef = useRef<InfinityObject[]>([])
  const objectMapRef = useRef<Map<string, InfinityObject>>(new Map())
  const selectedIdsRef = useRef<string[]>([])

  const commitObjects = (
    newObjects: InfinityObject[],
    newSelectedIds: string[],
  ) => {
    const nextObjectMap = createObjectMap(newObjects)
    const nextObjects = objectMapValues(nextObjectMap)
    objectMapRef.current = nextObjectMap
    objectsRef.current = nextObjects
    selectedIdsRef.current = newSelectedIds
    setObjects(nextObjects)
    setSelectedIds(newSelectedIds)
    return nextObjects
  }

  const saveSnapshot = (
    newObjects: InfinityObject[],
    newSelectedIds: string[],
  ) => {
    const { snapshots, index } = historyRef.current
    const trimmed = snapshots.slice(0, index + 1)
    const nextObjects = objectMapValues(createObjectMap(newObjects))
    trimmed.push({
      objects: [...nextObjects],
      selectedIds: [...newSelectedIds],
    })
    historyRef.current = { snapshots: trimmed, index: trimmed.length - 1 }
    commitObjects(nextObjects, newSelectedIds)
    setHistoryCursor({ index: trimmed.length - 1, length: trimmed.length })
  }

  const replaceObjectsFromServer = (
    newObjects: InfinityObject[],
    newSelectedIds: string[] = selectedIdsRef.current,
  ) => {
    const snapshot = {
      objects: [...newObjects],
      selectedIds: [...newSelectedIds],
    }
    historyRef.current = {
      snapshots: [snapshot],
      index: 0,
    }
    commitObjects(newObjects, newSelectedIds)
    setHistoryCursor({ index: 0, length: 1 })
  }

  const syncObjectsFromServer = (
    newObjects: InfinityObject[],
    newSelectedIds: string[] = selectedIdsRef.current,
  ) => {
    const { snapshots, index } = historyRef.current
    const nextSnapshots = snapshots.map((snapshot, snapshotIndex) =>
      snapshotIndex === index
        ? {
            objects: [...newObjects],
            selectedIds: [...newSelectedIds],
          }
        : snapshot,
    )
    historyRef.current = { snapshots: nextSnapshots, index }
    commitObjects(newObjects, newSelectedIds)
  }

  // 선택 해제를 history에 기록하지 않고 selection만 비움.
  const silentClearSelection = () => {
    selectedIdsRef.current = []
    setSelectedIds([])
  }

  const silentSetSelection = (newSelectedIds: string[]) => {
    selectedIdsRef.current = newSelectedIds
    setSelectedIds(newSelectedIds)
  }

  // 도형 클릭 등으로 selection만 변경할 때 사용 — history 기록 O.
  const recordSelection = (newSelectedIds: string[]) => {
    saveSnapshot(objectsRef.current, newSelectedIds)
  }

  const undo = () => {
    const { snapshots, index } = historyRef.current
    if (index <= 0) return
    const newIndex = index - 1
    historyRef.current = { ...historyRef.current, index: newIndex }
    const snapshot = snapshots[newIndex]
    commitObjects([...snapshot.objects], [...snapshot.selectedIds])
    setHistoryCursor({ index: newIndex, length: snapshots.length })
  }

  const redo = () => {
    const { snapshots, index } = historyRef.current
    if (index >= snapshots.length - 1) return
    const newIndex = index + 1
    historyRef.current = { ...historyRef.current, index: newIndex }
    const snapshot = snapshots[newIndex]
    commitObjects([...snapshot.objects], [...snapshot.selectedIds])
    setHistoryCursor({ index: newIndex, length: snapshots.length })
  }

  const canUndo = historyCursor.index > 0
  const canRedo = historyCursor.index < historyCursor.length - 1

  return {
    objects,
    objectsRef,
    objectMapRef,
    selectedIds,
    selectedIdsRef,
    saveSnapshot,
    replaceObjectsFromServer,
    syncObjectsFromServer,
    silentClearSelection,
    silentSetSelection,
    recordSelection,
    undo,
    redo,
    canUndo,
    canRedo,
  } as const
}
