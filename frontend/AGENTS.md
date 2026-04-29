# Agent Instructions — Frontend

> Full conventions are in README.md. This file is a quick-action reference for AI agents.

## Stack

Next.js 16 App Router · TypeScript · Tailwind CSS v4
@react-three/fiber · @react-three/drei · @react-three/rapier · Zustand · three
ky · @base-ui/react · lucide-react · motion
konva · react-konva · pnpm · babel-plugin-react-compiler
class-variance-authority · clsx · tailwind-merge · Pretendard Variable

---

## 초기 설치 (필수 — 반드시 전부 설치)

**기술 스택에 명시된 패키지는 프로젝트 시작 시 빠짐없이 설치해야 합니다.**
누락 시 peer 의존성 오류가 발생하거나 런타임에 모듈을 찾지 못합니다.

```bash
pnpm add next react react-dom @react-three/fiber @react-three/drei @react-three/rapier three
pnpm add @base-ui/react ky lucide-react motion konva react-konva zustand
pnpm add class-variance-authority clsx tailwind-merge
pnpm add -D typescript @types/node @types/react @types/react-dom @types/three eslint eslint-config-next tailwindcss @tailwindcss/postcss postcss
```

Before writing any code, verify `package.json` contains **all** of the following. Install any that are missing:

| Package                 | Purpose                                   |
| ----------------------- | ----------------------------------------- |
| `@react-three/fiber`    | R3F — Three.js React renderer             |
| `@react-three/drei`     | R3F helpers (Grid, Sky, etc.)             |
| `@react-three/rapier`   | Physics — collisions, boundaries, sensors |
| `three`                 | Three.js core                             |
| `zustand`               | Global state                              |
| `ky`                    | HTTP client                               |
| `@base-ui/react`        | 헤드리스 UI 프리미티브                    |
| `lucide-react`          | Icons                                     |
| `motion`                | DOM animation                             |
| `konva` + `react-konva`       | 2D canvas                                 |
| `class-variance-authority`   | Component variant management (CVA)        |
| `clsx`                       | Conditional class merging                 |
| `tailwind-merge`             | Tailwind class collision-free merging     |

---

## Variable & Parameter Naming

Code must be readable without comments. Single-letter or cryptic abbreviations are forbidden.

| ❌ Forbidden | ✅ Use instead            |
| ------------ | ------------------------- |
| `p`          | `doorOpenProgress`        |
| `u`          | `uniformScale`            |
| `d`          | `delta`                   |
| `t`          | `texture` / `elapsedTime` |
| `BH`         | `BODY_HEIGHT`             |
| `BD`         | `BODY_DEPTH`              |
| `HW`         | `HALF_WIDTH`              |
| `CY`         | `ARCH_CENTER_Y`           |
| `cb`         | `onComplete` / `callback` |
| `idx`        | `index`                   |

Rules:

- **Constants**: descriptive `SCREAMING_SNAKE_CASE` — `PRINTER_BODY_HEIGHT`, `HUD_DISTANCE`, `IDENTITY_QUATERNION`
- **Local variables**: meaningful `camelCase` — `doorOpenProgress`, `uniformScale`, `cameraForward`
- **Parameters**: function signature alone must convey intent — `delta` not `d`, `progress` not `p`
- **Loop counter exception**: `i`, `j` are allowed for simple numeric indices only

```ts
// ❌ Wrong
const BH = 0.7;
const p = doorOpenProgress.current;
meshRef.current.scale.x = u * (1 - p);

// ✅ Correct
const BODY_HEIGHT = 0.7;
const progress = doorOpenProgress.current;
meshRef.current.scale.x = uniformScale * (1 - progress);
```

---

## Layer Architecture

```
app/          → Routing entry points + metadata only (thin layer)
worlds/       → 3D scenes — per-scene canvas chain, always client-side
features/     → Feature UI + logic (kebab-case folder names)
shared/       → Hooks, stores, lib, UI primitives (available to all layers)
```

**Dependency direction — one way only:**

```
app → worlds · features → shared
```

Cross-layer rules:

- `features/` ↔ `features/`: no direct import — use `shared/stores/`
- `worlds/` ↔ `features/`: no direct import — `use*Interaction.ts` may write to feature stores only
- `shared/`: importable from any layer

### App Route Groups

```
app/
├── layout.tsx
├── (service)/           # URL-invisible route group — normal service pages
│   ├── layout.tsx       # (optional) shared service layout
│   ├── page.tsx         # URL: /
│   ├── hub/page.tsx     # URL: /hub
│   ├── relay-drawing/page.tsx
│   ├── infinite-canvas/page.tsx
│   ├── flipbook/page.tsx
│   └── share/[id]/page.tsx
└── admin/               # URL: /admin/* — backoffice (URL-exposed for middleware)
    ├── layout.tsx       # AdminAuthGuard + sidebar layout
    └── {section}/page.tsx
```

`(service)/` keeps user-facing URLs clean (`/`, `/hub`) while enabling a shared layout.
`admin/` exposes the prefix so middleware can gate with `pathname.startsWith('/admin')`.

### worlds/ Per-Scene Pattern

Each 3D route has its own 4-stage entry chain. **There is no single global `WorldCanvas.tsx`.**

```
worlds/
├── landing/
│   ├── LandingLoader.tsx    # 'use client' + dynamic(ssr:false) wrapper
│   ├── LandingCanvas.tsx    # owns <Canvas> + <Physics>
│   ├── LandingScene.tsx     # scene content root
│   ├── constants.ts
│   ├── GroundMesh.tsx       # (optional)
│   ├── useLandingInteraction.ts  # (optional)
│   └── objects/
│       └── {Name}Mesh.tsx
├── hub/
│   ├── HubLoader.tsx
│   ├── HubCanvas.tsx
│   ├── HubScene.tsx
│   ├── useHubInteraction.ts
│   └── objects/
│       └── {Name}Mesh.tsx
├── _infra/                  # always-present singletons (one instance per scene)
│   ├── Lighting.tsx
│   └── Character.tsx
└── _shared/mesh/            # multi-instance reusable meshes
    ├── index.ts
    └── {Name}Mesh.tsx
```

4-stage chain for each 3D page:

```
[1] app/(service)/page.tsx              Server Component — routing + OG metadata
        ↓ import
[2] worlds/{scene}/{Scene}Loader.tsx    'use client' + dynamic(ssr:false)
        ↓ dynamic import
[3] worlds/{scene}/{Scene}Canvas.tsx    owns <Canvas> + <Physics>
        ↓
[4] worlds/{scene}/{Scene}Scene.tsx     scene content (Lighting, Character, objects)
```

`_infra/` vs `_shared/mesh/` decision:
- "Can there be multiple of this in a scene?" → `_shared/mesh/`
- "Is there exactly one of this per scene?" → `_infra/`

---

## File Placement — Quick Reference

| What you are creating            | Where it goes                    | Filename pattern               |
| -------------------------------- | -------------------------------- | ------------------------------ |
| Routing entry point              | `app/(service)/` or `app/admin/` | `page.tsx`, `layout.tsx`       |
| OG metadata                      | `app/[route]/page.tsx`           | `generateMetadata` export      |
| 3D ssr:false wrapper             | `worlds/{scene}/`                | `{Scene}Loader.tsx`            |
| 3D canvas + physics owner        | `worlds/{scene}/`                | `{Scene}Canvas.tsx`            |
| Scene content root               | `worlds/{scene}/`                | `{Scene}Scene.tsx`             |
| Scene-specific 3D object         | `worlds/{scene}/objects/`        | `{Name}Mesh.tsx`               |
| Scene interaction logic          | `worlds/{scene}/`                | `use{Scene}Interaction.ts`     |
| Always-present singleton         | `worlds/_infra/`                 | `{Name}.tsx`                   |
| Reusable multi-instance mesh     | `worlds/_shared/mesh/`           | `{Name}Mesh.tsx`               |
| Feature page (URL route)         | `features/{feature-name}/`       | `{FeatureName}Page.tsx`        |
| Feature modal (DOM overlay)      | `features/{feature-name}/`       | `{FeatureName}Modal.tsx`       |
| Feature Konva canvas             | `features/{feature-name}/`       | `{FeatureName}Stage.tsx`       |
| Feature 3D canvas                | `features/{feature-name}/`       | `{FeatureName}Visual.tsx`      |
| Feature business logic           | `features/{feature-name}/`       | `use{FeatureName}.ts`          |
| Feature state                    | `features/{feature-name}/`       | `{featureName}Store.ts`        |
| Admin feature                    | `features/admin/{section}/`      | `{Section}Page.tsx`            |
| Cross-feature shared state       | `shared/stores/`                 | `{name}Store.ts`               |
| Shared hook                      | `shared/hooks/`                  | `use{Name}.ts`                 |
| API call function                | `shared/apis/`                   | `{domain}Api.ts`               |
| API client config                | `shared/libs/`                   | `apiClient.ts`                 |
| cn() utility                     | `shared/libs/`                   | `utils.ts`                     |
| Env config / feature flags       | `shared/config/`                 | `runtime.ts`, `flags.ts`       |
| 공용 UI 컴포넌트                 | `shared/components/{Name}/`      | `{Name}.tsx` + `index.ts`      |
| Page layout component            | `shared/layouts/`                | `{Name}Layout.tsx`             |
| CSS token system                 | `shared/styles/`                 | `index.css`, `tokens/`, `layers/` |
| Static assets (svg, glb, mp3)   | `shared/assets/`                 | `{name}.{ext}`                 |

---

## Resource Folderization Rule — Enforced

When a resource type has **1 file** → keep it flat.
When the **2nd file of the same type** is added → **create folder + `index.ts` barrel in the same PR**.

```
# 1 file — flat is correct
features/relay-drawing/
└── useRelayDrawing.ts

# 2nd hook added — folderize immediately (same PR)
features/relay-drawing/
└── hooks/
    ├── index.ts                   ← barrel (required)
    ├── useRelayDrawing.ts         ← moved from flat
    └── useRelayDrawingHistory.ts  ← new file
```

Resource types: `use*.ts` hooks · constants · utils · types · components
Each resource type evolves independently — hooks may be folderized while constants stay flat.

**PR is blocked if:**
- 2+ files of the same resource type exist without a folder
- A folder exists without an `index.ts` barrel

---

## Admin / Backoffice

Authentication flow:
1. `middleware.ts` → checks login (`pathname.startsWith('/admin')` → redirect to `/login` if unauthenticated)
2. `app/admin/layout.tsx` → `<AdminAuthGuard>` checks admin role (renders 403 if unauthorized)

Import rules:
```
✅ features/admin/*  →  shared/*
❌ features/admin/*  →  features/{normal-feature}/*   (forbidden)
❌ features/{normal}/* →  features/admin/*             (forbidden)
```

- `robots.txt`: `Disallow: /admin/`
- Admin layout owns sidebar + top navigation — do not repeat in individual page components

---

## UI / Logic Separation — Always Enforced

`*.tsx` files render only. Any logic below must be extracted to a separate file **before writing the component**.

**Must be in `use*.ts` hook:**

- API calls
- Data transformation / calculation
- `useFrame`-based 3D logic
- Game logic (distance detection, collision)

**Must be in `*Store.ts`:**

- State shared across multiple components

**Allowed inside component:**

- Pure UI state: `isOpen`, `isHovered` (useState)
- Simple UI flow: button click → open modal

```tsx
// ✅ Correct
export default function LabelPrinter() {
  const { labels, addLabel } = useLabelPrinter()
  return <div>{labels.map(...)}</div>
}

// ❌ Wrong — API call inside component
export default function LabelPrinter() {
  const addLabel = async () => {
    const result = await api.post("/labels", { ... })  // must move to hook
  }
}
```

---

## Next.js Client/Server Boundary

### When to add `'use client'`

**Add it when the file uses:**

- React hooks: `useState`, `useEffect`, `useRef`, etc.
- Event handlers: `onClick`, `onChange`, etc.
- Browser-only APIs: `window`, `localStorage`, etc.
- WebGL-related code: R3F, drei, rapier

**Do NOT add it when:**

- The component only renders static markup
- The component fetches data with `async/await` on the server

### app/ files — server by default

- `app/` files are routing entry points and metadata only — real UI comes from `worlds/` and `features/`
- R3F Canvas must always be loaded with `dynamic + ssr: false` inside a `{Scene}Loader.tsx` Client Component
- Common layout (Header, Footer, etc.) goes in `app/layout.tsx` only — never repeated in `page.tsx`

```tsx
// worlds/landing/LandingLoader.tsx  ← 'use client' wrapper
"use client";
import dynamic from "next/dynamic";
const LandingCanvas = dynamic(() => import("./LandingCanvas"), { ssr: false });
export default function LandingLoader() {
  return <LandingCanvas />;
}

// app/(service)/page.tsx  ← Server Component, no dynamic/ssr:false here
import LandingLoader from "@/worlds/landing/LandingLoader";
export default function Page() {
  return <LandingLoader />;
}
```

### worlds/ — per-scene client boundary

- Declare `'use client'` in `{Scene}Loader.tsx` and `{Scene}Canvas.tsx`
- Do NOT add `'use client'` to other files inside a scene folder (propagates from Canvas)
- Do NOT declare `<Canvas>` anywhere except `{Scene}Canvas.tsx`
- Do NOT declare `<Physics>` outside `{Scene}Canvas.tsx`

### features/ — explicit declaration

- 3D features: `*Visual.tsx` owns its own `<Canvas>` + declares `'use client'`
- Pure UI features: may stay as server components

---

## API Calls

This project uses a Spring/NestJS backend. Do NOT access the database directly. All data goes through the backend API.

All HTTP requests go through `shared/libs/apiClient.ts`:

```ts
import { api } from "@/shared/libs";

const data = await api.get<User[]>("/users");
const result = await api.post<Post>("/posts", { title: "..." });
```

Use native `fetch` directly only in server components for OG metadata generation.

---

## Environment Variables

- `NEXT_PUBLIC_*`: accessible in client components (e.g. `NEXT_PUBLIC_API_URL`)
- No prefix: server-only (e.g. `API_SECRET_KEY`) — always `undefined` in client components
- All env access must go through `shared/config/` — never reference `process.env.X` directly in feature code

---

## Rapier Physics — Rules & Patterns

### Physics Provider

Place `<Physics>` inside `<Canvas>` in `{Scene}Canvas.tsx` — exactly one per scene. Never add it to the scene component or per-object.

```tsx
// worlds/landing/LandingCanvas.tsx
<Canvas>
  <Physics gravity={[0, -9.81, 0]}>
    <LandingScene />
  </Physics>
</Canvas>
```

For flat/digital-twin scenes where gravity is irrelevant, use `gravity={[0, 0, 0]}`.

### RigidBody Type — Decision Table

| Type                    | Use for                                                                     |
| ----------------------- | --------------------------------------------------------------------------- |
| `kinematicPosition`     | Character; equipment/robots driven by code or external data (WebSocket/API) |
| `fixed`                 | Ground, walls, static structures                                            |
| `dynamic`               | Objects that react freely to physics (falling, being pushed)                |
| `sensor: true` Collider | Proximity detection — collision events only, no physical response           |

**Never use `dynamic` for the character** — physics simulation interferes with WASD control.

### Character Pattern

```tsx
// worlds/_infra/Character.tsx
import { RigidBody, CapsuleCollider, type RapierRigidBody } from "@react-three/rapier";

export default function Character() {
  const rb = useRef<RapierRigidBody>(null);
  useCharacterMovement(rb);
  return (
    <RigidBody ref={rb} type="kinematicPosition" colliders={false}>
      <CapsuleCollider args={[0.4, 0.4]} />
      <group>{/* 3D model */}</group>
    </RigidBody>
  );
}
```

In the movement hook, update position with `setNextKinematicTranslation()` — never mutate `position` directly.

```ts
// worlds/_infra/hooks/useCharacterMovement.ts
useFrame((_, delta) => {
  if (!rb.current) return;
  const pos = rb.current.translation();
  rb.current.setNextKinematicTranslation({ x: nextX, y: pos.y, z: nextZ });
});
```

### Digital Twin Equipment Pattern

Equipment/robots driven by external data (WebSocket/API) → `kinematicPosition`. Position sync logic goes in a `use{Name}.ts` hook, not in the component.

```tsx
// worlds/{scene}/objects/EquipmentMesh.tsx
<RigidBody type="kinematicPosition" ref={rb}>
  <CuboidCollider args={[1, 1, 1]} />
</RigidBody>
```

### Sensor (Proximity Detection) Pattern

```tsx
<RigidBody
  type="fixed"
  sensor
  onIntersectionEnter={onEnter}
  onIntersectionExit={onExit}
>
  <SphereCollider args={[3]} />
</RigidBody>
```

Event handling logic lives in `use{Scene}Interaction.ts`.

### CharacterController & Collision — What Gets Blocked

| Mesh state                                                         | Character blocked?     |
| ------------------------------------------------------------------ | ---------------------- |
| `RigidBody` + Collider (`fixed` / `kinematicPosition` / `dynamic`) | Yes ✅                 |
| Plain `<mesh>` without `RigidBody`                                 | No — passes through ❌ |
| `sensor: true` Collider                                            | No — event only ❌     |

**Any new structure or equipment must be wrapped in `<RigidBody>` for the character to collide with it.**

```tsx
// ✅ character is blocked
<RigidBody type="fixed">
  <mesh><boxGeometry args={[3, 5, 3]} /><meshStandardMaterial /></mesh>
</RigidBody>

// ❌ character passes through
<mesh><boxGeometry args={[3, 5, 3]} /><meshStandardMaterial /></mesh>
```

### Ground & Boundary Walls

```tsx
// worlds/{scene}/GroundMesh.tsx
<RigidBody type="fixed">
  <mesh rotation={[-Math.PI / 2, 0, 0]}>
    <planeGeometry args={[50, 50]} />
    <meshStandardMaterial color="#6b8f52" />
  </mesh>
</RigidBody>
```

Invisible boundary walls → `fixed` RigidBody + `CuboidCollider`. Never use `MathUtils.clamp` on position when Rapier is present.

---

## Three.js / R3F Rules

### `useFrame` inside `setState` — Forbidden

`useFrame` runs every frame (60 fps). Calling a React `useState` setter inside it triggers 60 re-renders per second.
Manage animation values with `useRef` and mutate Three.js objects directly.

```tsx
// ❌ Wrong — 60 re-renders/s
const [intensity, setIntensity] = useState(0);
useFrame(({ clock }) => {
  setIntensity(Math.sin(clock.elapsedTime * 5));
});

// ✅ Correct — direct Three.js mutation, zero re-renders
const lightRef = useRef<PointLight>(null);
useFrame(({ clock }) => {
  if (!lightRef.current) return;
  lightRef.current.intensity = Math.sin(clock.elapsedTime * 5);
});
```

### `setInterval` / `setTimeout` animation polling — Forbidden

Never poll animation completion with `setInterval` inside R3F. Use `useFrame` condition checks instead.

```ts
// ❌ Wrong
const check = setInterval(() => {
  if (Math.abs(mesh.position.y - target) < 0.01) {
    clearInterval(check);
    onDone();
  }
}, 50);

// ✅ Correct
const checking = useRef(false);
// on animation start: checking.current = true
useFrame(() => {
  if (!checking.current || !mesh.current) return;
  if (Math.abs(mesh.current.position.y - target) < 0.01) {
    checking.current = false;
    onDone();
  }
});
```

### `LoadingManager` — One singleton per scene, shared via module

Attaching `TextureLoader` to the default `LoadingManager` pollutes drei's `useProgress`.
Declare one isolated manager per scene in a shared module file and import it everywhere in that scene.

```ts
// worlds/{scene}/textureLoader.ts
import { LoadingManager } from "three";
export const isolatedManager = new LoadingManager();

// ❌ Wrong — duplicate declaration per file
const isolatedManager = new LoadingManager(); // SomeMesh.tsx
const isolatedManager = new LoadingManager(); // someHook.ts
```

### `useEffect` — No synchronous `setState` in effect body (React Compiler rule)

With `babel-plugin-react-compiler` enabled, calling `setState` synchronously in an effect body is a compile error.
Always call `setState` inside an async IIFE.

```ts
// ❌ Wrong
useEffect(() => {
  if (!url) {
    setTexture(null);
    return;
  }
}, [url]);

// ✅ Correct
useEffect(() => {
  let cancelled = false;
  (async () => {
    if (!url) {
      if (!cancelled) setTexture(null);
      return;
    }
    // ... async load ...
  })();
  return () => {
    cancelled = true;
  };
}, [url]);
```

---

## Design System

### Token Hierarchy

```
Primitive Tokens  →  Semantic Tokens
(raw values)          (role-based mapping)
```

- **Primitive** (`shared/styles/tokens/primitive/`): raw color palette, spacing scale — **never reference directly in components**
- **Semantic** (`shared/styles/tokens/semantic/`): role-based tokens (`bg-surface-*`, `text-fg-*`, `border-*`) — **always use these in components**

### Token Rules

```tsx
// ✅ Correct — Semantic tokens
className="bg-surface-default text-fg-primary border-border-default"

// ❌ Wrong — Primitive direct reference
className="bg-cream-50 text-brown-720"
```

### Typography Utility Classes

Use bundled utility classes. **Never combine raw Tailwind font utilities directly.**

```
h1-b (32px/700)    h2-b (24px/700)    h3-b (20px/700)    h4-b (16px/700)
body-l-b/m/r (16px · 700/500/400)
body-b/m/r   (14px · 700/500/400)
caption-b/m/r (12px · 700/500/400)
```

```tsx
// ✅ Correct
className="h2-b text-fg-primary"

// ❌ Wrong — raw Tailwind font class combination
className="text-2xl font-bold leading-tight"
```

### cn() — Always Use for Conditional Class Merging

```ts
// shared/libs/utils.ts
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

### CVA — Variant Components

```tsx
import { cva, type VariantProps } from 'class-variance-authority'
import { cn } from '@/shared/libs'

const buttonVariants = cva('body-b rounded-[var(--radius-md)]', {
  variants: {
    variant: {
      primary: 'bg-primary-1 text-fg-inverse',
      secondary: 'bg-surface-subtle text-fg-primary',
    },
  },
  defaultVariants: { variant: 'primary' },
})
```

### Component Folder Structure

Every component in `shared/components/` uses its own folder:

```
shared/components/
├── index.ts
└── Button/
    ├── Button.tsx
    ├── Button.types.ts   ← only when Props are complex
    └── index.ts
```

### CSS Layer Loading Order

`app/layout.tsx` imports `@/shared/styles/index.css`, which loads:
1. Primitive tokens
2. Semantic tokens
3. Tailwind layers (theme → utilities → base)

---

## Next.js Anti-patterns — Never Do These

- `useEffect + fetch` inside a component → extract to a hook
- Creating `app/api/` routes unnecessarily → use backend API directly
- Using `<a>` tag → use `<Link>` from `next/link`
- Using `<img>` tag → use `<Image>` from `next/image`
- Repeating common UI in each `page.tsx` → put it in `layout.tsx`
- Wrapping async server components without `<Suspense>` → slow queries block entire page render
- Using `NEXT_PUBLIC_`-less env vars in client components → always `undefined`
- Putting 3D pages under `app/` directly without `(service)/` group

---

## Barrel Exports

Every folder exposes a single `index.ts` entry point. Never import from internal paths directly.

```ts
// ✅
import { useRelayDrawing } from "@/features/relay-drawing";

// ❌
import { useRelayDrawing } from "@/features/relay-drawing/useRelayDrawing";
```

Exceptions:

- `_infra/` files are imported directly (no barrel to avoid circular refs)
- `use*Interaction.ts` may import feature store files directly for write access

---

## feature-level Store Pattern

Scene proximity detection results are written directly to feature stores from `use*Interaction.ts`:

```ts
// worlds/hub/useHubInteraction.ts
import { useFortuneStore } from "../../features/fortune/fortuneStore";
import { useLabelPrinterStore } from "../../features/label-printer/labelPrinterStore";

// ✅ allowed — store write only
// ❌ still forbidden — component/hook imports from features/
```

Always reset store values when `active` becomes false to prevent stale state after scene exit.

---

## Verification Checklist

Before completing any task, verify:

**Setup (check once per project)**

- [ ] All required packages from the stack are present in `package.json` — install any that are missing before writing code

**Architecture**

- [ ] No single-letter or cryptic-abbreviation variable/constant names (e.g. `p`, `u`, `BH`) — use descriptive names
- [ ] New file placed in the correct layer (`app/`, `worlds/`, `features/`, `shared/`)
- [ ] Filename matches the suffix convention (`{Scene}Loader`, `{Scene}Canvas`, `{Scene}Scene`, `*Page`, `*Modal`, `*Stage`, `*Mesh`, `*Visual`, `use*Interaction`, etc.)
- [ ] Component file contains no API calls, data transformation, or game logic
- [ ] Business logic extracted to `use*.ts` hook
- [ ] Shared state extracted to `*Store.ts`
- [ ] `index.ts` barrel updated if a new public export was added
- [ ] **Resource folderization**: if this PR adds the 2nd file of a resource type (hook/constant/util/type/component), the folder + `index.ts` barrel is created in the same PR
- [ ] No cross-feature direct imports (`features/A` ↔ `features/B` forbidden)
- [ ] No direct `worlds/` ↔ `features/` component or hook imports

**Routing**

- [ ] Normal service pages are under `app/(service)/` — not directly under `app/`
- [ ] Admin pages are under `app/admin/`
- [ ] `<Canvas>` declared only in `{Scene}Canvas.tsx` (not in page, loader, or scene component)
- [ ] `<Physics>` declared only inside `{Scene}Canvas.tsx`
- [ ] `'use client'` added only where the file actually uses hooks, event handlers, or browser APIs
- [ ] `'use client'` not added to scene/mesh/interaction files (propagates from `{Scene}Canvas.tsx`)

**Admin**

- [ ] Admin features only import from `shared/` — never from normal `features/`
- [ ] Normal features do not import from `features/admin/`

**R3F / Three.js**

- [ ] `motion.*` not used inside R3F `<Canvas>`
- [ ] `useFrame` callback contains no heavy computation
- [ ] `useFrame` does not call any React `setState` — animation values use `useRef` + direct Three.js mutation
- [ ] Animation completion is detected inside `useFrame` condition check, not `setInterval`/`setTimeout`
- [ ] `TextureLoader` uses scene-shared `isolatedManager` from `textureLoader.ts`, not a per-file `new LoadingManager()`
- [ ] `useEffect` body does not call `setState` synchronously — always inside async IIFE

**Rapier**

- [ ] Character RigidBody type is `kinematicPosition`, not `dynamic`
- [ ] Position updated via `setNextKinematicTranslation()`, not direct mutation
- [ ] `<Physics>` declared only in `{Scene}Canvas.tsx`, not per-scene component
- [ ] Ground and boundary walls use `fixed` RigidBody + Collider (not `MathUtils.clamp`)
- [ ] Proximity detection uses `sensor: true` Collider (not distance polling in `useFrame`)
- [ ] Equipment/robot position sync logic extracted to `use{Name}.ts` hook

**Design System**

- [ ] Components reference only Semantic tokens — no Primitive token (`bg-cream-*`, `text-brown-*`) in className
- [ ] Typography uses utility classes (`h1-b`, `body-r`, etc.) — no raw Tailwind font class combinations
- [ ] Conditional class merging uses `cn()` — no template literal string concatenation
- [ ] Variant components use CVA (`cva()`) — no manual variant switching via conditionals
- [ ] `app/layout.tsx` imports `@/shared/styles/index.css`

**Next.js**

- [ ] `<a>` and `<img>` tags replaced with `<Link>` and `<Image>`
- [ ] Common UI not repeated in `page.tsx` — lives in `layout.tsx`
- [ ] No `NEXT_PUBLIC_`-less env vars referenced in client components
- [ ] Async server components that may be slow are wrapped with `<Suspense>`
