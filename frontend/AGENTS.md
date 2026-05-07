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
| cn() utility                     | `shared/libs/`                   | `cn.ts`                        |
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

**Rendering structure rules:**

- Repeated UI should be rendered from arrays with stable `key`s.
- Extract repeated item markup and large visual sections into child components.
- Konva `*Stage.tsx` should own Stage/Layer composition; split shapes, hints, tools, and overlays into child components.
- Every non-decorative image needs meaningful `alt` text; use `alt=""` only for decorative images with `aria-hidden`.

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

Backend: Spring (no DB access from client). All data via REST API.

### Two auth flows — two separate clients

The codebase has two distinct authentication flows. Each has its own ky client. Both flows send their identifier as an **HTTP header**, and domain functions take **no identifier argument** — interceptors inject it from a Zustand store.

| Client | File | Domains | Auth header | Source store |
| --- | --- | --- | --- | --- |
| `api` | `shared/libs/apiClient.ts` | user, gallery, community, share, relay, invite, flipbook, file | `Anonymous-User-UUID: {userUuid}` | `useUserStore` (localStorage) |
| `adminApi` | `shared/libs/adminApiClient.ts` | admins, `auth/logout` | `Authorization: Bearer {accessToken}` | `useAdminAuthStore` (sessionStorage) |

**General user flow — anonymous UUID header:**

The backend issues `userUuid` on first visit. Every subsequent request is identified via the `Anonymous-User-UUID` header. There is no JWT or session cookie.

- The `api` `beforeRequest` hook reads `useUserStore.getState().userUuid` and injects it as `Anonymous-User-UUID` when present.
- Some endpoints don't need the header (`POST /users/anonymous` runs before issuance so the store is empty; `GET /community/{id}` is public). When the store is null the hook skips injection — no special-casing needed.
- Domain functions do NOT take `userUuid` as an argument. Body/query never contains a `userUuid` field — the backend reads only the header.
- Other users' UUIDs (e.g. a kick target's `targetUserUuid`) are domain data, not identity, and stay in the body.

**Backoffice flow — admin Bearer token:**

`auth/*` and `admins/*` require `Authorization: Bearer {accessToken}`. To keep the user-facing transport unaware of this, the backoffice uses a separate ky instance (`adminApi`) with hooks that:

- inject the access token from `useAdminAuthStore` on every request (`beforeRequest`),
- catch a 401 response, call `auth/reissue` with the stored refresh token, update the store, and retry the original request once (`afterResponse`),
- de-duplicate concurrent reissue calls so multiple in-flight 401s share a single refresh.

Admin domain functions therefore have **no `accessToken` argument** — the token is injected automatically. `auth/login` and `auth/reissue` must use the regular `api` (calling them with `adminApi` would either lack the token or recurse on 401). Only `auth/logout` uses `adminApi`.

### Three layers

```
shared/libs/apiClient.ts        ← user-facing ky transport. No auth, no envelope.
shared/libs/adminApiClient.ts   ← backoffice ky transport. Token auto-inject + 401 reissue retry.
shared/utils/apiUnwrap.ts       ← ApiResponse<T> unwrap helper.
shared/apis/apiError.ts         ← ApiError class (domain — bound to backend envelope).
shared/apis/{domain}Api.ts      ← Individual function exports per endpoint.
shared/stores/userStore.ts      ← Zustand persist (localStorage) — userUuid.
shared/stores/adminAuthStore.ts ← Zustand persist (sessionStorage) — admin tokens.
shared/hooks/useUserBootstrap.ts← Calls postAnonymousVerify → postAnonymous on mount.
shared/components/UserBootstrap ← Mounted once in app/layout.tsx.
```

### apiClient transport

```ts
// shared/libs/apiClient.ts
import ky from "ky";
import { runtime } from "@/shared/config";

type Query = Record<string, string | number | boolean>;

const client = ky.create({ prefix: `${runtime.apiUrl}/api/v1`, timeout: 30_000 });

const client = ky.create({
  prefix: `${runtime.apiUrl}/api/v1`,
  timeout: 30_000,
  hooks: {
    beforeRequest: [({ request }) => {
      const userUuid = useUserStore.getState().userUuid;
      if (userUuid) request.headers.set('Anonymous-User-UUID', userUuid);
    }],
  },
});

export const api = { get / post / put / patch / delete / postForm };  // method shapes unchanged
```

`PATCH` is a first-class method (used for partial updates like nickname/birth-info). `searchParams` on GET/DELETE is only for pagination/filter — never `userUuid`. `postForm` is for multipart/form-data uploads (e.g. relay submissions).

### adminApiClient — backoffice transport

```ts
// shared/libs/adminApiClient.ts (essentials)
const adminClient = ky.create({
  prefix: `${runtime.apiUrl}/api/v1`,
  timeout: 30_000,
  hooks: {
    beforeRequest: [({ request }) => {
      const accessToken = useAdminAuthStore.getState().accessToken;
      if (accessToken) request.headers.set('Authorization', `Bearer ${accessToken}`);
    }],
    afterResponse: [async ({ request, response }) => {
      if (response.status !== 401) return;
      if (request.url.includes('/auth/reissue')) return; // safety
      const newAccessToken = await refreshAccessToken();
      if (!newAccessToken) return;
      const retry = request.clone();
      retry.headers.set('Authorization', `Bearer ${newAccessToken}`);
      return fetch(retry);
    }],
  },
});
export const adminApi = { /* same shape as api */ };
```

- Admin domain functions take no `accessToken` argument — `getAdminList()`, `postAdmin(payload)`, `deleteAdmin(adminId)`.
- Concurrent 401s share a single in-flight reissue (deduped via a module-scoped `pendingReissue` promise).
- If reissue itself fails the store is cleared. Hooks reading `useAdminAuthStore.accessToken === null` should redirect to the admin login page.

### Choosing a client for a new domain

| Backend OpenAPI security | Client | Identifier arg on domain functions |
| --- | --- | --- |
| `Anonymous-User-UUID` header or none | `api` | None — store auto-injects header |
| `bearerAuth` (admin token) | `adminApi` | None — store auto-injects header |
| Bootstrap endpoints (`auth/login`, `auth/reissue`) | `api` | None — these have no caller identity yet |

Both clients auto-inject their identifier as a header. Domain functions only take path params and domain data (nicknames, page numbers, *other* users' UUIDs, etc.).

### ApiResponse envelope + apiUnwrap

All backend responses come as `ApiResponse<T> = { success, message, data, errors? }`. `apiUnwrap` converts `success: false` to a thrown `ApiError` and returns `data: T`. ApiError carries the `errors` field-level map for form rendering.

```ts
// shared/apis/userApi.ts
import { api } from "@/shared/libs";
import { apiUnwrap } from "@/shared/utils";
import type { ApiResponse, AnonymousUserResponse } from "@/shared/types";

export const postAnonymous = () =>
  apiUnwrap(api.post<ApiResponse<AnonymousUserResponse>>("users/anonymous"));
```

Catch-side handling (in feature hook):

```ts
import { ApiError } from "@/shared/apis";
import { HTTPError } from "ky";

try {
  await patchAnonymousNickname({ nickname: "망고" });  // userUuid auto-injected as header
} catch (error) {
  if (error instanceof ApiError) {
    setNicknameError(error.errors?.nickname);  // 200 + success:false
  } else if (error instanceof HTTPError) {
    // 4xx/5xx
  }
}
```

### Domain API naming — individual exports, never grouped objects

`{httpMethod}{ResourcePath}` camelCase. Domain prefix obvious from context (e.g. `users/`) is dropped. Single resource = singular noun; list = `List` suffix.

| HTTP | Path | Function |
| --- | --- | --- |
| POST | `/users/anonymous` | `postAnonymous` |
| POST | `/users/anonymous/verify` | `postAnonymousVerify` |
| POST | `/users/anonymous/birth-info` | `postAnonymousBirthInfo` |
| PATCH | `/users/anonymous/birth-info` | `patchAnonymousBirthInfo` |
| PATCH | `/users/anonymous/nickname` | `patchAnonymousNickname` |
| GET | `/users/anonymous/profile` | `getAnonymousProfile` |
| GET | `/gallery` (list) | `getGalleryList` |
| GET | `/gallery/{galleryId}` (single) | `getGallery` |
| DELETE | `/gallery/{galleryId}` | `deleteGallery` |
| GET | `/community/{communityId}` | `getCommunity` |

Signatures take only path params and domain data — never the caller's identifier. Examples: `getGallery(galleryId)`, `patchRelayRoomSettings(roomCode, timeLimitSeconds)`, `postRelayRoomKick(roomCode, targetUserUuid)`. The caller's `userUuid` / `accessToken` is auto-injected by the client interceptor.

```ts
import { postAnonymous, getGalleryList } from "@/shared/apis";
```

Do **not** group these into `userApi.foo()` style objects. Feature hooks import only what they need.

### User identity bootstrap

- `userUuid` lives in `useUserStore` (Zustand `persist` middleware, **localStorage**, key `nemonic-user`). No separate localStorage sync code — persist handles it.
- Root `app/layout.tsx` mounts `<UserBootstrap />` (`'use client'`). After persist hydration completes, `useUserBootstrap` calls `postAnonymousVerify` (if uuid stored) or `postAnonymous` (cold start), and updates the store.
- Pages/features must NOT call verify/createAnonymous directly — read `userUuid` from `useUserStore`.

### Admin auth bootstrap

- Admin access/refresh tokens live in `useAdminAuthStore` (Zustand `persist`, **sessionStorage**, key `nemonic-admin-auth`). sessionStorage is intentional — tokens must not survive tab close on shared devices. Do NOT switch to localStorage.
- Successful login: `postLogin` → `useAdminAuthStore.setTokens(loginResponse)`. The interceptor injects the token from then on.
- Logout: `postLogout(refreshToken)` → backend blacklist → `useAdminAuthStore.clear()`.
- `app/admin/layout.tsx` `<AdminAuthGuard>` redirects to `/admin/login` when `useAdminAuthStore.accessToken === null`.

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
- [ ] Repeated UI is rendered from arrays with stable `key`s, not duplicated JSX
- [ ] Large visual sections and repeated item markup are extracted into child components
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
- [ ] Backoffice domain APIs (`adminsApi`, `auth/logout`) use `adminApi`, not `api`
- [ ] Admin domain functions take **no** `accessToken` argument — token is auto-injected from `useAdminAuthStore`
- [ ] `auth/login` and `auth/reissue` use the regular `api` client (avoid 401 interceptor recursion)
- [ ] Admin tokens live in `useAdminAuthStore` (sessionStorage) — never `localStorage`, never a custom global var

**API client choice**

- [ ] No `Authorization` / Bearer header injected via the regular `api` client
- [ ] No new `ky.create(...)` instance in domain files — only `api` and `adminApi` exist
- [ ] multipart uploads use `api.postForm(...)` (or `adminApi`-equivalent if admin-only)
- [ ] User-facing domain functions take **no** `userUuid` argument — header is auto-injected from `useUserStore`
- [ ] No `userUuid` field in body or query — backend reads only the `Anonymous-User-UUID` header. Other users' UUIDs (e.g. `targetUserUuid`) stay in body as domain data

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
- [ ] Non-decorative `<Image>` usage has meaningful `alt` text; decorative images use `alt=""` with `aria-hidden`

**Konva / 2D Canvas**

- [ ] `*Stage.tsx` owns `<Stage>` / top-level `<Layer>` composition only
- [ ] Reusable shapes, hints, guides, cursors, and overlays are split into child components

**Next.js**

- [ ] `<a>` and `<img>` tags replaced with `<Link>` and `<Image>`
- [ ] Common UI not repeated in `page.tsx` — lives in `layout.tsx`
- [ ] No `NEXT_PUBLIC_`-less env vars referenced in client components
- [ ] Async server components that may be slow are wrapped with `<Suspense>`
