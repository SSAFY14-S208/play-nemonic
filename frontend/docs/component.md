# Component & Hook Rules

## UI / Logic Separation — Always Enforced

`*.tsx` files render only. Extract any logic below to a separate file **before writing the component**.

**Must be in `use*.ts` hook:**
- API calls
- Data transformation / calculation
- `useFrame`-based 3D logic
- Game logic (distance detection, collision)

**Must be in `*Store.ts`:**
- State shared across multiple components

**Allowed inside component:**
- Pure UI state: `isOpen`, `isHovered` (`useState`)
- Simple UI flow: button click → open modal

```tsx
// ✅ Correct
export default function LabelPrinter() {
  const { labels, addLabel } = useLabelPrinter()  // logic in hook
  return <div>{labels.map(...)}</div>
}

// ❌ Wrong — API call inside component
export default function LabelPrinter() {
  const addLabel = async () => {
    const result = await api.post("/labels", { ... })  // must move to hook
  }
}
```

## When to Split a Component

Split immediately when any of the following is true:
- 2+ JSX-returning functions exist in the same file
- A child component receives props it doesn't render itself (pass-through smell)
- The same JSX block appears 2+ times
- The file exceeds 100 lines of JSX

## When to Extract a Hook

Extract to `use*.ts` when any of the following is true:
- 2+ `useEffect` calls in one file
- Any API call exists in the component
- The same state logic is needed in 2+ places

## Component Spectrum

| Type            | Location                      | Characteristics                            |
|-----------------|-------------------------------|--------------------------------------------|
| Pure UI         | `shared/components/`          | No business logic, all behavior via props  |
| Smart UI        | `shared/components/`          | Owns UI state (hover, focus) only          |
| Feature-coupled | `features/{name}/components/` | Imports feature hooks directly             |

## Feature Component Promotion

Start inside the view. Promote when reuse occurs:

1. Used in 1 view → keep in view folder
2. Used in 2+ views, same logic → `features/{name}/components/`
3. Used in 2+ views, UI differs → share only the hook, build separate UIs
4. Business rules diverge → split into separate features

## Rendering Structure Rules

- Render repeated items from array constants with `map()` and stable `key`s
- Stable `key`: use unique id or slug; index is acceptable only when order never changes
- Split large visual sections and repeated item markup into child components
- Maintain prop flow even if a child only passes through; lift to global state only when prop chain is deep or another feature shares the data
- Konva `*Stage.tsx` owns Stage/Layer composition only — extract shapes, hints, guides, cursors, and tool overlays into child components

## Accessibility

- Meaningful `<Image>` must have `alt` describing what the image shows
- Purely decorative images: `alt=""` + `aria-hidden={true}`
- Never add `aria-hidden` to non-decorative content
