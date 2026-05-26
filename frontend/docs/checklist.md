# Verification Checklist

Run this checklist before marking any task complete.

> For the rules behind each item see [docs/rules.md](rules.md).

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
- [ ] External consumers use folder barrels; feature-internal imports use narrow resource barrels or explicit flat resource files, and do not import the barrel that exports them
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
- [ ] Multipart uploads use `api.postForm(...)` (or `adminApi`-equivalent if admin-only)
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
- [ ] State-driven styling uses JSX state in `cn()` — no `data-*` attribute + CSS child selector patterns (`group-data-[*]:`) for runtime state
- [ ] Variant components use CVA (`cva()`) — no manual variant switching via conditionals
- [ ] No exception document created (ADR, scope note, bypass comment) — code complies with convention, or convention was extended via team discussion and global instruction doc update
- [ ] New animation: uses `motion` unless infinite-loop ambient or CSS-variable-parameterized — then feature-scoped `@keyframes` only
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
