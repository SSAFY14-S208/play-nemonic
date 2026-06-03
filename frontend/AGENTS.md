# Frontend Agent Instructions

## Required Reading — Every Task

Read these four files before starting any task:
- [docs/rules.md](docs/rules.md)
- [docs/structure.md](docs/structure.md)
- [docs/naming.md](docs/naming.md)
- [docs/checklist.md](docs/checklist.md)

## Task-Specific Reading

Identify the task type and read the corresponding file before writing any code.

| Task type                        | Read this file          |
|----------------------------------|-------------------------|
| Component or hook work           | [docs/component.md](docs/component.md) |
| API integration or state mgmt    | [docs/api.md](docs/api.md)       |
| UI publishing or styling         | [docs/design.md](docs/design.md)    |
| 3D scene, R3F, or Rapier         | [docs/r3f.md](docs/r3f.md)       |
| Konva / react-konva              | [docs/konva.md](docs/konva.md)   |
| DOM animation, motion, CSS keyframes | [docs/animation.md](docs/animation.md) |
| Commit message suggestion        | [docs/commit.md](docs/commit.md)       |
| GitLab MR title and description  | [docs/merge-request.md](docs/merge-request.md)         |
| Logging, event tracking          | [docs/logging.md](docs/logging.md) + [docs/logging-events.md](docs/logging-events.md) |

## Stack

Next.js 16 App Router · TypeScript · Tailwind CSS v4
@react-three/fiber · @react-three/drei · @react-three/rapier · three · Zustand
ky · @base-ui/react · lucide-react · motion
konva · react-konva · pnpm · babel-plugin-react-compiler
class-variance-authority · clsx · tailwind-merge · Pretendard Variable

## Initial Install (run once per project)

```bash
pnpm add next react react-dom @react-three/fiber @react-three/drei @react-three/rapier three
pnpm add @base-ui/react ky lucide-react motion konva react-konva zustand
pnpm add class-variance-authority clsx tailwind-merge
pnpm add -D typescript @types/node @types/react @types/react-dom @types/three eslint eslint-config-next tailwindcss @tailwindcss/postcss postcss
```
