# Konva / react-konva Rules

## Placement

- Use only for 2D canvas features; place files in `features/` as `*Stage.tsx`
- Confirm `'use client'` or `dynamic(..., { ssr: false })` boundary — react-konva components must not leak outside a client boundary

## Stage Composition

Hierarchy: `Stage > Layer > Shape`

`*Stage.tsx` owns `<Stage>` and top-level `<Layer>` composition only. Extract everything else into child components:

```tsx
// ✅ Correct — *Stage.tsx owns Stage/Layer only
export default function RelayDrawingStage() {
  return (
    <Stage width={width} height={height}>
      <Layer>
        <DrawingCanvas />       {/* strokes */}
        <SelectionOverlay />    {/* selection box */}
        <CursorPreview />       {/* cursor indicator */}
        <GridGuide />           {/* grid hint */}
      </Layer>
    </Stage>
  );
}

// ❌ Wrong — shapes and overlays declared inline in Stage
export default function RelayDrawingStage() {
  return (
    <Stage width={width} height={height}>
      <Layer>
        <Rect x={0} y={0} width={100} height={100} fill="red" />
        <Circle x={50} y={50} radius={20} />
        {/* ... all shapes here ... */}
      </Layer>
    </Stage>
  );
}
```

Extract into child components:
- Grids and hint areas
- Selection boxes
- Cursor previews
- Tool overlays
- Any group of shapes with shared logic
