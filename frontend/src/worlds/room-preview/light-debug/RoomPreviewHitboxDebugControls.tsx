import { Copy, RotateCcw } from 'lucide-react'
import type { ChangeEvent } from 'react'
import type {
  RoomPreviewHitboxVectorAxis,
  RoomPreviewHitboxVectorField,
  RoomPreviewHubHitboxTarget,
  useRoomPreviewHubHitboxCalibration,
} from '../useRoomPreviewHubHitboxCalibration'
import {
  ROOM_PREVIEW_HUB_HITBOX_LABELS,
  ROOM_PREVIEW_HUB_HITBOX_TARGETS,
} from '../useRoomPreviewHubHitboxCalibration'

export type RoomPreviewHubHitboxCalibration = ReturnType<
  typeof useRoomPreviewHubHitboxCalibration
>

const HITBOX_NUMBER_FIELDS: Array<{
  field: Exclude<RoomPreviewHitboxVectorField, 'rotation'>
  label: string
}> = [
  { field: 'position', label: 'Position' },
  { field: 'size', label: 'Size' },
]

const HITBOX_AXES: RoomPreviewHitboxVectorAxis[] = ['x', 'y', 'z']

const formatHitboxValue = (value: number) => Number(value.toFixed(4))
const formatRotationValue = (value: number) => `${value.toFixed(0)} deg`

export default function RoomPreviewHitboxDebugControls({
  resetAllHitboxes,
  resetHitbox,
  selectedConfig,
  selectedTarget,
  setSelectedTarget,
  snapshot,
  updateHitboxValue,
}: RoomPreviewHubHitboxCalibration) {
  const handleTargetChange = (event: ChangeEvent<HTMLSelectElement>) => {
    setSelectedTarget(event.currentTarget.value as RoomPreviewHubHitboxTarget)
  }

  const handleValueChange =
    (
      field: RoomPreviewHitboxVectorField,
      axis: RoomPreviewHitboxVectorAxis,
    ) =>
    (event: ChangeEvent<HTMLInputElement>) => {
      updateHitboxValue({
        axis,
        field,
        target: selectedTarget,
        value: Number(event.currentTarget.value),
      })
    }

  const copyHitboxSnapshot = async () => {
    await window.navigator.clipboard.writeText(snapshot)
  }

  return (
    <section className="grid gap-3 border-b border-border-default/70 px-4 py-3">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <h3 className="text-sm font-semibold text-fg-primary">
            Hitbox calibration
          </h3>
          <p className="text-[11px] leading-4 text-fg-muted">
            Hitboxes are visible only while light debug is open.
          </p>
        </div>
        <button
          className="flex h-8 items-center justify-center gap-1.5 rounded-lg border border-border-default bg-surface-subtle px-2.5 text-xs font-semibold text-fg-secondary transition hover:bg-surface-muted"
          onClick={() => void copyHitboxSnapshot()}
          type="button"
        >
          <Copy aria-hidden className="size-3.5" />
          Copy
        </button>
      </div>

      <label className="grid gap-1.5 text-[11px] font-medium text-fg-muted">
        Target
        <select
          className="h-9 rounded-lg border border-border-default bg-surface-subtle px-2.5 text-xs font-semibold text-fg-secondary outline-none"
          value={selectedTarget}
          onChange={handleTargetChange}
        >
          {ROOM_PREVIEW_HUB_HITBOX_TARGETS.map((target) => (
            <option key={target} value={target}>
              {ROOM_PREVIEW_HUB_HITBOX_LABELS[target]}
            </option>
          ))}
        </select>
      </label>

      {HITBOX_NUMBER_FIELDS.map(({ field, label }) => (
        <div
          className="grid gap-2 rounded-xl border border-border-default/70 bg-surface-subtle p-3"
          key={field}
        >
          <div className="flex items-center justify-between gap-3">
            <h4 className="text-xs font-semibold text-fg-secondary">{label}</h4>
            <span className="text-[10px] uppercase tracking-[0.06em] text-fg-muted">
              {selectedTarget}
            </span>
          </div>
          <div className="grid grid-cols-3 gap-2">
            {HITBOX_AXES.map((axis, axisIndex) => (
              <label
                className="grid gap-1.5 text-[11px] font-medium text-fg-muted"
                key={axis}
              >
                {axis.toUpperCase()}
                <input
                  className="h-8 rounded-lg border border-border-default bg-surface-default px-2 text-xs font-semibold tabular-nums text-fg-primary outline-none"
                  step={0.01}
                  type="number"
                  value={formatHitboxValue(selectedConfig[field][axisIndex])}
                  onChange={handleValueChange(field, axis)}
                />
              </label>
            ))}
          </div>
        </div>
      ))}

      <div className="grid gap-3 rounded-xl border border-border-default/70 bg-surface-subtle p-3">
        <div className="flex items-center justify-between gap-3">
          <h4 className="text-xs font-semibold text-fg-secondary">Rotation</h4>
          <span className="text-[10px] uppercase tracking-[0.06em] text-fg-muted">
            degrees
          </span>
        </div>
        <div className="grid gap-2">
          {HITBOX_AXES.map((axis, axisIndex) => (
            <label
              className="grid gap-1.5 text-[11px] font-medium text-fg-muted"
              key={axis}
            >
              <span className="flex items-center justify-between gap-3">
                <span>Rotate {axis.toUpperCase()}</span>
                <span className="tabular-nums text-fg-primary">
                  {formatRotationValue(selectedConfig.rotation[axisIndex])}
                </span>
              </span>
              <input
                className="h-2 w-full accent-primary-500"
                max={180}
                min={-180}
                step={1}
                type="range"
                value={selectedConfig.rotation[axisIndex]}
                onChange={handleValueChange('rotation', axis)}
              />
            </label>
          ))}
        </div>
      </div>

      <div className="grid grid-cols-2 gap-2">
        <button
          className="flex h-9 items-center justify-center gap-1.5 rounded-lg border border-border-default bg-surface-subtle text-xs font-semibold text-fg-secondary transition hover:bg-surface-muted"
          onClick={() => resetHitbox(selectedTarget)}
          type="button"
        >
          <RotateCcw aria-hidden className="size-3.5" />
          Reset target
        </button>
        <button
          className="flex h-9 items-center justify-center gap-1.5 rounded-lg border border-border-default bg-surface-subtle text-xs font-semibold text-fg-secondary transition hover:bg-surface-muted"
          onClick={resetAllHitboxes}
          type="button"
        >
          <RotateCcw aria-hidden className="size-3.5" />
          Reset all
        </button>
      </div>
    </section>
  )
}
