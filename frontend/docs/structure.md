# Project Structure

## Layer Concepts

| Layer   | Definition                                                        | Folder                                              |
|---------|-------------------------------------------------------------------|-----------------------------------------------------|
| App     | Routing entry points and metadata only                            | `app/`                                              |
| World   | 3D scene — one folder per scene                                   | `worlds/`                                           |
| View    | Full-screen unit swapped inside a Page based on runtime condition | `features/{name}/views/` or `worlds/{scene}/views/` |
| Feature | Business capability; promoted here when used by 2+ views          | `features/`                                         |
| Shared  | Project-wide utilities, components, hooks, stores                 | `shared/`                                           |

Feature placement rule:
- Used in only 1 view → keep inside that view's folder (hooks/, sections/, etc.)
- Used in 2+ views → move to `features/`
- Pure utility function → `shared/utils/` from the start (never "lift when needed")

## Layer Hierarchy

Screens are assembled bottom-up:

```
Page      → 1:1 with a URL route. Owns routing only.
  └── View      → Full-screen unit swapped by condition (auth state, step, mode).
        └── Section   → Independent UI block within a View (header, toolbar, panel).
              └── Component → Leaf UI element within a Section.
```

**View** — A full-screen content unit rendered inside a Page. Multiple Views can exist under one Page
and are swapped based on runtime conditions (login state, onboarding step, feature mode, etc.).

```
Example:
CanvasPage
├── CanvasMainView    ← shown when user is active
└── CanvasLoginView   ← shown when user is unauthenticated
```

**Section** — An independent, visually distinct UI block that composes a View.
Sections own their own markup and may have view-scoped hooks co-located inside them.

```
Example:
CanvasMainView
├── CanvasToolbar     ← section
├── CanvasBoard       ← section
└── CanvasFooter      ← section
```

**Component** — A leaf UI element inside a Section. Has no knowledge of business logic unless it is
a feature-coupled component (see [docs/component.md](component.md)).

### What qualifies as a Feature

A feature is a capability expressible as **"the user can do X"**.

```
✅ Feature examples
fortune/          → "the user can receive a fortune memo"
relay-drawing/    → "the user can participate in relay drawing"
label-printer/    → "the user can print a label"

❌ Not a feature — do not create these as feature folders
CanvasMainView    → this is a View, not a feature
CanvasHeader      → this is a Section, not a feature
useCanvasScroll   → this is a view-scoped hook, keep it in the view folder
```

A feature folder contains: API calls, business logic hooks, Zustand store,
and feature-coupled components (components that directly import feature hooks).
It does NOT contain: pure UI components (→ `shared/components/`), view layout (→ view folder).

## View-Scoped Hook Placement

Hooks that belong to a single View live inside that View's folder, not in `features/`.

| Hook type                           | Example                  | Location                                       |
|-------------------------------------|--------------------------|------------------------------------------------|
| View UI state (layout, open/close)  | `useCanvasLayout.ts`     | `features/{name}/views/{View}/hooks/`          |
| View lifecycle (enter/exit effects) | `useCanvasEffect.ts`     | `features/{name}/views/{View}/hooks/`          |
| View-specific event handling        | `useCanvasKeyboard.ts`   | `features/{name}/views/{View}/hooks/`          |
| Multi-view reusable business logic  | `usePrinting.ts`         | `features/printing/hooks/`                     |

**Promotion rule:** Start inside the View. Move to `features/` only when a second View needs it.

View-scoped hooks are allowed to import from `features/` (consuming feature logic),
but `features/` hooks must never import from a view folder.

## Dependency Direction

```
app → worlds · features → shared
```

Cross-layer rules:
- `features/` ↔ `features/`: no direct import — use `shared/stores/`
- `worlds/` ↔ `features/`: no direct import — `use*Interaction.ts` may write to feature stores only
- `shared/`: importable from any layer

## Folder Structure

```
src/
├── app/
│   ├── layout.tsx
│   ├── (service)/           # URL-invisible group — normal service pages
│   │   ├── layout.tsx
│   │   ├── page.tsx         # URL: /
│   │   ├── hub/page.tsx
│   │   ├── relay-drawing/page.tsx
│   │   ├── infinite-canvas/page.tsx
│   │   ├── flipbook/page.tsx
│   │   └── share/[id]/page.tsx
│   └── admin/               # URL: /admin/* — backoffice
│       ├── layout.tsx       # AdminAuthGuard + sidebar layout
│       └── {section}/page.tsx
│
├── worlds/
│   ├── {scene}/
│   │   ├── {Scene}Loader.tsx    # 'use client' + dynamic(ssr:false) wrapper
│   │   ├── {Scene}Canvas.tsx    # owns <Canvas> + <Physics>
│   │   ├── {Scene}Scene.tsx     # scene content root
│   │   ├── constants.ts
│   │   ├── use{Scene}Interaction.ts
│   │   └── objects/
│   │       └── {Name}Mesh.tsx
│   ├── _infra/              # one-per-scene singletons
│   │   ├── Lighting.tsx
│   │   └── Character.tsx
│   └── _shared/mesh/        # multi-instance reusable meshes
│       ├── index.ts
│       └── {Name}Mesh.tsx
│
├── features/
│   └── {feature-name}/
│       ├── index.ts
│       ├── {FeatureName}Page.tsx         # route entry — layout only, no logic
│       ├── {FeatureName}Modal.tsx        # DOM overlay modal
│       ├── {FeatureName}Stage.tsx        # Konva Stage owner (optional)
│       ├── {FeatureName}Visual.tsx       # independent <Canvas> owner (optional)
│       ├── use{FeatureName}.ts           # business logic hook
│       ├── {featureName}Store.ts         # Zustand store (optional)
│       ├── views/                        # full-screen units swapped by condition
│       │   └── {FeatureName}MainView/
│       │       ├── index.tsx             # view root — assembles sections
│       │       ├── hooks/                # view-scoped hooks (UI state, lifecycle)
│       │       │   └── use{Name}.ts
│       │       └── sections/             # independent UI blocks within this view
│       │           └── {SectionName}/
│       │               ├── index.tsx
│       │               └── hooks/        # section-scoped hooks (optional)
│       └── components/                   # feature-coupled components (import feature hooks directly)
│           └── {Name}.tsx
│
└── shared/
    ├── apis/        { index.ts, {domain}Api.ts }
    ├── assets/      # bundler-managed: svg, glb, mp3 — NOT public/
    ├── components/  { index.ts, {Name}/{ {Name}.tsx, index.ts } }
    ├── config/      { index.ts, runtime.ts }
    ├── constants/   { index.ts, {name}.ts }
    ├── hooks/       { index.ts, use{Name}.ts }
    ├── layouts/     { index.ts, {Name}Layout.tsx }
    ├── libs/        { index.ts, apiClient.ts, adminApiClient.ts, cn.ts }
    ├── stores/      { index.ts, {name}Store.ts }
    ├── styles/      { index.css, tokens/, layers/ }
    ├── types/       { index.ts, {domain}.ts }
    └── utils/       { index.ts, {name}.ts }
```

## 3D Scene Entry Chain

```
[1] app/(service)/page.tsx           Server Component — routing + OG metadata
        ↓ import
[2] worlds/{scene}/{Scene}Loader.tsx  'use client' + dynamic(ssr:false)
        ↓ dynamic import
[3] worlds/{scene}/{Scene}Canvas.tsx  owns <Canvas> + <Physics>
        ↓
[4] worlds/{scene}/{Scene}Scene.tsx   assembles Lighting, Character, objects
```

`_infra/` vs `_shared/mesh/`: "Can there be multiple of this in a scene?" → `_shared/mesh/`; "Exactly one per scene?" → `_infra/`

> For folder/file naming, suffix conventions, file placement, folderization, and barrel rules see [docs/naming.md](naming.md).
