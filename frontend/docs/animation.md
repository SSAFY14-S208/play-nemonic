# Animation Rules

## motion — When to Use

Use `motion` for all DOM animations by default:

| Use case | `motion` API |
|----------|-------------|
| Enter / exit transitions | `AnimatePresence` + `initial` / `animate` / `exit` |
| Scroll-triggered | `whileInView` |
| Gesture-driven | `whileHover`, `whileTap`, `useDragControls` |
| One-shot UI transition | `animate` prop |
| Reusable animation component | `motion.*` + `variants` |

**Never use `motion.*` inside R3F `<Canvas>`** — see `docs/r3f.md` for in-canvas animation patterns.

## CSS @keyframes — When Justified

Write `@keyframes` in a feature CSS file **only** when all conditions apply:

1. The animation runs continuously (`infinite`) — ambient loops: drift, breathe, pulse, sway
2. The keyframe steps reference CSS custom properties as parameters (`calc(var(--my-offset) + ...)`)
3. OR bulk pause via `animation-play-state` on a parent selector is required

```css
/* features/fortune/fortune.css — justified example */
@keyframes fortune-2d-popo-breathe {
  0%, 100% { transform: translate3d(-50%, -50%, 0) rotate(-0.28deg) scale(1); }
  50%       { transform: translate3d(-50%, -50.8%, 0) rotate(0.2deg) scale(1.012); }
}

/* Bulk pause during viewport resize — justifies CSS over motion */
body.is-resizing [data-fortune-stage-root] * {
  animation-play-state: paused !important;
}
```

Register the animation in `theme.css` `@theme` so Tailwind generates the utility class:

```css
/* shared/styles/layers/theme.css */
@theme {
  --animate-fortune-popo-breathe: fortune-2d-popo-breathe 7.6s ease-in-out infinite;
}
```

```tsx
/* Use as a Tailwind utility class — not as an inline style */
<div className="animate-fortune-popo-breathe" />
```

**Do not use a feature CSS file for:** entrance transitions, click responses, scroll animations, or anything expressible as `motion` props.

## Custom Fonts

Use `next/font/local` instead of `@font-face` in feature CSS:

```ts
// shared/fonts.ts
import localFont from "next/font/local";
export const graceSerif = localFont({
  src: "../public/fonts/GraceSerif-Regular.otf",
  display: "swap",
});
```

Register the font in `theme.css` `@theme` → use as `font-{name}` Tailwind utility class.
Do not write `@font-face` directly in feature CSS files.

## Decision Guide

```
New animation needed
│
├─ Runs infinitely in a loop (drift / breathe / pulse)?
│   └─ AND uses CSS custom property parameters OR needs bulk pause control?
│       ├─ YES → @keyframes in features/{name}/{name}.css
│       └─ NO  → motion
│
├─ Enter / exit / scroll / gesture?
│   └─ → motion
│
└─ Inside R3F <Canvas>?
    └─ → useRef + direct Three.js mutation (no motion, no CSS animation)
```
