# Frontend 컨벤션 가이드

## 기술 스택

| 분류            | 기술                                                         |
| --------------- | ------------------------------------------------------------ |
| 프레임워크      | Next.js 16 (App Router)                                      |
| 언어            | TypeScript                                                   |
| 스타일          | Tailwind CSS v4                                              |
| 3D              | @react-three/fiber · @react-three/drei · @react-three/rapier |
| 상태 관리       | Zustand                                                      |
| HTTP 클라이언트 | ky                                                           |
| UI              | shadcn/ui (style: base-nova, primitive: @base-ui/react)      |
| 2D 캔버스       | konva · react-konva                                          |
| 아이콘          | lucide-react                                                 |
| 애니메이션      | motion                                                       |
| 패키지 매니저   | pnpm                                                         |
| React Compiler  | babel-plugin-react-compiler 활성화                           |

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
│   │   ├── {Scene}Scene.tsx      # 씬 루트 (메인 Canvas 컨텍스트)
│   │   ├── constants.ts
│   │   ├── GroundMesh.tsx        # (선택)
│   │   ├── use{Scene}Interaction.ts  # (선택)
│   │   └── objects/
│   │       └── {Name}Mesh.tsx
│   ├── _infra/
│   │   ├── SceneManager.tsx
│   │   ├── Lighting.tsx
│   │   └── Character.tsx
│   └── _shared/
│       └── mesh/
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
│   ├── apis/                         # 백엔드 API 호출 함수 (도메인별 분리)
│   │   ├── index.ts
│   │   └── {domain}Api.ts
│   ├── constants/                    # 전역 상수
│   │   ├── index.ts
│   │   └── {name}.ts
│   ├── hooks/                        # 재사용 React 훅
│   │   ├── index.ts
│   │   └── use{Name}.ts
│   ├── libs/                         # 외부 라이브러리 래퍼·설정 (apiClient 등)
│   │   ├── index.ts
│   │   └── apiClient.ts
│   ├── stores/                       # 전역 Zustand store
│   │   ├── index.ts
│   │   └── {name}Store.ts
│   ├── types/                        # 공유 TypeScript 타입 정의
│   │   ├── index.ts
│   │   └── {domain}.ts
│   ├── ui/                           # shadcn 컴포넌트
│   │   ├── index.ts
│   │   └── {name}.tsx
│   └── utils/                        # 순수 유틸리티 함수
│       ├── index.ts
│       └── {name}.ts
│
└── styles/
    ├── index.css
    └── shadcn.css
```

---

## 폴더 네이밍

| 위치               | 케이스       | 예시                                                                                       |
| ------------------ | ------------ | ------------------------------------------------------------------------------------------ |
| 최상위 도메인 폴더 | `lowercase`  | `worlds/`, `features/`, `shared/`                                                          |
| 씬 / 도메인 하위   | `lowercase`  | `outside/`, `home/`                                                                        |
| 도구 하위 (복수형) | `lowercase`  | `apis/`, `constants/`, `hooks/`, `libs/`, `stores/`, `types/`, `ui/`, `utils/`, `objects/` |
| feature 단위       | `kebab-case` | `drawing-canvas/`, `label-printer/`                                                        |
| 인프라 공유        | `_prefix`    | `_infra/`, `_shared/`                                                                      |

> `_infra/` — 월드 전체에 항상 존재하는 단일 주체 (SceneManager, Lighting, Character)  
> `_shared/` — 씬 안에 여러 개 배치 가능한 재사용 컴포넌트

---

## 변수·파라미터 네이밍

코드를 읽는 사람이 주석 없이도 의미를 파악할 수 있어야 합니다.

| ❌ 금지 | ✅ 올바른 예              |
| ------- | ------------------------- |
| `p`     | `doorOpenProgress`        |
| `u`     | `uniformScale`            |
| `d`     | `delta`                   |
| `t`     | `texture` / `elapsedTime` |
| `BH`    | `BODY_HEIGHT`             |
| `BD`    | `BODY_DEPTH`              |
| `HW`    | `HALF_WIDTH`              |
| `CY`    | `ARCH_CENTER_Y`           |
| `cb`    | `onComplete` / `callback` |
| `idx`   | `index`                   |

- **상수**: 역할이 드러나는 `SCREAMING_SNAKE_CASE` — `PRINTER_BODY_HEIGHT`, `HUD_DISTANCE`, `IDENTITY_QUATERNION`
- **지역 변수**: 의미 전달 `camelCase` — `doorOpenProgress`, `uniformScale`, `cameraForward`
- **파라미터**: 함수 시그니처만 봐도 역할이 명확해야 함 — `delta` (not `d`), `progress` (not `p`)
- **루프 변수 예외**: `i`, `j`는 단순 인덱스에 한해 허용

---

## 파일 네이밍

| 종류                 | 케이스                | 예시                                 |
| -------------------- | --------------------- | ------------------------------------ |
| React 컴포넌트       | `PascalCase.tsx`      | `HomeScene.tsx`, `DrawingCanvas.tsx` |
| shadcn UI 프리미티브 | `lowercase.tsx`       | `drawer.tsx`, `button.tsx`           |
| 훅                   | `camelCase.ts`        | `useCharacterControls.ts`            |
| 스토어               | `camelCase.ts`        | `canvasStore.ts`                     |
| 상수                 | `constants.ts` (고정) | `constants.ts`                       |
| 유틸                 | `camelCase.ts`        | `utils.ts`                           |
| 배럴                 | `index.ts` (고정)     | `index.ts`                           |

---

## 파일명 접미사 컨벤션

| 접미사               | 의미                                         | 위치                         |
| -------------------- | -------------------------------------------- | ---------------------------- |
| `*Scene.tsx`         | 씬 루트. 메인 `<Canvas>` 컨텍스트를 이어받음 | `worlds/{scene}/`            |
| `*Mesh.tsx`          | 3D 지오메트리 단위. `<Canvas>` 선언 없음     | `worlds/`, `features/`       |
| `*Visual.tsx`        | 독립 `<Canvas>`를 소유하는 3D 컴포넌트       | `features/`                  |
| `*Modal.tsx`         | DOM 오버레이 모달                            | `features/`                  |
| `*Canvas.tsx`        | Konva 2D 캔버스 컴포넌트                     | `features/`                  |
| `use*Interaction.ts` | 씬 상호작용 훅. 거리 감지 + 키 이벤트 처리   | `worlds/{scene}/`            |
| `use*.ts`            | 그 외 React 훅                               | `features/`, `shared/hooks/` |

---

## UI / 비즈니스 로직 분리 규칙

**컴포넌트 파일(`*.tsx`)은 렌더링만 담당합니다.** 로직은 반드시 외부 훅 또는 스토어 파일로 분리합니다.

### 컴포넌트 안에 있어도 되는 것

- `isOpen`, `isHovered` 같은 순수 UI 상태 (`useState`)
- 버튼 클릭 → 모달 열기 같은 단순 UI 흐름

### 반드시 외부 파일로 분리해야 하는 것

| 로직 유형                        | 분리 위치                                     |
| -------------------------------- | --------------------------------------------- |
| API 호출                         | `use{FeatureName}.ts`                         |
| 데이터 변환 / 계산               | `use{FeatureName}.ts` 또는 `utils.ts`         |
| 여러 컴포넌트에 영향을 주는 상태 | `{featureName}Store.ts` 또는 `shared/stores/` |
| `useFrame` 기반 3D 로직          | `use{Name}.ts`                                |
| 게임 로직 (거리 감지, 충돌 판정) | `use{Scene}Interaction.ts`                    |

### 올바른 구조 예시

```tsx
// features/label-printer/LabelPrinter.tsx — UI만
export default function LabelPrinter() {
  const { labels, addLabel, removeLabel } = useLabelPrinter()  // 훅에서 로직 가져옴

  return (
    <div>
      {labels.map(label => <LabelItem key={label.id} {...label} />)}
      <button onClick={addLabel}>추가</button>
    </div>
  )
}

// features/label-printer/useLabelPrinter.ts — 로직만
export function useLabelPrinter() {
  const labels = useLabelPrinterStore(s => s.labels)
  const addLabel = async () => {
    const result = await api.post("/labels", { ... })
    // ...
  }
  return { labels, addLabel }
}
```

---

## 배럴 export (index.ts)

모든 폴더는 `index.ts`를 통해 외부에 단일 진입점을 제공합니다.

```ts
// 외부에서
import {
  useInteractiveObject,
  InteractionSheet,
} from "../features/interaction-sheet";

// 내부 경로 직접 참조 금지
// import { useInteractiveObject } from "../features/interaction-sheet/useInteractiveObject" // X
```

---

## 의존 방향 규칙

```
app → worlds · features → shared
```

- `features/` 간 직접 import 금지 → feature 간 공유 상태는 `shared/stores/` 경유
- `worlds/`↔`features/` 간 컴포넌트/훅 직접 import 금지
- `shared/`는 어느 레이어에서도 참조 가능

### feature-level store 예외

씬의 근접 감지 결과처럼 특정 feature에만 해당하는 트리거 상태는 해당 `features/{feature}/` 안에 store를 두고, `worlds/{scene}/use*Interaction.ts`가 직접 write합니다.

```
worlds/home/useHomeInteraction.ts
  ├──write──▶ features/interaction-sheet/interactionSheetStore.ts
  └──write──▶ features/label-printer/labelPrinterStore.ts
```

- `use*Interaction.ts`의 feature store import는 예외적으로 허용
- 단, feature 컴포넌트/훅 import는 여전히 금지
- `active` false 시 store 값을 리셋해 씬 이탈 후 stale 상태 방지

---

## Next.js 서버/클라이언트 경계 규칙

### app/ 레이어 — 라우팅 진입점

`app/` 디렉토리 파일은 **라우팅 진입점과 메타데이터 선언만** 담당합니다.

**Next.js 16에서 `dynamic + ssr: false`는 Server Component에서 사용 불가** — `WorldLoader.tsx` Client Component 래퍼를 경유합니다.

```tsx
// worlds/WorldLoader.tsx  ← 'use client' 래퍼
"use client";
import dynamic from "next/dynamic";
const WorldCanvas = dynamic(() => import("./WorldCanvas"), { ssr: false });
export default function WorldLoader() {
  return <WorldCanvas />;
}

// app/page.tsx  ← Server Component (dynamic/ssr:false 코드 없음)
import WorldLoader from "@/worlds/WorldLoader";
export default function Page() {
  return <WorldLoader />;
}
```

```tsx
// app/share/[id]/page.tsx
export async function generateMetadata({ params }) {
  const result = await getResult(params.id);
  return {
    openGraph: {
      title: `${result.name}의 결과`,
      images: [result.imageUrl],
    },
  };
}

export default function SharePage({ params }) {
  return <ShareFeature id={params.id} />;
}
```

### worlds/ 레이어 — 전체 클라이언트

- `WorldCanvas.tsx`에 `'use client'` 선언 — 하위 파일은 자동으로 클라이언트 컨텍스트 전파됨
- `worlds/` 하위에서 `<Canvas>` 선언 금지 — `WorldCanvas.tsx`에만 존재
- `worlds/` 하위 파일에 `'use client'` 중복 선언 불필요

### features/ 레이어

- 3D가 필요한 feature: `*Visual.tsx`가 독립 `<Canvas>` 소유 + `'use client'` 선언
- 순수 UI feature: 서버 컴포넌트로 유지 가능 (필요 시 클라이언트 컴포넌트로 분리)

---

## API 클라이언트 (ky)

`shared/libs/apiClient.ts`에 단일 클라이언트를 정의하고 프로젝트 전체에서 사용합니다.

```ts
// shared/libs/apiClient.ts
import ky from "ky";

const client = ky.create({
  prefix: process.env.NEXT_PUBLIC_API_URL, // ky v2: prefixUrl → prefix
  timeout: 30_000,
  hooks: {
    beforeRequest: [
      ({ request }) => {
        const token = getToken();
        if (token) request.headers.set("Authorization", `Bearer ${token}`);
      },
    ],
    afterResponse: [
      async ({ response }) => {
        if (response.status === 401) {
          // 인증 만료 처리
        }
        return response;
      },
    ],
  },
});

export const api = {
  get: <T>(path: string) => client.get(path).json<T>(),
  post: <T>(path: string, body: unknown) =>
    client.post(path, { json: body }).json<T>(),
  put: <T>(path: string, body: unknown) =>
    client.put(path, { json: body }).json<T>(),
  delete: <T>(path: string) => client.delete(path).json<T>(),
};
```

- 모든 API 호출은 `api.get()`, `api.post()` 등을 통해서만 진행
- 서버 컴포넌트에서 Next.js 캐싱(`next: { revalidate }`)이 필요한 경우에만 native `fetch` 직접 사용 허용

---

## 라이브러리별 사용 규칙

### shadcn/ui

- style: `base-nova` (primitive: `@base-ui/react`, Radix UI 미사용)
- 컴포넌트 추가는 `pnpm dlx shadcn@latest add {component}` 로만 진행
- 설치된 컴포넌트는 `shared/ui/`에 위치, `shared/ui/index.ts` 배럴로 재export
- 아이콘은 `lucide-react` 사용

### motion

- DOM 요소 애니메이션에 사용 (`motion.div`, `AnimatePresence` 등)
- R3F `<Canvas>` 내부에서 `motion.*` 사용 금지

### @react-three/rapier

물리 엔진은 캐릭터 이동, 충돌 감지, 디지털 트윈 설비 시뮬레이션에 사용합니다.

**Physics Provider**: `<Physics>`는 `WorldCanvas.tsx` 내 `<Canvas>` 바로 하위에 하나만 배치합니다. 씬별로 중복 선언 금지.

```tsx
<Canvas>
  <Physics gravity={[0, -9.81, 0]}>
    <SceneManager />
  </Physics>
</Canvas>
```

**RigidBody 타입 선택 기준**

| 타입                    | 사용 대상                                           |
| ----------------------- | --------------------------------------------------- |
| `kinematicPosition`     | 캐릭터, 외부 데이터로 위치가 갱신되는 설비·로봇     |
| `fixed`                 | 바닥, 벽, 고정 구조물                               |
| `dynamic`               | 물리적으로 자유 반응이 필요한 물체                  |
| `sensor: true` Collider | 충돌 이벤트만 감지, 물리 반응 없음 (근접 감지 범위) |

- 캐릭터에 `dynamic` 금지 → `kinematicPosition` 사용
- 위치 갱신은 `setNextKinematicTranslation()` 사용 — `position` 직접 수정 금지
- 경계 처리는 `fixed` RigidBody + Collider — `MathUtils.clamp` 금지
- 새 구조물·설비는 반드시 `RigidBody`로 감싸야 캐릭터가 막힘

---

### Three.js / R3F 패턴

#### `useFrame` 안에서 React `setState` 호출 금지

`useFrame`은 60fps로 실행되므로 `setState` 호출 시 초당 60번 리렌더가 유발됩니다. 애니메이션 값은 `useRef`로 관리하고 Three.js 객체를 ref로 직접 조작합니다.

```tsx
// ❌ 금지
const [intensity, setIntensity] = useState(0);
useFrame(({ clock }) => {
  setIntensity(Math.sin(clock.elapsedTime * 5));
});

// ✅ 올바른 방식
const lightRef = useRef<PointLight>(null);
useFrame(({ clock }) => {
  if (!lightRef.current) return;
  lightRef.current.intensity = Math.sin(clock.elapsedTime * 5);
});
```

#### R3F 씬 내 `setInterval`/`setTimeout` 애니메이션 폴링 금지

```ts
// ❌ 금지
const check = setInterval(() => {
  if (Math.abs(mesh.position.y - target) < 0.01) {
    clearInterval(check);
    onDone();
  }
}, 50);

// ✅ 올바른 방식 — useFrame 내 조건 검사
const checking = useRef(false);
useFrame(() => {
  if (!checking.current) return;
  if (Math.abs(mesh.current.position.y - target) < 0.01) {
    checking.current = false;
    onDone();
  }
});
```

#### `TextureLoader` 사용 시 `LoadingManager` 격리

`TextureLoader`를 기본 `DefaultLoadingManager`에 연결하면 drei의 `useProgress`가 오염됩니다. 씬 공유 싱글턴을 별도 파일에 선언하고 import합니다.

```ts
// worlds/{scene}/textureLoader.ts — 씬 단위 공유 싱글턴
import { LoadingManager } from "three";
export const isolatedManager = new LoadingManager();

// ❌ 금지 — 각 파일에 중복 선언
const isolatedManager = new LoadingManager(); // SomeMesh.tsx
const isolatedManager = new LoadingManager(); // someHook.ts
```

#### `useEffect` 내 동기 `setState` 금지 (React Compiler 규칙)

`babel-plugin-react-compiler` 활성화 상태에서 `useEffect` 본문 안 동기 `setState`는 컴파일 오류입니다. async IIFE 안에서 처리합니다.

```ts
// ❌ 금지
useEffect(() => {
  if (!url) {
    setTexture(null);
    return;
  }
}, [url]);

// ✅ 올바른 방식
useEffect(() => {
  let cancelled = false;
  (async () => {
    if (!url) {
      if (!cancelled) setTexture(null);
      return;
    }
  })();
  return () => {
    cancelled = true;
  };
}, [url]);
```

---

### konva / react-konva

- 2D 캔버스 기능에만 사용, `features/` 하위에 `*Canvas.tsx` 파일로 배치
- `Stage > Layer > Shape` 구조 준수

### CSS 파일 구조

- 프로젝트 전역 스타일은 `index.css`에 추가
- `shadcn.css`는 shadcn CLI가 관리하는 영역이므로 직접 수정 지양

---

## 핵심 금지사항

- `worlds/` 하위에서 `<Canvas>` 선언 금지 (WorldCanvas.tsx에만 존재)
- `_infra/`에 재사용 가능한 메시 추가 금지 → `_shared/mesh/`로
- `_shared/mesh/`에 단일 주체 컴포넌트 추가 금지 → `_infra/`로
- `index.ts` 배럴을 우회한 직접 경로 import 금지
- `features/` 간 직접 import 금지
- `worlds/`↔`features/` 컴포넌트/훅 직접 import 금지
- 단일 문자·무의미한 축약어 변수명 금지 (`p`, `u`, `BH` 등) → 의미가 드러나는 이름 사용
- `useFrame` 안에 무거운 연산 배치 금지
- `useFrame` 안에서 React `setState` 호출 금지 → `useRef` + Three.js 객체 직접 조작으로 대체
- R3F 씬 내 애니메이션 완료 감지를 `setInterval`/`setTimeout`으로 폴링 금지 → `useFrame` 조건 검사로 대체
- `TextureLoader` 사용 시 파일마다 `new LoadingManager()` 중복 선언 금지 → 씬 공유 `textureLoader.ts` 싱글턴에서 import
- `useEffect` 본문에서 `setState` 동기 호출 금지 (React Compiler 오류) → async IIFE 안에서 처리
- R3F `<Canvas>` 내부에서 `motion.*` 사용 금지
- 캐릭터에 `dynamic` RigidBody 사용 금지 → `kinematicPosition` 사용
- Rapier 도입 후 `position` 직접 수정 금지 → `setNextKinematicTranslation()` 사용
- `<Physics>` Provider를 씬별로 중복 선언 금지 → `WorldCanvas.tsx` 한 곳에만
- Rapier 사용 시 경계를 `MathUtils.clamp`로 처리 금지 → `fixed` RigidBody + Collider로 처리
- `*.tsx` 컴포넌트 파일에 API 호출, 데이터 변환, 게임 로직 직접 작성 금지 → 훅/스토어로 분리
- 서버 컴포넌트 파일에 `'use client'` 선언 금지 (필요 시 별도 파일로 분리)
- `<a>` 태그 사용 금지 → `<Link>`
- `<img>` 태그 사용 금지 → `<Image>`
- `NEXT_PUBLIC_` 없는 환경변수를 클라이언트 컴포넌트에서 참조 금지
