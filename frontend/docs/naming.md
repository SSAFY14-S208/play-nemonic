# Naming & File Placement

> For architectural concepts (layers, hierarchy, dependency direction) see [docs/structure.md](structure.md).

## Folder Naming

| Location               | Case         | Examples                                              |
|------------------------|--------------|-------------------------------------------------------|
| Top-level domain       | `lowercase`  | `worlds/`, `features/`, `shared/`                    |
| Scene / domain sub     | `lowercase`  | `landing/`, `hub/`                                   |
| Resource sub (plural)  | `lowercase`  | `apis/`, `components/`, `hooks/`, `stores/`, `utils/`|
| Feature unit           | `kebab-case` | `relay-drawing/`, `label-printer/`                   |
| View folder            | `PascalCase` | `RelayDrawingView/`, `RelayResultView/`              |
| Section folder         | `PascalCase` | `DrawingStage/`, `RoundTransition/`                  |
| Infra / shared prefix  | `_prefix`    | `_infra/`, `_shared/`                                |

## File Naming

| Kind             | Case              | Examples                                      |
|------------------|-------------------|-----------------------------------------------|
| React component  | `PascalCase.tsx`  | `LandingScene.tsx`, `FortuneModal.tsx`        |
| Hook             | `camelCase.ts`    | `useCharacterControls.ts`                     |
| Store            | `camelCase.ts`    | `fortuneStore.ts`                             |
| Constants file   | `constants.ts`    | fixed name                                    |
| Util file        | `camelCase.ts`    | `apiUnwrap.ts`                                |
| Barrel           | `index.ts`        | fixed name                                    |

## File Suffix Conventions

| Suffix                | Meaning                                                  | Location           |
|-----------------------|----------------------------------------------------------|--------------------|
| `{Scene}Loader.tsx`   | `'use client'` + `dynamic(ssr:false)` wrapper            | `worlds/{scene}/`  |
| `{Scene}Canvas.tsx`   | Owns `<Canvas>` + `<Physics>`                            | `worlds/{scene}/`  |
| `{Scene}Scene.tsx`    | Scene root, assembled inside Canvas context              | `worlds/{scene}/`  |
| `*Mesh.tsx`           | 3D geometry unit — no `<Canvas>` declaration             | `worlds/`, `features/` |
| `*Page.tsx`           | 2D route entry point (URL-bearing page)                  | `features/`        |
| `*Modal.tsx`          | DOM overlay modal                                        | `features/`        |
| `*Stage.tsx`          | Konva `<Stage>` owner                                    | `features/`        |
| `*Visual.tsx`         | Independent `<Canvas>` owner                             | `features/`        |
| `use*Interaction.ts`  | Scene interaction hook (proximity + key events)          | `worlds/{scene}/`  |

## File Placement Quick Reference

| What you are creating         | Where it goes                                      | Filename pattern              |
|-------------------------------|----------------------------------------------------|-------------------------------|
| Routing entry point           | `app/(service)/` or `app/admin/`                   | `page.tsx`, `layout.tsx`      |
| 3D ssr:false wrapper          | `worlds/{scene}/`                                  | `{Scene}Loader.tsx`           |
| 3D canvas + physics owner     | `worlds/{scene}/`                                  | `{Scene}Canvas.tsx`           |
| Scene content root            | `worlds/{scene}/`                                  | `{Scene}Scene.tsx`            |
| Scene-specific 3D object      | `worlds/{scene}/objects/`                          | `{Name}Mesh.tsx`              |
| Scene interaction logic       | `worlds/{scene}/`                                  | `use{Scene}Interaction.ts`    |
| Always-present singleton      | `worlds/_infra/`                                   | `{Name}.tsx`                  |
| Reusable multi-instance mesh  | `worlds/_shared/mesh/`                             | `{Name}Mesh.tsx`              |
| Feature page                  | `features/{feature-name}/`                         | `{FeatureName}Page.tsx`       |
| Feature modal                 | `features/{feature-name}/`                         | `{FeatureName}Modal.tsx`      |
| Feature Konva canvas          | `features/{feature-name}/`                         | `{FeatureName}Stage.tsx`      |
| Feature 3D canvas             | `features/{feature-name}/`                         | `{FeatureName}Visual.tsx`     |
| Feature business logic        | `features/{feature-name}/`                         | `use{FeatureName}.ts`         |
| Feature state                 | `features/{feature-name}/`                         | `{featureName}Store.ts`       |
| View root component           | `features/{name}/views/{View}/`                    | `index.tsx`                   |
| View-scoped hook              | `features/{name}/views/{View}/hooks/`              | `use{Name}.ts`                |
| Section root component        | `features/{name}/views/{View}/sections/{Section}/` | `index.tsx`                   |
| Feature-coupled component     | `features/{feature-name}/components/`              | `{Name}.tsx`                  |
| Cross-feature shared state    | `shared/stores/`                                   | `{name}Store.ts`              |
| Shared hook                   | `shared/hooks/`                                    | `use{Name}.ts`                |
| API call function             | `shared/apis/`                                     | `{domain}Api.ts`              |
| Env config / feature flags    | `shared/config/`                                   | `runtime.ts`, `flags.ts`      |
| Shared UI component           | `shared/components/{Name}/`                        | `{Name}.tsx` + `index.ts`     |
| Static assets (svg, glb, mp3) | `shared/assets/`                                   | `{name}.{ext}`                |

## Resource Folderization Rule

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
    ├── useRelayDrawing.ts
    └── useRelayDrawingHistory.ts
```

Resource types: `use*.ts` hooks · constants · utils · types · components
Each resource type evolves independently — hooks may be folderized while constants stay flat.

**PR is blocked if:** 2+ files of same resource type exist without a folder, or a folder exists without `index.ts`.

## Barrel Exports

External consumers import from folder barrels, never from internal file paths.

```ts
// ✅ correct
import { useRelayDrawing } from "@/features/relay-drawing";

// ❌ forbidden — bypasses barrel
import { useRelayDrawing } from "@/features/relay-drawing/useRelayDrawing";
```

Internal implementation imports:
- Sibling files may import each other directly (`./useLogin`, `./loginStore`)
- Do not import the barrel from a file exported by that same barrel (avoids circular graphs)
- When a resource folder barrel exists (`hooks/index.ts`) → import from it (`../hooks`)
- When resource is still flat (`constants.ts`) → import the explicit file (`../constants`)
- Do not use broad parent barrels (`..`) for internal constants/hooks/types/utils/stores
- Do not bypass an existing resource folder barrel with deep paths (`../hooks/useFoo`)
- Merge imports from the same source into one `import` declaration

Exceptions:
- `_infra/` files: import directly (barrel would create circular graphs)
- `use*Interaction.ts`: may import feature store files directly for write access
- `app/` route files and CSS/asset side-effect imports are not barrel targets

## feature-level Store Pattern

Scene proximity detection results are written directly to feature stores from `use*Interaction.ts`:

```ts
// worlds/hub/useHubInteraction.ts
import { useFortuneStore } from "../../features/fortune/fortuneStore";
// ✅ store write only — component/hook imports still forbidden
```

Always reset store values when leaving the scene to prevent stale state (`active = false`).
