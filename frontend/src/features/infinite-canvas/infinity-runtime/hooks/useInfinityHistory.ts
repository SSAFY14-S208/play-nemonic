'use client'

import { useRef, useState } from 'react'

import type { InfinityObject } from '../constants'

interface InfinitySnapshot {
  objects: InfinityObject[]
  selectedIds: string[]
}

const INITIAL_SNAPSHOT: InfinitySnapshot = { objects: [], selectedIds: [] }

export function useInfinityHistory() {
  const [objects, setObjects] = useState<InfinityObject[]>([])
  const [selectedIds, setSelectedIds] = useState<string[]>([])
  const [historyCursor, setHistoryCursor] = useState({ index: 0, length: 1 })

  const historyRef = useRef<{ snapshots: InfinitySnapshot[]; index: number }>({
    snapshots: [INITIAL_SNAPSHOT],
    index: 0,
  })
  const objectsRef = useRef<InfinityObject[]>([])
  const selectedIdsRef = useRef<string[]>([])

  const saveSnapshot = (
    newObjects: InfinityObject[],
    newSelectedIds: string[],
  ) => {
    const { snapshots, index } = historyRef.current
    const trimmed = snapshots.slice(0, index + 1)
    trimmed.push({
      objects: [...newObjects],
      selectedIds: [...newSelectedIds],
    })
    historyRef.current = { snapshots: trimmed, index: trimmed.length - 1 }
    objectsRef.current = newObjects
    selectedIdsRef.current = newSelectedIds
    setObjects(newObjects)
    setSelectedIds(newSelectedIds)
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
    objectsRef.current = newObjects
    selectedIdsRef.current = newSelectedIds
    setObjects(newObjects)
    setSelectedIds(newSelectedIds)
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
    objectsRef.current = newObjects
    selectedIdsRef.current = newSelectedIds
    setObjects(newObjects)
    setSelectedIds(newSelectedIds)
  }

  // 선택 해제를 history에 기록하지 않고 selection만 비움.
  const silentClearSelection = () => {
    selectedIdsRef.current = []
    setSelectedIds([])
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
    objectsRef.current = [...snapshot.objects]
    selectedIdsRef.current = [...snapshot.selectedIds]
    setObjects([...snapshot.objects])
    setSelectedIds([...snapshot.selectedIds])
    setHistoryCursor({ index: newIndex, length: snapshots.length })
  }

  const redo = () => {
    const { snapshots, index } = historyRef.current
    if (index >= snapshots.length - 1) return
    const newIndex = index + 1
    historyRef.current = { ...historyRef.current, index: newIndex }
    const snapshot = snapshots[newIndex]
    objectsRef.current = [...snapshot.objects]
    selectedIdsRef.current = [...snapshot.selectedIds]
    setObjects([...snapshot.objects])
    setSelectedIds([...snapshot.selectedIds])
    setHistoryCursor({ index: newIndex, length: snapshots.length })
  }

  const canUndo = historyCursor.index > 0
  const canRedo = historyCursor.index < historyCursor.length - 1

  return {
    objects,
    objectsRef,
    selectedIds,
    selectedIdsRef,
    saveSnapshot,
    replaceObjectsFromServer,
    syncObjectsFromServer,
    silentClearSelection,
    recordSelection,
    undo,
    redo,
    canUndo,
    canRedo,
  } as const
}
