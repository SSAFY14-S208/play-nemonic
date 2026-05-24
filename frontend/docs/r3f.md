# R3F & Rapier Rules

> For DOM animation (motion library, CSS @keyframes, custom fonts) see `docs/animation.md`.
> For Konva / react-konva see `docs/konva.md`.

## Rapier Physics

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

For flat/digital-twin scenes where gravity is irrelevant: `gravity={[0, 0, 0]}`.

### RigidBody Type — Decision Table

| Type                    | Use for                                                                     |
|-------------------------|-----------------------------------------------------------------------------|
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

In the movement hook, update position with `setNextKinematicTranslation()` — never mutate `.position` directly:

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
<RigidBody type="fixed" sensor onIntersectionEnter={onEnter} onIntersectionExit={onExit}>
  <SphereCollider args={[3]} />
</RigidBody>
```

Event handling logic lives in `use{Scene}Interaction.ts`.

### CharacterController & Collision — What Gets Blocked

| Mesh state                                              | Character blocked? |
|---------------------------------------------------------|--------------------|
| `RigidBody` + Collider (`fixed` / `kinematicPosition`)  | Yes ✅             |
| Plain `<mesh>` without `RigidBody`                      | No ❌              |
| `sensor: true` Collider                                 | No (event only) ❌ |

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

### `useFrame` — No `setState`

`useFrame` runs every frame (60 fps). Calling a React `useState` setter inside it triggers 60 re-renders per second. Manage animation values with `useRef` and mutate Three.js objects directly.

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

### `setInterval` / `setTimeout` Animation Polling — Forbidden

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

### `LoadingManager` — One Singleton per Scene

Attaching `TextureLoader` to the default `LoadingManager` pollutes drei's `useProgress`. Declare one isolated manager per scene in a shared module file and import it everywhere in that scene.

```ts
// worlds/{scene}/textureLoader.ts — scene-level singleton
import { LoadingManager } from "three";
export const isolatedManager = new LoadingManager();

// ❌ Wrong — duplicate declaration per file
const isolatedManager = new LoadingManager(); // SomeMesh.tsx
const isolatedManager = new LoadingManager(); // someHook.ts
```

### `useEffect` — No Synchronous `setState` (React Compiler Rule)

With `babel-plugin-react-compiler` enabled, calling `setState` synchronously in an effect body is a compile error. Always call `setState` inside an async IIFE.

```ts
// ❌ Wrong
useEffect(() => {
  if (!url) {
    setTexture(null);  // compile error
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
  return () => { cancelled = true; };
}, [url]);
```

