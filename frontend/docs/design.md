# Design System

## Styling Rules — Non-Negotiable

- Use Tailwind utility classes only
- Creating `.module.css` files is **FORBIDDEN**
- Inline `style={{ }}` props are **FORBIDDEN**
- Exception: `@layer utilities` in `shared/styles/layers/utilities.css` only

## CSS File Structure

```
shared/styles/
├── index.css           ← entry point (imported in app/layout.tsx only)
├── tokens/
│   ├── primitive/      ← raw values — NEVER reference directly in components
│   └── semantic/       ← role-based tokens — ALWAYS use these in components
└── layers/
    ├── base.css        ← global reset + Pretendard font
    ├── theme.css       ← Tailwind theme integration
    └── utilities.css   ← custom utility classes (h1-b, body-r, etc.) go here only
```

CSS layer loading order (`shared/styles/index.css`): primitive tokens → semantic tokens → Tailwind layers (theme → utilities → base).

## Token Hierarchy

```
Primitive Tokens  →  Semantic Tokens
(raw values)          (role-based mapping)
```

- **Primitive** (`shared/styles/tokens/primitive/`): raw color palette, spacing scale — **never reference directly in components**
- **Semantic** (`shared/styles/tokens/semantic/`): role-based tokens (`bg-surface-*`, `text-fg-*`, `border-*`) — **always use these in components**

```tsx
// ✅ Correct — semantic tokens
className="bg-surface-default text-fg-primary border-border-default"

// ❌ Wrong — primitive direct reference
className="bg-cream-50 text-brown-720"
```

## Color Tokens

| Token                    | Use                              |
|--------------------------|----------------------------------|
| `bg-primary-1`           | CTA button bg, primary emphasis  |
| `bg-primary-5`           | Active tab bg (light accent)     |
| `bg-surface-default`     | Default card/screen bg           |
| `bg-surface-subtle`      | Background above dividers        |
| `text-fg-primary`        | Primary body text                |
| `text-fg-secondary`      | Secondary text                   |
| `text-fg-disabled`       | Inactive text                    |
| `text-fg-inverse`        | Text on dark backgrounds         |
| `border-border-default`  | Default border                   |
| `text-primary-2`         | Links, active icon color         |

## Typography Utility Classes

Use bundled utility classes. **Never combine raw Tailwind font utilities directly.**

```
h1-b (32px/700)    h2-b (24px/700)    h3-b (20px/700)    h4-b (16px/700)
body-l-b / body-l-m / body-l-r  (16px · 700/500/400)
body-b  / body-m  / body-r      (14px · 700/500/400)
caption-b / caption-m / caption-r (12px · 700/500/400)
```

```tsx
// ✅ Correct
className="h2-b text-fg-primary"
className="body-r text-fg-secondary"

// ❌ Wrong — raw Tailwind font class combination
className="text-2xl font-bold leading-tight"
```

## Spacing

CSS variable scale maps 1:1 to Tailwind scale.

```
--space-1: 4px   → p-1, gap-1
--space-2: 8px   → p-2, gap-2
--space-4: 16px  → p-4, gap-4  (default card interior)
--space-6: 24px  → p-6         (section interior)
```

## Border Radius

```
rounded-[var(--radius-sm)]  : 4px  — badges, tags
rounded-[var(--radius-md)]  : 8px  — buttons, inputs
rounded-[var(--radius-lg)]  : 12px — cards
rounded-[var(--radius-xl)]  : 16px — modals, bottom sheets
rounded-full                : circle — avatars
```

## Shadow

```
shadow-sm      : default card
shadow-md      : dropdown
shadow-lg      : modal
shadow-soft-lg : navigation bar and soft-shadow elements
```

## Z-Index

```
z-[var(--z-dropdown)] : 200  — dropdown
z-[var(--z-sticky)]   : 300  — sticky header
z-[var(--z-overlay)]  : 400  — dim backdrop
z-[var(--z-modal)]    : 500  — modal
z-[var(--z-toast)]    : 600  — toast
```

## cn() — Always Use for Conditional Class Merging

```ts
// shared/libs/cn.ts
import { clsx, type ClassValue } from 'clsx'
import { twMerge } from 'tailwind-merge'
export function cn(...inputs: ClassValue[]) { return twMerge(clsx(inputs)) }
```

```tsx
// ✅ Correct
<div className={cn("body-r text-fg-primary", isActive && "text-primary-2")} />

// ❌ Wrong — string concatenation
<div className={`body-r text-fg-primary ${isActive ? "text-primary-2" : ""}`} />
```

### State-driven styling — JSX state in cn(), NOT data-* attributes

`data-*` + CSS child selector patterns are **FORBIDDEN** for conditional visual state.
Pass the JSX boolean directly to `cn()` instead.

```tsx
// ❌ Wrong — data-* attribute + CSS child selector for runtime state
<button data-playing={isBgmPlaying} className="group">
  <Music2 className="group-data-[playing=true]:animate-pulse text-primary" />
</button>

// ✅ Correct — JSX state directly in cn()
<button>
  <Music2 className={cn('text-primary', isBgmPlaying && 'animate-pulse')} />
</button>
```

The `group` + `group-data-[*]:` pattern is only justified when a **parent DOM node** drives
child styling and the parent's state is impossible to thread as a prop (e.g., third-party
wrappers). For first-party components this situation never arises — use `cn()` directly.

## CVA — Variant Components

```tsx
import { cva, type VariantProps } from 'class-variance-authority'
import { cn } from '@/shared/libs'

const buttonVariants = cva('body-b rounded-[var(--radius-md)] transition-colors', {
  variants: {
    variant: {
      primary: 'bg-primary-1 text-fg-inverse',
      secondary: 'bg-surface-subtle text-fg-primary border border-border-default',
    },
    size: {
      sm: 'px-3 py-1.5',
      md: 'px-4 py-2',
    },
  },
  defaultVariants: { variant: 'primary', size: 'md' },
})

interface ButtonProps
  extends React.ButtonHTMLAttributes<HTMLButtonElement>,
    VariantProps<typeof buttonVariants> {}

export function Button({ className, variant, size, ...props }: ButtonProps) {
  return <button className={cn(buttonVariants({ variant, size }), className)} {...props} />
}
```

## Shared Component Folder Structure

```
shared/components/
├── index.ts
└── Button/
    ├── Button.tsx
    ├── Button.types.ts   ← only when Props are complex
    └── index.ts
```

## When a Feature Needs a Distinct Visual Identity

Extend the design system — never create a feature-scoped exception:

1. New colors or surfaces → add semantic tokens to `shared/styles/tokens/semantic/`
2. Custom fonts → `next/font/local` → register in `theme.css` `@theme` → use as `font-{name}` utility class
3. Infinite ambient animations → feature-scoped `@keyframes` in a feature CSS file (see [docs/animation.md](animation.md))
4. If the design system genuinely cannot cover the case → bring to team discussion → update the design system globally
