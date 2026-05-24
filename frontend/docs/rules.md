# Core Rules

## Always Enforced

- No single-letter or cryptic abbreviations: `p` → `doorOpenProgress`, `BH` → `BODY_HEIGHT`, `cb` → `onComplete`
- Loop counters `i`, `j` are the only single-letter exception
- Constants: descriptive `SCREAMING_SNAKE_CASE` — `PRINTER_BODY_HEIGHT`, `HUD_DISTANCE`
- No `.module.css` files; no inline `style={{ }}` props
- No direct `process.env.X` in feature code — use `shared/config/`
- 2nd resource file of same type (hook/const/util/type/component) requires folder + `index.ts` barrel in same PR
- **No convention exception files** — do not write any document (ADR, note, comment) that grants a feature, file, or folder an exemption from established rules; either fix the code to comply or extend the convention itself
- **Convention gaps → team discussion, not local workaround** — if the convention does not cover a real case, raise it with the team and update the global instruction docs so the solution applies to everyone; resolving it privately within a feature is forbidden

## Architecture

- Import direction: `app → worlds · features → shared` (one way only)
- No direct imports between `features/` — route shared state through `shared/stores/`
- No direct `worlds/` ↔ `features/` component or hook imports
- `use*Interaction.ts` may write to feature stores only (component/hook imports still forbidden)
- No `features/admin/` ↔ normal `features/` imports (both directions forbidden)
- External consumers must import from folder barrel — no bypassing `index.ts`
- Feature-internal files: use resource folder barrel (`../hooks`) or explicit flat file (`../fooStore`)
- No bypassing existing resource folder barrels with deep paths (`../hooks/useFoo`, `./utils/formatFoo`)
- No self-referential barrel imports (`index.ts → LoginPage.tsx → index.ts` cycle)
- `_infra/`: one-per-scene singletons only; `_shared/mesh/`: multi-instance reusable meshes — never mix

## Routing

- Normal service pages go under `app/(service)/` — never directly under `app/`
- Admin pages go under `app/admin/`
- `<Canvas>` declared only in `{Scene}Canvas.tsx`
- `<Physics>` declared only inside `{Scene}Canvas.tsx`
- `dynamic + ssr:false` only in `{Scene}Loader.tsx` Client Component — never in a Server Component
- Add `'use client'` only when the file uses hooks, event handlers, or browser/WebGL APIs
- Do NOT add `'use client'` to scene/mesh/interaction files (propagates automatically from Canvas)

## API

- User-facing domain: use `api` client; backoffice domain: use `adminApi` client
- Domain functions take no identifier argument — interceptor auto-injects from store
- No `userUuid` field in request body or query — backend reads only the `Anonymous-User-UUID` header
- Other users' UUIDs (e.g. `targetUserUuid`) are domain data — they stay in the body
- No `accessToken` argument on admin domain functions — auto-injected from `useAdminAuthStore`
- `auth/login` and `auth/reissue` must use regular `api`, not `adminApi` (prevents 401 recursion)
- Admin tokens stored in `useAdminAuthStore` (sessionStorage) — never `localStorage`
- No new `ky.create(...)` instances in domain files — only `api` and `adminApi` exist
- Multipart uploads use `api.postForm(...)` — not mixed with regular JSON calls

## R3F / Three.js / Rapier

- No `setState` inside `useFrame` — use `useRef` + direct Three.js object mutation
- No `setInterval`/`setTimeout` for animation polling in R3F — use `useFrame` condition checks
- No `motion.*` inside R3F `<Canvas>`
- CSS `@keyframes` in a feature CSS file only for: (a) infinite-loop ambient animations (drift, breathe, pulse, sway), (b) keyframes referencing CSS custom property parameters, or (c) bulk `animation-play-state` control; all other animations must use `motion`
- Custom fonts: use `next/font/local` → register token in `theme.css` `@theme` block → use as `font-{name}` utility class; no `@font-face` in feature CSS files
- `TextureLoader` must use scene-shared `isolatedManager` from `textureLoader.ts` — no per-file `new LoadingManager()`
- No synchronous `setState` in `useEffect` body — wrap state update in async IIFE (React Compiler rule)
- Character RigidBody type must be `kinematicPosition` — never `dynamic`
- Position updates via `setNextKinematicTranslation()` — no direct `.position` mutation after Rapier is in use
- Boundary enforcement via `fixed` RigidBody + Collider — no `MathUtils.clamp`
- Konva `*Stage.tsx` owns Stage/Layer composition only — extract shapes, hints, overlays to child components

## Design System

- Use semantic tokens only — no primitive tokens (`bg-cream-*`, `text-brown-*`) in className
- Typography via utility classes (`h1-b`, `body-r`, etc.) — no raw Tailwind font class combinations (`text-2xl font-bold`)
- Conditional class merging via `cn()` — no template literal string concatenation
- Variant components use CVA (`cva()`) — no manual conditional class switching
- No feature-scoped visual exceptions — extend the design system with semantic tokens; if the design system cannot cover a case, bring it to team discussion and update the global rules

## Next.js

- Use `<Link>` not `<a>`; use `<Image>` not `<img>`
- Common UI goes in `layout.tsx` — not repeated in each `page.tsx`
- Async server components that may be slow must be wrapped with `<Suspense>`
- No `NEXT_PUBLIC_`-less env vars referenced in client components
- No unnecessary `app/api/` route handlers — use backend API directly

## Component & Hook

- `*.tsx` files render only — no API calls, data transforms, or game logic
- Repeated UI rendered from arrays with stable `key`s — no duplicate JSX blocks
- Non-decorative `<Image>` must have meaningful `alt` text
- `aria-hidden` only on purely decorative images, never on meaningful content

---

> Before marking any task complete, run [docs/checklist.md](checklist.md).
