# Frontend — Claude Code Instructions

## 기술 스택

Next.js 16 (App Router) + TypeScript + Tailwind CSS v4
@react-three/fiber · @react-three/drei · @react-three/rapier · three · Zustand
ky · @supabase/supabase-js · shadcn/ui · @base-ui/react · lucide-react · motion
konva · react-konva
패키지 매니저: **pnpm**
React Compiler (babel-plugin-react-compiler) 활성화됨

---

## 초기 의존성 설치 (반드시 준수)

**기술 스택에 명시된 라이브러리는 프로젝트 초기화 시점에 모두 설치해야 합니다.**  
나중에 필요할 것 같은 라이브러리를 빠뜨리면 peer 의존성 충돌이 발생하므로, 아래 명령을 그대로 실행합니다.

```bash
pnpm add next react react-dom @react-three/fiber @react-three/drei @react-three/rapier three
pnpm add @base-ui/react @supabase/supabase-js ky lucide-react motion konva react-konva zustand
pnpm add -D typescript @types/node @types/react @types/react-dom @types/three eslint eslint-config-next tailwindcss @tailwindcss/postcss postcss
```

### 설치 확인 체크리스트

새 프로젝트를 시작하거나 기존 프로젝트에 참여할 때, `package.json`의 `dependencies`에 아래 항목이 모두 있는지 확인합니다. 하나라도 없으면 즉시 설치합니다.

| 패키지 | 용도 |
|--------|------|
| `@react-three/fiber` | R3F — Three.js React 렌더러 |
| `@react-three/drei` | R3F 헬퍼 (Grid, Sky, OrbitControls 등) |
| `@react-three/rapier` | 물리 엔진 — 충돌·경계·센서 처리 |
| `three` | Three.js 코어 |
| `zustand` | 전역 상태 관리 |
| `ky` | HTTP 클라이언트 |
| `@supabase/supabase-js` | Supabase 클라이언트 |
| `@base-ui/react` | shadcn/ui primitive |
| `lucide-react` | 아이콘 |
| `motion` | DOM 애니메이션 |
| `konva` + `react-konva` | 2D 캔버스 |

---

## 폴더 구조

```
src/
├── app/                          # Next.js App Router — 라우팅 진입점만 담당
│   ├── layout.tsx
│   ├── page.tsx
│   └── share/
│       └── [id]/
│           └── page.tsx
│
├── worlds/
│   ├── WorldCanvas.tsx           # Canvas 진입점 ('use client' 선언, <Canvas> 유일)
│   ├── WorldLoader.tsx           # dynamic + ssr:false 래퍼 ('use client')
│   ├── {scene}/
│   │   ├── index.ts
│   │   ├── {Scene}Scene.tsx      # 씬 루트
│   │   ├── constants.ts
│   │   ├── GroundMesh.tsx        # (선택)
│   │   ├── use{Scene}Interaction.ts  # (선택)
│   │   └── objects/
│   │       └── {Name}Mesh.tsx
│   ├── _infra/                   # 월드 전체에 항상 존재하는 단일 주체
│   │   ├── SceneManager.tsx
│   │   ├── Lighting.tsx
│   │   └── Character.tsx
│   └── _shared/
│       └── mesh/                 # 씬 안에 여러 개 배치 가능한 재사용 메시
│           ├── index.ts
│           └── {Name}Mesh.tsx
│
├── features/
│   └── {feature-name}/
│       ├── index.ts
│       ├── {FeatureName}.tsx         # UI만 담당 — 로직은 훅/스토어로 분리
│       ├── use{FeatureName}.ts       # UI 컴포넌트가 참조하는 비즈니스 로직 훅
│       ├── {featureName}Store.ts     # feature 전용 Zustand store (선택)
│       ├── {FeatureName}Visual.tsx   # 독립 <Canvas> 필요 시
│       └── {Name}Mesh.tsx
│
├── shared/
│   ├── hooks/
│   │   ├── index.ts
│   │   └── use{Name}.ts
│   ├── store/
│   │   ├── index.ts
│   │   └── {name}Store.ts
│   ├── lib/
│   │   ├── index.ts
│   │   └── apiClient.ts
│   └── ui/
│       ├── index.ts
│       └── {name}.tsx
│
└── styles/
    ├── index.css                 # 전역 스타일 (@import "tailwindcss")
    └── shadcn.css                # shadcn CLI 관리 영역 — 직접 수정 지양
```

> **`_infra/`** — 월드 전체에 항상 존재하는 단일 주체 (SceneManager, Lighting, Character)  
> **`_shared/`** — 씬 안에 여러 개 배치 가능한 재사용 컴포넌트

---

## 폴더 네이밍

| 위치 | 케이스 | 예시 |
|------|--------|------|
| 최상위 도메인 폴더 | `lowercase` | `worlds/`, `features/`, `shared/` |
| 씬 / 도메인 하위 | `lowercase` | `outside/`, `home/` |
| 도구 하위 | `lowercase` | `hooks/`, `store/`, `ui/`, `lib/`, `objects/` |
| feature 단위 | `kebab-case` | `drawing-canvas/`, `label-printer/` |
| 인프라 공유 | `_prefix` | `_infra/`, `_shared/` |

---

## 변수·파라미터 네이밍

코드를 읽는 사람이 주석 없이도 의미를 파악할 수 있어야 합니다.

### 금지: 단일 문자·무의미한 축약어

| ❌ 금지 | ✅ 올바른 예 |
|---------|------------|
| `p` | `doorOpenProgress` |
| `u` | `uniformScale` |
| `d` | `delta` |
| `t` | `texture` / `elapsedTime` |
| `BH` | `BODY_HEIGHT` |
| `BD` | `BODY_DEPTH` |
| `HW` | `HALF_WIDTH` |
| `CY` | `ARCH_CENTER_Y` |
| `cb` | `onComplete` / `callback` |
| `idx` | `index` |

### 규칙

- **상수**: 역할이 드러나는 `SCREAMING_SNAKE_CASE` — `PRINTER_BODY_HEIGHT`, `HUD_DISTANCE`, `IDENTITY_QUATERNION`
- **지역 변수**: `camelCase`로 의미 전달 — `doorOpenProgress`, `uniformScale`, `cameraForward`
- **파라미터**: 함수 시그니처만 봐도 역할이 명확해야 함 — `delta` (not `d`), `progress` (not `p`)
- **루프 변수 예외**: `i`, `j`는 단순 인덱스에 한해 허용. 그 외 단일 문자 금지.

```ts
// ❌ 금지
const BH = 0.7
const p = doorOpenProgress.current
meshRef.current.scale.x = u * (1 - p)

// ✅ 올바른 예
const BODY_HEIGHT = 0.7
const progress = doorOpenProgress.current
meshRef.current.scale.x = uniformScale * (1 - progress)
```

---

## 파일 네이밍

| 종류 | 케이스 | 예시 |
|------|--------|------|
| React 컴포넌트 | `PascalCase.tsx` | `HomeScene.tsx`, `DrawingCanvas.tsx` |
| shadcn UI 프리미티브 | `lowercase.tsx` | `drawer.tsx`, `button.tsx` |
| 훅 | `camelCase.ts` | `useCharacterControls.ts` |
| 스토어 | `camelCase.ts` | `canvasStore.ts` |
| 상수 | `constants.ts` (고정) | |
| 유틸 | `camelCase.ts` | `utils.ts` |
| 배럴 | `index.ts` (고정) | |

### 파일명 접미사

| 접미사 | 의미 | 위치 |
|--------|------|------|
| `*Scene.tsx` | 씬 루트. 메인 `<Canvas>` 컨텍스트를 이어받음 | `worlds/{scene}/` |
| `*Mesh.tsx` | 3D 지오메트리 단위. `<Canvas>` 선언 없음 | `worlds/`, `features/` |
| `*Visual.tsx` | 독립 `<Canvas>`를 소유하는 3D 컴포넌트 | `features/` |
| `*Modal.tsx` | DOM 오버레이 모달 | `features/` |
| `*Canvas.tsx` | Konva 2D 캔버스 컴포넌트 | `features/` |
| `use*Interaction.ts` | 씬 상호작용 훅 (거리 감지 + 키 이벤트) | `worlds/{scene}/` |
| `use*.ts` | 그 외 React 훅 | `features/`, `shared/hooks/` |

---

## UI / 비즈니스 로직 분리 규칙 (반드시 준수)

`*.tsx` 파일은 **렌더링만** 담당합니다. 아래 항목은 반드시 별도 파일로 분리합니다.

**훅(`use*.ts`)으로 분리:**
- API 호출
- 데이터 변환 / 계산 로직
- `useFrame` 기반 3D 로직
- 게임 로직 (거리 감지, 충돌 판정)

**스토어(`*Store.ts`)로 분리:**
- 여러 컴포넌트에 영향을 주는 상태

**컴포넌트 안에 있어도 되는 것:**
- `isOpen`, `isHovered` 같은 순수 UI 상태
- 버튼 클릭 → 모달 열기 같은 단순 UI 흐름

```tsx
// ✅ 올바른 구조
export default function LabelPrinter() {
  const { labels, addLabel } = useLabelPrinter()  // 로직은 훅에서
  return <div>{labels.map(...)}</div>
}

// ❌ 금지
export default function LabelPrinter() {
  const [labels, setLabels] = useState([])
  const addLabel = async () => {
    const result = await api.post("/labels", { ... })  // API 호출을 컴포넌트 안에
    setLabels(prev => [...prev, result])
  }
  return <div>...</div>
}
```

---

## 의존 방향 규칙

```
app → worlds · features → shared
```

- `features/` 간 직접 import 금지 → `shared/store/` 경유
- `worlds/`↔`features/` 컴포넌트/훅 직접 import 금지
- `shared/`는 어느 레이어에서도 참조 가능

### feature-level store 예외

씬의 근접 감지 결과처럼 특정 feature에만 해당하는 트리거 상태는 해당 `features/{feature}/` 안에 store를 두고, `worlds/{scene}/use*Interaction.ts`가 직접 write합니다.

```
worlds/home/useHomeInteraction.ts
  ├──write──▶ features/interaction-sheet/interactionSheetStore.ts
  └──write──▶ features/label-printer/labelPrinterStore.ts
```

- `use*Interaction.ts`의 feature store write는 예외적으로 허용
- 단, feature 컴포넌트/훅 import는 여전히 금지
- **씬 이탈 시 `active = false`로 store 값을 리셋해 stale 상태 방지**

---

## Next.js 서버/클라이언트 경계 규칙

### `'use client'` 판단 기준

**붙여야 하는 경우:**
- `useState`, `useEffect`, `useRef` 등 React 훅 사용
- `onClick`, `onChange` 등 이벤트 핸들러 직접 사용
- `window`, `localStorage` 등 브라우저 전용 API 사용
- R3F, drei, rapier 등 WebGL 관련 코드 포함

**붙이지 않아도 되는 경우 (서버 컴포넌트 유지):**
- 위 항목이 하나도 없는 순수 렌더링 컴포넌트
- `async/await`로 백엔드 API를 서버에서 직접 호출하는 컴포넌트

### app/ — 진입점만

- `app/` 파일은 라우팅 진입점과 메타데이터 선언만 담당
- 실제 UI/로직은 `worlds/`, `features/`에서 가져옴
- **Next.js 16에서 `dynamic + ssr: false`는 Server Component에서 사용 불가** → `WorldLoader.tsx` 같은 Client Component 래퍼로 분리

```tsx
// worlds/WorldLoader.tsx  ← Client Component 래퍼
'use client'
import dynamic from 'next/dynamic'
const WorldCanvas = dynamic(() => import('./WorldCanvas'), { ssr: false })
export default function WorldLoader() {
  return <WorldCanvas />
}

// app/page.tsx  ← Server Component (ssr:false 코드 없음)
import WorldLoader from '@/worlds/WorldLoader'
export default function Page() {
  return <WorldLoader />
}
```

```tsx
// app/share/[id]/page.tsx
export async function generateMetadata({ params }) {
  // OG 태그만 여기서 처리 — 백엔드 API 호출 가능
  const result = await fetch(`${process.env.API_URL}/results/${params.id}`).then(r => r.json())
  return { openGraph: { title: result.name, images: [result.imageUrl] } }
}
export default function SharePage({ params }) {
  return <ShareFeature id={params.id} />  // 실제 UI는 features/에서
}
```

### 공통 레이아웃 — app/layout.tsx에만

페이지 간 공통 UI(Header, Footer, Sidebar 등)는 반드시 `app/layout.tsx`에 작성합니다.  
각 `page.tsx`에 공통 UI를 반복 작성하지 않습니다.

특정 라우트 그룹에만 다른 레이아웃이 필요한 경우 중첩 `layout.tsx`를 사용합니다.

### worlds/ — 전체 클라이언트

- `WorldCanvas.tsx`에만 `'use client'` 선언 — 하위 파일은 자동 전파
- `worlds/` 하위에서 `<Canvas>` 선언 금지 (`WorldCanvas.tsx`에만 존재)
- `worlds/` 하위 파일에 `'use client'` 중복 선언 불필요

### features/ — 명시적 선언

- 3D가 필요한 feature: `*Visual.tsx`에 `'use client'` 선언
- 순수 UI feature: 서버 컴포넌트 유지 가능

---

## API 클라이언트 규칙

이 프로젝트는 Spring/NestJS 백엔드와 협업합니다. DB를 직접 조작하지 않으며, 모든 데이터는 백엔드 API를 통해서만 접근합니다.

```ts
// shared/lib/apiClient.ts
import ky from 'ky'

const client = ky.create({
  prefix: process.env.NEXT_PUBLIC_API_URL,  // ky v2: prefixUrl → prefix
  timeout: 30_000,
  hooks: {
    beforeRequest: [
      (request) => {
        const token = getToken()
        if (token) request.headers.set('Authorization', `Bearer ${token}`)
      },
    ],
    afterResponse: [
      async (_request, _options, response) => {
        if (response.status === 401) {
          // 인증 만료 처리
        }
        return response
      },
    ],
  },
})

export const api = {
  get: <T>(path: string) => client.get(path).json<T>(),
  post: <T>(path: string, body: unknown) => client.post(path, { json: body }).json<T>(),
  put: <T>(path: string, body: unknown) => client.put(path, { json: body }).json<T>(),
  delete: <T>(path: string) => client.delete(path).json<T>(),
}
```

- 모든 API 호출은 `api.get/post/put/delete`를 통해서만 진행
- 서버 컴포넌트에서 Next.js 캐싱(`next: { revalidate }`)이 필요한 경우에만 native `fetch` 직접 사용 허용
- `useEffect` 안에서 `fetch`를 직접 호출하는 패턴 금지 → 훅으로 분리

---

## 라이브러리별 사용 규칙

### shadcn/ui

- style: `base-nova` (primitive: `@base-ui/react`, Radix UI 미사용)
- 컴포넌트 추가는 `pnpm dlx shadcn@latest add {component}`로만 진행
- 설치된 컴포넌트는 `shared/ui/`에 위치, `shared/ui/index.ts` 배럴로 재export
- 아이콘은 `lucide-react` 사용

### motion

- DOM 요소 애니메이션에 사용 (`motion.div`, `AnimatePresence` 등)
- R3F `<Canvas>` 내부에서 `motion.*` 사용 금지

### @react-three/rapier

물리 엔진은 캐릭터 이동 제어, 충돌 감지, 디지털 트윈 설비 시뮬레이션에 사용합니다.

#### Physics Provider 배치

`<Physics>`는 `WorldCanvas.tsx` 내 `<Canvas>` 바로 하위에 하나만 배치합니다. 씬별로 중복 선언하지 않습니다.

```tsx
// worlds/WorldCanvas.tsx
<Canvas>
  <Physics gravity={[0, -9.81, 0]}>
    <SceneManager />
  </Physics>
</Canvas>
```

디지털 트윈처럼 중력이 의미 없는 평면 이동 씬은 `gravity={[0, 0, 0]}`으로 설정합니다.

#### RigidBody 타입 선택 기준

| 타입 | 사용 대상 |
|------|-----------|
| `kinematicPosition` | 캐릭터, 외부 데이터(WebSocket/API)로 위치가 갱신되는 설비·로봇 |
| `fixed` | 바닥, 벽, 고정 구조물 |
| `dynamic` | 물리적으로 자유 반응이 필요한 물체 (자유낙하, 밀림 등) |
| `sensor: true` Collider | 충돌 이벤트만 감지, 물리 반응 없음 (근접 감지 범위) |

`kinematicPosition`은 이동을 코드가 제어하고 충돌 처리는 물리 엔진이 담당합니다.  
`dynamic`을 캐릭터에 쓰면 물리 연산이 이동을 방해하므로 **절대 금지**합니다.

#### 캐릭터 패턴

```tsx
// worlds/_infra/Character.tsx
import { RigidBody, CapsuleCollider } from '@react-three/rapier'

export default function Character() {
  const rb = useRef<RapierRigidBody>(null)
  useCharacterMovement(rb)
  return (
    <RigidBody ref={rb} type="kinematicPosition" colliders={false}>
      <CapsuleCollider args={[0.4, 0.4]} />
      <group>{/* 3D 모델 */}</group>
    </RigidBody>
  )
}
```

이동 훅에서는 `setNextKinematicTranslation()`으로 위치를 갱신합니다.

```ts
// worlds/_infra/hooks/useCharacterMovement.ts
useFrame((_, delta) => {
  if (!rb.current) return
  const pos = rb.current.translation()
  // 다음 위치 계산 후
  rb.current.setNextKinematicTranslation({ x: nextX, y: pos.y, z: nextZ })
})
```

`groupRef.current.position`을 직접 수정하는 패턴은 Rapier 도입 후 **금지**합니다.

#### 디지털 트윈 설비 패턴

외부 데이터로 위치가 갱신되는 설비(로봇, 이동형 장비)는 `kinematicPosition`으로 처리합니다.

```tsx
// worlds/{scene}/objects/EquipmentMesh.tsx
<RigidBody type="kinematicPosition" ref={rb}>
  <CuboidCollider args={[1, 1, 1]} />
  {/* 설비 모델 */}
</RigidBody>
```

WebSocket/API로 받은 위치 데이터를 `useFrame` 안에서 `setNextKinematicTranslation()`으로 적용합니다.  
위치 동기화 로직은 반드시 `use{Name}.ts` 훅으로 분리합니다.

#### 센서(감지 범위) 패턴

설비 근접 감지처럼 물리 반응 없이 이벤트만 필요한 경우 `sensor` Collider를 사용합니다.

```tsx
<RigidBody type="fixed" sensor onIntersectionEnter={onEnter} onIntersectionExit={onExit}>
  <SphereCollider args={[3]} />
</RigidBody>
```

감지 이벤트 처리 로직은 `use{Scene}Interaction.ts`에 위치합니다.

#### KinematicCharacterController와 충돌 처리

캐릭터는 `KinematicCharacterController`의 `computeColliderMovement`로 이동합니다.  
이 메서드는 Rapier World에 등록된 **모든 Collider**를 대상으로 충돌을 계산합니다.

| mesh 상태 | 캐릭터 충돌 |
|-----------|------------|
| `RigidBody` + Collider 있음 (`fixed` / `kinematicPosition` / `dynamic`) | 막힘 ✅ |
| 일반 `<mesh>` (RigidBody 없음) | 통과 ❌ |
| `sensor: true` Collider | 통과 — 이벤트만 발생 ❌ |

**새 구조물·설비를 추가할 때는 반드시 `RigidBody`로 감싸야 캐릭터가 막힙니다.**  
Controller 코드는 수정하지 않아도 자동으로 반응합니다.

```tsx
// ✅ 캐릭터가 막힘 — RigidBody 있음
<RigidBody type="fixed">
  <mesh><boxGeometry args={[3, 5, 3]} /><meshStandardMaterial /></mesh>
</RigidBody>

// ❌ 캐릭터가 통과 — RigidBody 없음
<mesh><boxGeometry args={[3, 5, 3]} /><meshStandardMaterial /></mesh>
```

#### 바닥·경계벽 패턴

```tsx
// worlds/{scene}/GroundMesh.tsx
import { RigidBody } from '@react-three/rapier'

<RigidBody type="fixed">
  <mesh rotation={[-Math.PI / 2, 0, 0]}>
    <planeGeometry args={[50, 50]} />
    <meshStandardMaterial color="#6b8f52" />
  </mesh>
</RigidBody>
```

보이지 않는 경계벽도 `fixed` RigidBody + `CuboidCollider`로 구성합니다.

### Three.js / R3F 패턴

#### `useFrame` 안에서 React `setState` 호출 금지

`useFrame`은 매 프레임(60fps) 실행되므로, 내부에서 `useState` setter를 호출하면 초당 60번 리렌더가 유발됩니다.
애니메이션 값은 `useRef`로 관리하고, Three.js 객체는 ref를 통해 직접 조작합니다.

```tsx
// ❌ 금지 — 60fps 리렌더 유발
const [intensity, setIntensity] = useState(0)
useFrame(({ clock }) => {
  setIntensity(Math.sin(clock.elapsedTime * 5))
})

// ✅ 올바른 방식 — Three.js 객체를 ref로 직접 조작
const lightRef = useRef<PointLight>(null)
useFrame(({ clock }) => {
  if (!lightRef.current) return
  lightRef.current.intensity = Math.sin(clock.elapsedTime * 5)
})
```

#### R3F 씬 내 `setInterval`/`setTimeout` 애니메이션 폴링 금지

R3F 씬 안에서 애니메이션 완료를 `setInterval`로 폴링하면 R3F 루프와 독립된 타이머가 생겨 동기화가 어렵습니다.
`useFrame` 내부 조건 검사로 대체합니다.

```ts
// ❌ 금지
const check = setInterval(() => {
  if (Math.abs(mesh.position.y - target) < 0.01) {
    clearInterval(check)
    onComplete()
  }
}, 50)

// ✅ 올바른 방식 — useFrame 내 조건 검사
const checking = useRef(false)
// (애니메이션 시작 시) checking.current = true
useFrame(() => {
  if (!checking.current) return
  if (Math.abs(mesh.position.y - target) < 0.01) {
    checking.current = false
    onComplete()
  }
})
```

#### `TextureLoader` 사용 시 `LoadingManager` 격리

`TextureLoader`를 기본 `DefaultLoadingManager`에 연결하면 drei의 `useProgress`가 간섭받아 로딩 진행도가 오염됩니다.
격리된 매니저는 **씬 공유 모듈 파일 상단에 싱글턴으로 선언**하고, 같은 씬 내 여러 파일에서 import해 사용합니다.

```ts
// worlds/{scene}/textureLoader.ts — 씬 단위 공유 싱글턴
import { LoadingManager } from 'three'
export const isolatedManager = new LoadingManager()

// ❌ 금지 — 각 파일에 중복 선언
const isolatedManager = new LoadingManager() // DrawingDoorMesh.tsx
const isolatedManager = new LoadingManager() // useOnboardingPrinterInteraction.ts
```

#### `useEffect` 내 `setState` 동기 호출 금지 (React Compiler 규칙)

React Compiler(`babel-plugin-react-compiler`) 활성화 상태에서 `useEffect` 본문 안에서 `setState`를 동기 호출하면 컴파일 오류가 발생합니다.
상태 초기화가 필요한 경우 async IIFE 안에서 처리합니다.

```ts
// ❌ 금지 — 동기 setState
useEffect(() => {
  if (!url) {
    setTexture(null) // 오류: synchronous setState in effect
    return
  }
  // ...
}, [url])

// ✅ 올바른 방식 — async IIFE 안에서 처리
useEffect(() => {
  let cancelled = false
  ;(async () => {
    if (!url) {
      if (!cancelled) setTexture(null)
      return
    }
    // ...
  })()
  return () => { cancelled = true }
}, [url])
```

---

### konva / react-konva

- 2D 캔버스 기능에만 사용, `features/` 하위에 `*Canvas.tsx` 파일로 배치
- `Stage > Layer > Shape` 구조 준수

### CSS 파일 구조

- 전역 스타일은 `styles/index.css`에 추가 (`@import "tailwindcss"` 포함)
- `styles/shadcn.css`는 shadcn CLI가 관리하는 영역이므로 직접 수정 지양
- `layout.tsx`에서 `@/styles/index.css`를 import

---

## Next.js 안티패턴 금지

- **`useEffect + fetch` 패턴 금지** — 클라이언트 컴포넌트의 데이터 조회는 훅으로 분리
- **불필요한 `app/api/` Route 생성 금지** — 외부 웹훅, 클라이언트 mutation 전용으로만 사용
- **`<a>` 태그 사용 금지** → `next/link`의 `<Link>` 사용
- **`<img>` 태그 사용 금지** → `next/image`의 `<Image>` 사용
- **`page.tsx`에 공통 UI 반복 작성 금지** → `layout.tsx`로
- **비동기 서버 컴포넌트는 `<Suspense>`로 감쌀 것** — 느린 조회가 페이지 전체를 블로킹하지 않도록

---

## 환경변수 규칙

- `NEXT_PUBLIC_` prefix: 클라이언트에서 접근 가능한 값 (API URL 등)
- prefix 없음: 서버 컴포넌트 / API Route에서만 사용 (시크릿 키 등)

```ts
process.env.NEXT_PUBLIC_API_URL       // ✅ 클라이언트에서 접근 가능
process.env.SUPABASE_SERVICE_ROLE_KEY // ❌ 클라이언트에서 접근 불가 → undefined
```

---

## 배럴 export

모든 폴더는 `index.ts`를 통해 외부에 단일 진입점을 제공합니다.  
내부 파일 경로 직접 참조 금지 (`_infra/` 및 `use*Interaction.ts`의 feature store import는 예외).

```ts
// ✅ 올바른 import
import { useInteractiveObject } from '@/features/interaction-sheet'

// ❌ 금지 — 내부 경로 직접 참조
import { useInteractiveObject } from '@/features/interaction-sheet/useInteractiveObject'
```

---

## 핵심 금지사항 (반드시 준수)

- `*.tsx`에 API 호출, 데이터 변환, 게임 로직 직접 작성 금지 → 훅/스토어로 분리
- `useEffect` 안에서 `fetch` 직접 호출 금지 → 훅으로 분리
- `worlds/` 하위에서 `<Canvas>` 선언 금지 (`WorldCanvas.tsx`에만 존재)
- Server Component에서 `dynamic + ssr: false` 사용 금지 → `WorldLoader.tsx` 같은 Client Component 래퍼로 분리
- `_infra/`에 재사용 가능한 메시 추가 금지 → `_shared/mesh/`로
- `_shared/mesh/`에 단일 주체 컴포넌트 추가 금지 → `_infra/`로
- `index.ts` 배럴 우회 import 금지
- `features/` 간 직접 import 금지
- `worlds/`↔`features/` 컴포넌트/훅 직접 import 금지
- `useFrame` 안에 무거운 연산 배치 금지
- `useFrame` 안에서 React `setState` 호출 금지 → `useRef` + Three.js 객체 직접 조작으로 대체
- R3F 씬 내 애니메이션 완료 감지를 `setInterval`/`setTimeout`으로 폴링 금지 → `useFrame` 조건 검사로 대체
- `TextureLoader` 사용 시 파일마다 `new LoadingManager()` 중복 선언 금지 → 씬 공유 `textureLoader.ts` 싱글턴에서 import
- `useEffect` 본문에서 `setState` 동기 호출 금지 (React Compiler 오류) → async IIFE 안에서 처리
- R3F `<Canvas>` 내부에서 `motion.*` 사용 금지
- Rapier 도입 후 `groupRef.current.position` 직접 수정 금지 → `setNextKinematicTranslation()` 사용
- 캐릭터에 `dynamic` RigidBody 사용 금지 → `kinematicPosition` 사용
- `<Physics>` Provider를 씬별로 중복 선언 금지 → `WorldCanvas.tsx` 한 곳에만
- Rapier 사용 시 경계를 `MathUtils.clamp`로 처리 금지 → `fixed` RigidBody + Collider로 처리
- 불필요한 `'use client'` 선언 금지 (판단 기준은 위 참고)
- `<a>` 태그 사용 금지 → `<Link>`
- `<img>` 태그 사용 금지 → `<Image>`
- `page.tsx`에 공통 UI 반복 작성 금지 → `layout.tsx`로
- `NEXT_PUBLIC_` 없는 환경변수를 클라이언트 컴포넌트에서 참조 금지
