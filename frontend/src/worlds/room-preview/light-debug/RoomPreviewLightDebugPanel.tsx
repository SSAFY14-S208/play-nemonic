'use client'

import {
  Copy,
  Eye,
  EyeOff,
  RotateCcw,
  SlidersHorizontal,
  Target,
} from 'lucide-react'
import { useEffect } from 'react'

import {
  ROOM_PREVIEW_BLENDER_AREA_LIGHTS,
  ROOM_PREVIEW_BLENDER_POINT_LIGHTS,
} from '../objects/RoomPreviewBlenderLights'
import {
  type RoomPreviewLightDebugAxis,
  type RoomPreviewLightDebugGlobalKey,
  type RoomPreviewLightDebugMultiplierKey,
  type RoomPreviewLightDebugShapeKey,
  type RoomPreviewLightDebugVector3,
  useRoomPreviewLightDebugStore,
} from './roomPreviewLightDebugStore'
import { useRoomPreviewLightDebugEnabled } from './useRoomPreviewLightDebugEnabled'

type LightSourceItem = {
  color: [number, number, number]
  defaultDirectMultiplier?: number
  defaultEnabled: boolean
  defaultHeightMultiplier?: number
  defaultPointMultiplier?: number
  defaultPointRangeMultiplier?: number
  defaultSpillMultiplier?: number
  defaultWidthMultiplier?: number
  directScale?: number
  energy: number
  kind: 'area' | 'point'
  name: string
  nearbyAssets: string[]
  position: [number, number, number]
  positionOffset?: RoomPreviewLightDebugVector3
  rotationOffset?: RoomPreviewLightDebugVector3
  size?: number
  sizeY?: number
  spillScale?: number
}

type SliderFieldProps = {
  label: string
  max: number
  min: number
  onChange: (value: number) => void
  step: number
  value: number
  valueFormatter?: (value: number) => string
}

const LIGHT_SOURCE_ITEMS: LightSourceItem[] = [
  ...ROOM_PREVIEW_BLENDER_AREA_LIGHTS.map((light) => ({
    color: light.color,
    defaultDirectMultiplier: light.defaultDirectMultiplier,
    defaultEnabled: !light.hideRender,
    defaultHeightMultiplier: light.defaultHeightMultiplier,
    defaultSpillMultiplier: light.defaultSpillMultiplier,
    defaultWidthMultiplier: light.defaultWidthMultiplier,
    directScale: light.directScale,
    energy: light.energy,
    kind: 'area' as const,
    name: light.name,
    nearbyAssets: light.nearbyAssets,
    position: light.position,
    positionOffset: light.positionOffset,
    rotationOffset: light.rotationOffset,
    size: light.size,
    sizeY: light.sizeY,
    spillScale: light.spillScale,
  })),
  ...ROOM_PREVIEW_BLENDER_POINT_LIGHTS.map((light) => ({
    color: light.color,
    defaultEnabled: !light.hideRender,
    defaultPointMultiplier: light.defaultPointMultiplier,
    defaultPointRangeMultiplier: light.defaultPointRangeMultiplier,
    energy: light.energy,
    kind: 'point' as const,
    name: light.name,
    nearbyAssets: ['side table glow cluster'],
    position: light.position,
    positionOffset: light.positionOffset,
  })),
]

const FIRST_LIGHT_NAME = LIGHT_SOURCE_ITEMS[0]?.name ?? null
const LIGHT_DEBUG_AXES: RoomPreviewLightDebugAxis[] = ['x', 'y', 'z']
const ZERO_VECTOR3: RoomPreviewLightDebugVector3 = [0, 0, 0]
const VECTOR_AXIS_INDEX: Record<RoomPreviewLightDebugAxis, number> = {
  x: 0,
  y: 1,
  z: 2,
}

const formatMultiplier = (value: number) => `${value.toFixed(2)}x`
const formatSignedOffset = (value: number) =>
  `${value >= 0 ? '+' : ''}${value.toFixed(2)}`
const formatSignedDegrees = (value: number) =>
  `${value >= 0 ? '+' : ''}${value.toFixed(0)} deg`

const formatNumber = (value: number) =>
  new Intl.NumberFormat('en-US', {
    maximumFractionDigits: 3,
    minimumFractionDigits: 0,
  }).format(value)

const getRgbColor = ([red, green, blue]: [number, number, number]) => {
  const toChannel = (value: number) =>
    Math.round(Math.min(Math.max(value, 0), 1) * 255)

  return `rgb(${toChannel(red)} ${toChannel(green)} ${toChannel(blue)})`
}

const joinClassNames = (...classNames: Array<false | null | string>) =>
  classNames.filter(Boolean).join(' ')

const getVectorAxisValue = (
  vector: RoomPreviewLightDebugVector3,
  axis: RoomPreviewLightDebugAxis,
) => vector[VECTOR_AXIS_INDEX[axis]]

const SliderField = ({
  valueFormatter = formatMultiplier,
  label,
  max,
  min,
  onChange,
  step,
  value,
}: SliderFieldProps) => (
  <label className="grid gap-1.5 text-[11px] font-medium text-fg-muted">
    <span className="flex items-center justify-between gap-3">
      <span>{label}</span>
      <span className="tabular-nums text-fg-primary">
        {valueFormatter(value)}
      </span>
    </span>
    <input
      className="h-2 w-full accent-primary-500"
      max={max}
      min={min}
      onChange={(event) => onChange(Number(event.currentTarget.value))}
      step={step}
      type="range"
      value={value}
    />
  </label>
)

export const RoomPreviewLightDebugPanel = () => {
  const isQueryEnabled = useRoomPreviewLightDebugEnabled()
  const areaDirectMaster = useRoomPreviewLightDebugStore(
    (state) => state.areaDirectMaster,
  )
  const areaSpillMaster = useRoomPreviewLightDebugStore(
    (state) => state.areaSpillMaster,
  )
  const globalFillMultiplier = useRoomPreviewLightDebugStore(
    (state) => state.globalFillMultiplier,
  )
  const lightOverrides = useRoomPreviewLightDebugStore(
    (state) => state.lightOverrides,
  )
  const pointMaster = useRoomPreviewLightDebugStore((state) => state.pointMaster)
  const selectedLightName = useRoomPreviewLightDebugStore(
    (state) => state.selectedLightName,
  )
  const showHelpers = useRoomPreviewLightDebugStore(
    (state) => state.showHelpers,
  )
  const soloLightName = useRoomPreviewLightDebugStore(
    (state) => state.soloLightName,
  )
  const resetAll = useRoomPreviewLightDebugStore((state) => state.resetAll)
  const resetLight = useRoomPreviewLightDebugStore((state) => state.resetLight)
  const selectLight = useRoomPreviewLightDebugStore((state) => state.selectLight)
  const setDebugEnabled = useRoomPreviewLightDebugStore(
    (state) => state.setDebugEnabled,
  )
  const setGlobalMultiplier = useRoomPreviewLightDebugStore(
    (state) => state.setGlobalMultiplier,
  )
  const setLightMultiplier = useRoomPreviewLightDebugStore(
    (state) => state.setLightMultiplier,
  )
  const setLightPositionOffset = useRoomPreviewLightDebugStore(
    (state) => state.setLightPositionOffset,
  )
  const setLightRotationOffset = useRoomPreviewLightDebugStore(
    (state) => state.setLightRotationOffset,
  )
  const setLightShapeMultiplier = useRoomPreviewLightDebugStore(
    (state) => state.setLightShapeMultiplier,
  )
  const soloLight = useRoomPreviewLightDebugStore((state) => state.soloLight)
  const toggleLight = useRoomPreviewLightDebugStore(
    (state) => state.toggleLight,
  )
  const toggleShowHelpers = useRoomPreviewLightDebugStore(
    (state) => state.toggleShowHelpers,
  )

  useEffect(() => {
    let isCancelled = false

    const syncDebugStore = async () => {
      if (isCancelled) {
        return
      }

      setDebugEnabled(isQueryEnabled)

      if (isQueryEnabled && !selectedLightName && FIRST_LIGHT_NAME) {
        selectLight(FIRST_LIGHT_NAME)
      }
    }

    void syncDebugStore()

    return () => {
      isCancelled = true
    }
  }, [isQueryEnabled, selectLight, selectedLightName, setDebugEnabled])

  if (!isQueryEnabled) {
    return null
  }

  const selectedLight =
    LIGHT_SOURCE_ITEMS.find((light) => light.name === selectedLightName) ??
    LIGHT_SOURCE_ITEMS[0]

  if (!selectedLight) {
    return null
  }

  const selectedOverride = lightOverrides[selectedLight.name] ?? {}
  const selectedEnabled =
    selectedOverride.enabled ?? selectedLight.defaultEnabled
  const selectedDirectMultiplier =
    selectedOverride.directMultiplier ??
    selectedLight.defaultDirectMultiplier ??
    1
  const selectedSpillMultiplier =
    selectedOverride.spillMultiplier ?? selectedLight.defaultSpillMultiplier ?? 1
  const selectedPointMultiplier =
    selectedOverride.pointMultiplier ?? selectedLight.defaultPointMultiplier ?? 1
  const selectedWidthMultiplier =
    selectedOverride.widthMultiplier ?? selectedLight.defaultWidthMultiplier ?? 1
  const selectedHeightMultiplier =
    selectedOverride.heightMultiplier ??
    selectedLight.defaultHeightMultiplier ??
    1
  const selectedPointRangeMultiplier =
    selectedOverride.pointRangeMultiplier ??
    selectedLight.defaultPointRangeMultiplier ??
    1
  const selectedPositionOffset =
    selectedOverride.positionOffset ?? selectedLight.positionOffset ?? ZERO_VECTOR3
  const selectedRotationOffset =
    selectedOverride.rotationOffset ?? selectedLight.rotationOffset ?? ZERO_VECTOR3
  const activeLightCount = LIGHT_SOURCE_ITEMS.filter((light) => {
    const lightEnabled = lightOverrides[light.name]?.enabled ?? light.defaultEnabled
    const soloAllowed = !soloLightName || soloLightName === light.name

    return lightEnabled && soloAllowed
  }).length

  const setSelectedMultiplier = (
    key: RoomPreviewLightDebugMultiplierKey,
    value: number,
  ) => {
    setLightMultiplier(selectedLight.name, key, value)
  }

  const setSelectedPositionOffset = (
    axis: RoomPreviewLightDebugAxis,
    value: number,
  ) => {
    setLightPositionOffset(selectedLight.name, axis, value)
  }

  const setSelectedRotationOffset = (
    axis: RoomPreviewLightDebugAxis,
    value: number,
  ) => {
    setLightRotationOffset(selectedLight.name, axis, value)
  }

  const setSelectedShapeMultiplier = (
    key: RoomPreviewLightDebugShapeKey,
    value: number,
  ) => {
    setLightShapeMultiplier(selectedLight.name, key, value)
  }

  const setGlobal = (key: RoomPreviewLightDebugGlobalKey, value: number) => {
    setGlobalMultiplier(key, value)
  }

  const copyDebugSnapshot = async () => {
    const snapshot = {
      globals: {
        areaDirectMaster,
        areaSpillMaster,
        globalFillMultiplier,
        pointMaster,
        showHelpers,
        soloLightName,
      },
      lights: LIGHT_SOURCE_ITEMS.map((light) => ({
        defaultEnabled: light.defaultEnabled,
        defaultDirectMultiplier: light.defaultDirectMultiplier ?? null,
        defaultHeightMultiplier: light.defaultHeightMultiplier ?? null,
        defaultPointMultiplier: light.defaultPointMultiplier ?? null,
        defaultPointRangeMultiplier: light.defaultPointRangeMultiplier ?? null,
        defaultPositionOffset: light.positionOffset ?? null,
        defaultRotationOffset: light.rotationOffset ?? null,
        defaultSpillMultiplier: light.defaultSpillMultiplier ?? null,
        defaultWidthMultiplier: light.defaultWidthMultiplier ?? null,
        energy: light.energy,
        kind: light.kind,
        name: light.name,
        override: lightOverrides[light.name] ?? null,
      })),
    }

    await window.navigator.clipboard.writeText(
      JSON.stringify(snapshot, null, 2),
    )
  }

  return (
    <aside className="fixed bottom-3 left-3 right-3 z-[80] flex max-h-[74vh] flex-col overflow-hidden rounded-2xl border border-border-default/80 bg-surface-default/95 text-fg-primary shadow-2xl backdrop-blur sm:bottom-auto sm:left-auto sm:right-4 sm:top-4 sm:max-h-[calc(100vh-2rem)] sm:w-[25rem]">
      <header className="flex items-start justify-between gap-3 border-b border-border-default/70 px-4 py-3">
        <div className="min-w-0">
          <p className="text-[11px] font-semibold uppercase tracking-[0.08em] text-fg-muted">
            Room light debug
          </p>
          <h2 className="truncate text-base font-semibold text-fg-primary">
            Blender 광원 1:1 믹서
          </h2>
        </div>
        <span className="rounded-full border border-border-default bg-surface-subtle px-2.5 py-1 text-xs font-semibold tabular-nums text-fg-secondary">
          {activeLightCount}/{LIGHT_SOURCE_ITEMS.length}
        </span>
      </header>

      <div className="min-h-0 overflow-y-auto">
        <section className="grid grid-cols-3 gap-2 border-b border-border-default/70 p-3">
          <button
            aria-pressed={showHelpers}
            className={joinClassNames(
              'flex h-9 items-center justify-center gap-1.5 rounded-lg border text-xs font-semibold transition',
              showHelpers
                ? 'border-primary-300 bg-primary-50 text-primary-700'
                : 'border-border-default bg-surface-subtle text-fg-secondary',
            )}
            onClick={toggleShowHelpers}
            type="button"
          >
            <SlidersHorizontal aria-hidden className="size-3.5" />
            위치
          </button>
          <button
            className="flex h-9 items-center justify-center gap-1.5 rounded-lg border border-border-default bg-surface-subtle text-xs font-semibold text-fg-secondary transition hover:bg-surface-muted"
            onClick={resetAll}
            type="button"
          >
            <RotateCcw aria-hidden className="size-3.5" />
            전체 리셋
          </button>
          <button
            className="flex h-9 items-center justify-center gap-1.5 rounded-lg border border-border-default bg-surface-subtle text-xs font-semibold text-fg-secondary transition hover:bg-surface-muted"
            onClick={() => void copyDebugSnapshot()}
            type="button"
          >
            <Copy aria-hidden className="size-3.5" />
            복사
          </button>
        </section>

        <section className="grid gap-3 border-b border-border-default/70 px-4 py-3">
          <div className="flex items-center justify-between gap-3">
            <h3 className="text-sm font-semibold text-fg-primary">전체 믹스</h3>
            {soloLightName ? (
              <button
                className="rounded-full border border-primary-200 bg-primary-50 px-2.5 py-1 text-[11px] font-semibold text-primary-700"
                onClick={() => soloLight(soloLightName)}
                type="button"
              >
                Solo 해제
              </button>
            ) : null}
          </div>
          <SliderField
            label="Area direct"
            max={3}
            min={0}
            onChange={(value) => setGlobal('areaDirectMaster', value)}
            step={0.01}
            value={areaDirectMaster}
          />
          <SliderField
            label="Area spill"
            max={5}
            min={0}
            onChange={(value) => setGlobal('areaSpillMaster', value)}
            step={0.01}
            value={areaSpillMaster}
          />
          <SliderField
            label="Point"
            max={5}
            min={0}
            onChange={(value) => setGlobal('pointMaster', value)}
            step={0.01}
            value={pointMaster}
          />
          <SliderField
            label="Ambient / environment"
            max={2}
            min={0}
            onChange={(value) => setGlobal('globalFillMultiplier', value)}
            step={0.01}
            value={globalFillMultiplier}
          />
        </section>

        <section className="grid gap-2 border-b border-border-default/70 px-3 py-3">
          <div className="flex items-center justify-between gap-3 px-1">
            <h3 className="text-sm font-semibold text-fg-primary">광원 목록</h3>
            <span className="text-[11px] text-fg-muted">
              area {ROOM_PREVIEW_BLENDER_AREA_LIGHTS.length} / point{' '}
              {ROOM_PREVIEW_BLENDER_POINT_LIGHTS.length}
            </span>
          </div>
          <div className="grid max-h-52 gap-1.5 overflow-y-auto pr-1">
            {LIGHT_SOURCE_ITEMS.map((light) => {
              const isSelected = light.name === selectedLight.name
              const lightEnabled =
                lightOverrides[light.name]?.enabled ?? light.defaultEnabled
              const lightSoloed = soloLightName === light.name

              return (
                <button
                  className={joinClassNames(
                    'grid min-h-11 grid-cols-[0.75rem_minmax(0,1fr)_auto] items-center gap-2 rounded-lg border px-2.5 py-2 text-left transition',
                    isSelected
                      ? 'border-primary-300 bg-primary-50 text-primary-800'
                      : 'border-border-default bg-surface-subtle text-fg-secondary hover:bg-surface-muted',
                  )}
                  key={light.name}
                  onClick={() => selectLight(light.name)}
                  type="button"
                >
                  <span
                    className={joinClassNames(
                      'size-3 rounded-full border border-white/80 shadow-sm',
                      lightEnabled ? '' : 'opacity-35 grayscale',
                    )}
                    style={{ backgroundColor: getRgbColor(light.color) }}
                  />
                  <span className="min-w-0">
                    <span className="block truncate text-xs font-semibold">
                      {light.name}
                    </span>
                    <span className="block truncate text-[10px] uppercase tracking-[0.06em] text-fg-muted">
                      {light.kind}
                      {!light.defaultEnabled ? ' · hidden in render' : ''}
                    </span>
                  </span>
                  <span className="flex items-center gap-1">
                    {lightSoloed ? (
                      <Target
                        aria-label="solo"
                        className="size-3.5 text-primary-600"
                      />
                    ) : null}
                    {lightEnabled ? (
                      <Eye
                        aria-label="enabled"
                        className="size-3.5 text-fg-muted"
                      />
                    ) : (
                      <EyeOff
                        aria-label="disabled"
                        className="size-3.5 text-fg-muted"
                      />
                    )}
                  </span>
                </button>
              )
            })}
          </div>
        </section>

        <section className="grid gap-3 px-4 py-3">
          <div className="flex items-start justify-between gap-3">
            <div className="min-w-0">
              <h3 className="truncate text-sm font-semibold text-fg-primary">
                {selectedLight.name}
              </h3>
              <p className="text-[11px] text-fg-muted">
                energy {formatNumber(selectedLight.energy)}
                {selectedLight.kind === 'area' &&
                selectedLight.directScale !== undefined &&
                selectedLight.spillScale !== undefined
                  ? ` · direct ${formatNumber(selectedLight.directScale)} · spill ${formatNumber(selectedLight.spillScale)}`
                  : ''}
              </p>
            </div>
            <span className="rounded-full border border-border-default bg-surface-subtle px-2 py-0.5 text-[11px] font-semibold uppercase text-fg-muted">
              {selectedLight.kind}
            </span>
          </div>

          <div className="grid grid-cols-3 gap-2">
            <button
              aria-pressed={selectedEnabled}
              className={joinClassNames(
                'flex h-9 items-center justify-center gap-1.5 rounded-lg border text-xs font-semibold transition',
                selectedEnabled
                  ? 'border-success-200 bg-success-50 text-success-700'
                  : 'border-border-default bg-surface-subtle text-fg-muted',
              )}
              onClick={() =>
                toggleLight(selectedLight.name, selectedLight.defaultEnabled)
              }
              type="button"
            >
              {selectedEnabled ? (
                <Eye aria-hidden className="size-3.5" />
              ) : (
                <EyeOff aria-hidden className="size-3.5" />
              )}
              {selectedEnabled ? 'On' : 'Off'}
            </button>
            <button
              aria-pressed={soloLightName === selectedLight.name}
              className={joinClassNames(
                'flex h-9 items-center justify-center gap-1.5 rounded-lg border text-xs font-semibold transition',
                soloLightName === selectedLight.name
                  ? 'border-primary-300 bg-primary-50 text-primary-700'
                  : 'border-border-default bg-surface-subtle text-fg-secondary hover:bg-surface-muted',
              )}
              onClick={() => soloLight(selectedLight.name)}
              type="button"
            >
              <Target aria-hidden className="size-3.5" />
              Solo
            </button>
            <button
              className="flex h-9 items-center justify-center gap-1.5 rounded-lg border border-border-default bg-surface-subtle text-xs font-semibold text-fg-secondary transition hover:bg-surface-muted"
              onClick={() => resetLight(selectedLight.name)}
              type="button"
            >
              <RotateCcw aria-hidden className="size-3.5" />
              리셋
            </button>
          </div>

          {selectedLight.kind === 'area' ? (
            <div className="grid gap-3 rounded-xl border border-border-default/70 bg-surface-subtle p-3">
              <SliderField
                label="선택 direct gain"
                max={6}
                min={0}
                onChange={(value) =>
                  setSelectedMultiplier('directMultiplier', value)
                }
                step={0.01}
                value={selectedDirectMultiplier}
              />
              <SliderField
                label="선택 spill gain"
                max={6}
                min={0}
                onChange={(value) =>
                  setSelectedMultiplier('spillMultiplier', value)
                }
                step={0.01}
                value={selectedSpillMultiplier}
              />
            </div>
          ) : (
            <div className="grid gap-3 rounded-xl border border-border-default/70 bg-surface-subtle p-3">
              <SliderField
                label="선택 point gain"
                max={6}
                min={0}
                onChange={(value) =>
                  setSelectedMultiplier('pointMultiplier', value)
                }
                step={0.01}
                value={selectedPointMultiplier}
              />
            </div>
          )}

          {selectedLight.kind === 'area' ? (
            <div className="grid gap-3 rounded-xl border border-border-default/70 bg-surface-subtle p-3">
              <div className="flex items-center justify-between gap-3">
                <h4 className="text-xs font-semibold text-fg-secondary">
                  Size / shape
                </h4>
                <span className="text-[10px] uppercase tracking-[0.06em] text-fg-muted">
                  Rect area
                </span>
              </div>
              <SliderField
                label="Width"
                max={4}
                min={0.1}
                onChange={(value) =>
                  setSelectedShapeMultiplier('widthMultiplier', value)
                }
                step={0.01}
                value={selectedWidthMultiplier}
              />
              <SliderField
                label="Height"
                max={4}
                min={0.1}
                onChange={(value) =>
                  setSelectedShapeMultiplier('heightMultiplier', value)
                }
                step={0.01}
                value={selectedHeightMultiplier}
              />
            </div>
          ) : (
            <div className="grid gap-3 rounded-xl border border-border-default/70 bg-surface-subtle p-3">
              <div className="flex items-center justify-between gap-3">
                <h4 className="text-xs font-semibold text-fg-secondary">
                  Range
                </h4>
                <span className="text-[10px] uppercase tracking-[0.06em] text-fg-muted">
                  Point light
                </span>
              </div>
              <SliderField
                label="Range"
                max={4}
                min={0.1}
                onChange={(value) =>
                  setSelectedShapeMultiplier('pointRangeMultiplier', value)
                }
                step={0.01}
                value={selectedPointRangeMultiplier}
              />
            </div>
          )}

          <div className="grid gap-3 rounded-xl border border-border-default/70 bg-surface-subtle p-3">
            <div className="flex items-center justify-between gap-3">
              <h4 className="text-xs font-semibold text-fg-secondary">
                Transform offset
              </h4>
              <span className="text-[10px] uppercase tracking-[0.06em] text-fg-muted">
                Blender units
              </span>
            </div>
            <div className="grid gap-2">
              {LIGHT_DEBUG_AXES.map((axis) => (
                <SliderField
                  key={`move-${axis}`}
                  label={`Move ${axis.toUpperCase()}`}
                  max={1.5}
                  min={-1.5}
                  onChange={(value) => setSelectedPositionOffset(axis, value)}
                  step={0.01}
                  value={getVectorAxisValue(selectedPositionOffset, axis)}
                  valueFormatter={formatSignedOffset}
                />
              ))}
            </div>
            {selectedLight.kind === 'area' && (
              <div className="grid gap-2 border-t border-border-default/70 pt-3">
                {LIGHT_DEBUG_AXES.map((axis) => (
                  <SliderField
                    key={`rotate-${axis}`}
                    label={`Rotate ${axis.toUpperCase()}`}
                    max={180}
                    min={-180}
                    onChange={(value) => setSelectedRotationOffset(axis, value)}
                    step={1}
                    value={getVectorAxisValue(selectedRotationOffset, axis)}
                    valueFormatter={formatSignedDegrees}
                  />
                ))}
              </div>
            )}
          </div>

          <dl className="grid grid-cols-2 gap-2 text-[11px] text-fg-muted">
            <div className="rounded-lg border border-border-default/70 bg-surface-subtle px-2.5 py-2">
              <dt className="font-semibold text-fg-secondary">Blender 위치</dt>
              <dd className="mt-1 tabular-nums">
                {selectedLight.position.map(formatNumber).join(', ')}
              </dd>
            </div>
            <div className="rounded-lg border border-border-default/70 bg-surface-subtle px-2.5 py-2">
              <dt className="font-semibold text-fg-secondary">기본 상태</dt>
              <dd className="mt-1">
                {selectedLight.defaultEnabled ? 'visible' : 'hidden render'}
              </dd>
            </div>
          </dl>

          <div className="rounded-lg border border-border-default/70 bg-surface-subtle px-2.5 py-2">
            <p className="text-[11px] font-semibold text-fg-secondary">
              가까운 에셋
            </p>
            <p className="mt-1 text-[11px] leading-5 text-fg-muted">
              {selectedLight.nearbyAssets.join(' · ')}
            </p>
          </div>
        </section>
      </div>
    </aside>
  )
}
