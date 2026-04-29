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
| UI              | @base-ui/react (헤드리스 UI 프리미티브)                      |
| 2D 캔버스       | konva · react-konva                                          |
| 아이콘          | lucide-react                                                 |
| 애니메이션      | motion                                                       |
| 컴포넌트 변형   | class-variance-authority (CVA)                               |
| 클래스 병합     | clsx · tailwind-merge                                        |
| 폰트            | Pretendard Variable                                          |
| 패키지 매니저   | pnpm                                                         |
| React Compiler  | babel-plugin-react-compiler 활성화                           |

---

## 폴더 구조

```
src/
├── app/                             # Next.js App Router — 라우팅 진입점만 담당
│   ├── layout.tsx                   # 모든 페이지 공통 layout
│   ├── (service)/                   # 일반 서비스 라우트 그룹 (URL 미노출)
│   │   ├── layout.tsx               # (선택) 서비스 공통 layout
│   │   ├── page.tsx                 # URL: /       → 랜딩 3D
│   │   ├── hub/page.tsx             # URL: /hub    → 허브 3D
│   │   ├── relay-drawing/page.tsx   # URL: /relay-drawing
│   │   ├── infinite-canvas/page.tsx # URL: /infinite-canvas
│   │   ├── flipbook/page.tsx        # URL: /flipbook
│   │   └── share/[id]/page.tsx      # URL: /share/:id
│   └── admin/                       # URL: /admin/* → 백오피스 (URL 노출)
│       ├── layout.tsx               # AdminAuthGuard + 사이드바 layout
│       └── {section}/page.tsx
│
├── worlds/                          # 3D 씬 코드 — 씬당 한 폴더
│   ├── landing/
│   │   ├── LandingLoader.tsx        # 'use client' + dynamic(ssr:false) 래퍼
│   │   ├── LandingCanvas.tsx        # <Canvas> + <Physics> 소유
│   │   ├── LandingScene.tsx         # 씬 루트
│   │   ├── constants.ts
│   │   ├── GroundMesh.tsx           # (선택)
│   │   ├── useLandingInteraction.ts # (선택)
│   │   └── objects/
│   │       └── {Name}Mesh.tsx
│   ├── hub/
│   │   ├── HubLoader.tsx
│   │   ├── HubCanvas.tsx
│   │   ├── HubScene.tsx
│   │   ├── useHubInteraction.ts
│   │   └── objects/
│   │       └── {Name}Mesh.tsx
│   ├── _infra/                      # 씬당 하나만 존재하는 단일 주체
│   │   ├── Lighting.tsx
│   │   └── Character.tsx
│   └── _shared/mesh/                # 씬 안에 여러 개 배치 가능한 재사용 메시
│       ├── index.ts
│       └── {Name}Mesh.tsx
│
├── features/                        # 도메인 기능 단위 (페이지·모달 모두 포함)
│   ├── relay-drawing/               # 2D 페이지 feature
│   │   ├── index.ts
│   │   ├── RelayDrawingPage.tsx     # 라우트 진입점
│   │   ├── RelayDrawingStage.tsx    # Konva Stage
│   │   ├── useRelayDrawing.ts
│   │   └── relayDrawingStore.ts
│   ├── fortune/                     # 모달 feature
│   │   ├── index.ts
│   │   ├── FortuneModal.tsx         # 모달 진입점
│   │   ├── useFortune.ts
│   │   └── fortuneStore.ts
│   ├── share/
│   │   ├── index.ts
│   │   ├── SharePage.tsx
│   │   └── useShare.ts
│   └── admin/                       # 백오피스 feature 컨테이너
│       └── {section}/
│           ├── index.ts
│           ├── {Section}Page.tsx
│           └── use{Section}.ts
│
├── shared/                          # 어느 레이어에서나 쓸 수 있는 공용 자원
│   ├── apis/                        # 백엔드 API 호출 함수 (도메인별 분리)
│   │   ├── index.ts
│   │   └── {domain}Api.ts
│   ├── assets/                      # 컴포넌트 import용 정적 자산 (svg, glb, mp3)
│   │   └── {name}.{ext}
│   ├── components/                  # 자체 구현 공용 UI 컴포넌트 (컴포넌트마다 폴더)
│   │   ├── index.ts
│   │   └── {ComponentName}/
│   │       ├── {ComponentName}.tsx
│   │       ├── {ComponentName}.types.ts  # 복잡한 Props인 경우
│   │       └── index.ts
│   ├── config/                      # env 검증, 환경별 설정, 피처 플래그
│   │   ├── index.ts
│   │   └── runtime.ts
│   ├── constants/                   # 환경 무관 전역 상수
│   │   ├── index.ts
│   │   └── {name}.ts
│   ├── hooks/                       # 재사용 React 훅
│   │   ├── index.ts
│   │   └── use{Name}.ts
│   ├── layouts/                     # 페이지 레이아웃 컴포넌트
│   │   ├── index.ts
│   │   └── {Name}Layout.tsx
│   ├── libs/                        # 외부 라이브러리 래퍼·설정
│   │   ├── index.ts
│   │   ├── apiClient.ts
│   │   └── utils.ts                 # cn() — clsx + tailwind-merge 래퍼
│   ├── stores/                      # 전역 Zustand store
│   │   ├── index.ts
│   │   └── {name}Store.ts
│   ├── styles/                      # CSS 토큰 시스템 진입점
│   │   ├── index.css                # 전체 CSS 진입점 (@import "tailwindcss" 포함)
│   │   ├── tokens/
│   │   │   ├── primitive/           # 원시 값 (색상 팔레트, 간격 스케일)
│   │   │   └── semantic/            # 역할 기반 토큰 (surface, fg, primary...)
│   │   └── layers/
│   │       ├── base.css             # 전역 리셋 + Pretendard 폰트
│   │       ├── theme.css            # Tailwind 테마 통합
│   │       └── utilities.css        # 타이포그래피 유틸리티 클래스
│   ├── types/                       # 공유 TypeScript 타입 정의
│   │   ├── index.ts
│   │   └── {domain}.ts
│   └── utils/                       # 순수 유틸리티 함수
│       ├── index.ts
│       └── {name}.ts
```

> **`_infra/`** — 씬당 하나만 존재하는 단일 주체 (Lighting, Character)
> **`_shared/mesh/`** — 같은 씬 안에 여러 개 배치 가능한 재사용 메시
> **`shared/assets/`** — `public/`이 아닌 번들러가 관리하는 정적 자산 (svg, glb, mp3)
> **`shared/config/`** — `process.env` 직접 참조 대신 환경 설정을 한 곳에서 관리
> **`shared/styles/`** — CSS 토큰 시스템. `app/layout.tsx`에서 `@/shared/styles/index.css` import
> **`shared/layouts/`** — 여러 페이지에서 공유하는 레이아웃 컴포넌트
> **`shared/libs/utils.ts`** — `cn()` 유틸리티 (`clsx` + `tailwind-merge` 래퍼)

---

## 멀티 페이지 라우팅

### URL 매핑

| URL                  | 진입점 컴포넌트                   | 도메인      |
| -------------------- | --------------------------------- | ----------- |
| `/`                  | `LandingLoader`                   | 3D          |
| `/hub`               | `HubLoader` + 모달들              | 3D + DOM    |
| `/relay-drawing`     | `RelayDrawingPage`                | 2D (Konva)  |
| `/infinite-canvas`   | `InfiniteCanvasPage`              | 2D (Konva)  |
| `/flipbook`          | `FlipbookPage`                    | 2D          |
| `/share/[id]`        | `SharePage`                       | 일반        |
| `/admin/*`           | `features/admin/{section}Page`    | 백오피스    |

### (service) 라우트 그룹

`app/(service)/`의 괄호 안 폴더명은 URL에 나타나지 않습니다. 사용자에게 깔끔한 URL(`/`, `/hub`)을 제공하면서 코드 구조상 "일반 서비스 페이지" 그룹화가 가능합니다. 그룹별 shared layout도 작성할 수 있습니다.

```tsx
// app/(service)/layout.tsx — 서비스 페이지 공통 layout (예: 풀스크린)
export default function ServiceLayout({ children }) {
  return <div className="h-screen overflow-hidden">{children}</div>;
}
```

### admin 라우트

`app/admin/`은 URL에 `/admin`이 노출됩니다. 이를 통해:
- `middleware.ts`에서 `pathname.startsWith('/admin')` 한 줄로 인증 처리
- `robots.txt`에 `Disallow: /admin/` 추가로 SEO 차단
- `app/admin/layout.tsx`에서 사이드바 + 권한 검사 일괄 처리

```tsx
// app/admin/layout.tsx
export default function AdminLayout({ children }) {
  return (
    <AdminAuthGuard>
      <div className="grid grid-cols-[200px_1fr]">
        <AdminSidebar />
        <main>{children}</main>
      </div>
    </AdminAuthGuard>
  );
}
```

**이중 인증 게이트:**
1. `middleware.ts` → 로그인 여부 확인 (미인증 → `/login` 리다이렉트)
2. `app/admin/layout.tsx` → 관리자 권한 확인 (권한 없음 → 403)

---

## 3D 씬 — 4단계 진입점 패턴

3D 페이지는 한 라우트에 도달하기까지 **4단계 컴포넌트 체인**을 거칩니다.

```
[1] app/(service)/page.tsx              Server Component — 라우팅 + OG 메타데이터
        │ import
        ▼
[2] worlds/{scene}/{Scene}Loader.tsx    'use client' + dynamic(ssr:false) 래퍼
        │ dynamic import
        ▼
[3] worlds/{scene}/{Scene}Canvas.tsx    <Canvas> + <Physics> 소유
        │
        ▼
[4] worlds/{scene}/{Scene}Scene.tsx     씬 루트 — Lighting, Character, 오브젝트 조립
```

각 단계의 책임:

| 단계 | 환경 | 책임 |
| ---- | ---- | ---- |
| `page.tsx` | Server | 라우팅, OG 메타데이터 |
| `{Scene}Loader.tsx` | Client | `dynamic + ssr:false` 래퍼 |
| `{Scene}Canvas.tsx` | Client | `<Canvas>` + `<Physics>` |
| `{Scene}Scene.tsx` | Client | 씬 콘텐츠 조립 |

```tsx
// worlds/landing/LandingLoader.tsx
"use client";
import dynamic from "next/dynamic";
const LandingCanvas = dynamic(() => import("./LandingCanvas"), { ssr: false });
export default function LandingLoader() {
  return <LandingCanvas />;
}

// worlds/landing/LandingCanvas.tsx
"use client";
import { Canvas } from "@react-three/fiber";
import { Physics } from "@react-three/rapier";
import LandingScene from "./LandingScene";
export default function LandingCanvas() {
  return (
    <Canvas>
      <Physics gravity={[0, -9.81, 0]}>
        <LandingScene />
      </Physics>
    </Canvas>
  );
}

// app/(service)/page.tsx  — Server Component
import LandingLoader from "@/worlds/landing/LandingLoader";
export default function Page() {
  return <LandingLoader />;
}
```

**이 분리가 필요한 이유:**
- `page.tsx`는 Server Component이므로 R3F를 직접 import 불가 → 빌드 실패
- `dynamic + ssr:false`는 Server Component에서 사용 불가 (Next.js 16 규칙) → Loader 레이어 필요
- Canvas와 씬 코드를 한 파일에 두면 파일이 비대해지고 책임이 뒤섞임

### 허브에서 모달 띄우기

Canvas와 DOM 모달은 형제(sibling) 관계입니다. `<Canvas>`는 DOM 자식을 가질 수 없기 때문.

```tsx
// app/(service)/hub/page.tsx
<>
  <HubLoader />      {/* 전체 화면 Canvas */}
  <FortuneModal />   {/* active 시 fixed + z-index로 위에 띄움 */}
</>
```

모달 활성화는 store를 통해 간접 연결합니다:

```
[HubScene] 센서 감지 → [useHubInteraction] 키 입력 →
  fortuneStore.setActive(true) → [FortuneModal] 렌더
```

---

## _infra/ vs _shared/mesh/

| 기준 | `_infra/` | `_shared/mesh/` |
| ---- | --------- | --------------- |
| 인스턴스 수 | 씬당 **1개** | 씬 안에 **여러 개** 가능 |
| 예시 | `Lighting.tsx`, `Character.tsx` | `TreeMesh.tsx`, `RockMesh.tsx` |

새 컴포넌트를 만들 때 자문:
- "이 씬에 여러 개 둘 수 있나?" → `_shared/mesh/`
- "이 씬에 무조건 하나만 있어야 하나?" → `_infra/`

---

## 폴더 네이밍

| 위치               | 케이스       | 예시                                                                                                           |
| ------------------ | ------------ | -------------------------------------------------------------------------------------------------------------- |
| 최상위 도메인 폴더 | `lowercase`  | `worlds/`, `features/`, `shared/`                                                                              |
| 씬 / 도메인 하위   | `lowercase`  | `landing/`, `hub/`                                                                                             |
| 도구 하위 (복수형) | `lowercase`  | `apis/`, `assets/`, `components/`, `config/`, `constants/`, `hooks/`, `layouts/`, `libs/`, `stores/`, `styles/`, `types/`, `utils/`, `objects/` |
| feature 단위       | `kebab-case` | `relay-drawing/`, `label-printer/`                                                                             |
| 인프라 공유        | `_prefix`    | `_infra/`, `_shared/`                                                                                          |

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

| 종류                 | 케이스                | 예시                                         |
| -------------------- | --------------------- | -------------------------------------------- |
| React 컴포넌트       | `PascalCase.tsx`      | `LandingScene.tsx`, `FortuneModal.tsx`       |
| 훅                   | `camelCase.ts`        | `useCharacterControls.ts`                    |
| 스토어               | `camelCase.ts`        | `fortuneStore.ts`                            |
| 상수                 | `constants.ts` (고정) | `constants.ts`                               |
| 유틸                 | `camelCase.ts`        | `utils.ts`                                   |
| 배럴                 | `index.ts` (고정)     | `index.ts`                                   |

---

## 파일명 접미사 컨벤션

| 접미사               | 의미                                                    | 위치                          |
| -------------------- | ------------------------------------------------------- | ----------------------------- |
| `{Scene}Loader.tsx`  | 3D 라우트 진입점. `'use client'` + `dynamic(ssr:false)` | `worlds/{scene}/`             |
| `{Scene}Canvas.tsx`  | `<Canvas>` + `<Physics>` 소유                           | `worlds/{scene}/`             |
| `{Scene}Scene.tsx`   | 씬 루트. Canvas 컨텍스트 안에서 오브젝트 조립           | `worlds/{scene}/`             |
| `*Mesh.tsx`          | 3D 지오메트리 단위. `<Canvas>` 선언 없음                | `worlds/`, `features/`        |
| `*Page.tsx`          | 2D 라우트 진입점 (URL이 있는 페이지)                    | `features/`                   |
| `*Modal.tsx`         | DOM 오버레이 모달                                       | `features/`                   |
| `*Stage.tsx`         | Konva `<Stage>` 소유 2D 캔버스                          | `features/`                   |
| `*Visual.tsx`        | 독립 `<Canvas>`를 소유하는 3D 컴포넌트                  | `features/`                   |
| `use*Interaction.ts` | 씬 상호작용 훅 (거리 감지 + 키 이벤트 처리)             | `worlds/{scene}/`             |
| `use*.ts`            | 그 외 React 훅                                          | `features/`, `shared/hooks/`  |

### 진입 방식은 폴더가 아닌 접미사로

같은 `features/{name}/` 안에 있어도 접미사가 사용자의 진입 방식을 알려줍니다. 기획 변경(모달 → 페이지 전환)이 일어났을 때 접미사만 바꾸면 되고, 내부 훅·스토어는 그대로 재사용됩니다.

---

## 자원 폴더화 규칙

> **단위 파일이 1개면 평면 파일로 두고, 2개째 추가될 때 즉시 폴더화 + index.ts 배럴을 만든다.**

이 규칙은 PR merge 차단 사유로 다룹니다.

### 진화 과정

```
# 시작 — 훅 1개: 평면이 정답
features/relay-drawing/
├── RelayDrawingPage.tsx
└── useRelayDrawing.ts        ← 훅 1개

# 2번째 훅 추가 — 즉시 폴더화 (같은 PR에서)
features/relay-drawing/
├── RelayDrawingPage.tsx
└── hooks/
    ├── index.ts              ← 배럴 (필수!)
    ├── useRelayDrawing.ts    ← 기존 파일 이동
    └── useRelayDrawingHistory.ts  ← 새 훅
```

각 자원 타입은 독립적으로 진화합니다. 훅이 폴더화되어도 상수가 1개면 상수는 여전히 평면으로 유지합니다.

| 자원 | 1개 (평면) | 2개+ (폴더화 + 배럴 필수) |
| ---- | ---------- | ------------------------- |
| 훅 | `use{Feat}.ts` | `hooks/` |
| 상수 | `constants.ts` | `constants/` |
| 유틸 | `utils.ts` | `utils/` |
| 타입 | `{feat}.types.ts` | `types/` |
| 컴포넌트 | `{Sub}.tsx` | `components/` |

**merge 차단 조건:**
- 같은 자원 타입 파일이 2개 이상인데 폴더화되지 않은 PR
- 폴더는 만들었지만 `index.ts` 배럴이 없는 PR

### index.ts 배럴이 필수인 이유

```ts
// ✅ 배럴 있음 — 내부 구조가 숨겨짐
import { useRelayDrawing } from "@/features/relay-drawing";

// ❌ 배럴 없음 — 내부 경로가 import에 노출됨
import { useRelayDrawing } from "@/features/relay-drawing/hooks/useRelayDrawing";
```

배럴이 있으면 내부 파일 이동·이름 변경이 외부 import에 영향을 주지 않습니다.

---

## config/ vs constants/

```ts
// shared/constants/ — 환경 무관, 항상 같은 값
export const CHARACTER_HEIGHT = 1.8;      // dev/prod 모두 1.8
export const CHARACTER_WALK_SPEED = 5;

// shared/config/ — 환경 의존, 환경마다 다를 수 있는 값
export const runtime = {
  pollingIntervalMs: env.isDev ? 1000 : 5000,
  apiUrl: env.apiUrl,
};
```

판단 기준: **이 값이 dev/staging/prod에서 다르게 설정될 가능성이 있나?** 있으면 `config/`, 없으면 `constants/`.

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

```tsx
// ✅ 올바른 구조
export default function LabelPrinter() {
  const { labels, addLabel } = useLabelPrinter()  // 훅에서 로직 가져옴
  return <div>{labels.map(label => <LabelItem key={label.id} {...label} />)}</div>
}

// ❌ 금지 — API 호출이 컴포넌트 안에
export default function LabelPrinter() {
  const addLabel = async () => {
    const result = await api.post("/labels", { ... })  // 훅으로 분리해야 함
  }
}
```

---

## 배럴 export (index.ts)

모든 폴더는 `index.ts`를 통해 외부에 단일 진입점을 제공합니다.

```ts
// ✅ 배럴을 통한 import
import { useRelayDrawing, RelayDrawingPage } from "@/features/relay-drawing";

// ❌ 내부 경로 직접 참조 금지
import { useRelayDrawing } from "@/features/relay-drawing/useRelayDrawing";
```

**예외:**
- `_infra/` 파일은 직접 import (순환 참조 방지)
- `use*Interaction.ts`의 feature store import는 허용 (write 전용)

---

## 의존 방향 규칙

```
app → worlds · features → shared
```

- `features/` 간 직접 import 금지 → feature 간 공유 상태는 `shared/stores/` 경유
- `worlds/` ↔ `features/` 간 컴포넌트/훅 직접 import 금지
- `shared/`는 어느 레이어에서도 참조 가능

### feature-level store 예외

씬 상호작용 훅(`use*Interaction.ts`)은 feature store에 직접 write할 수 있습니다.

```
worlds/hub/useHubInteraction.ts
  ├──write──▶ features/fortune/fortuneStore.ts
  └──write──▶ features/label-printer/labelPrinterStore.ts
```

- feature store write는 예외적으로 허용
- 단, feature 컴포넌트/훅 import는 여전히 금지
- 씬 이탈 시 `active = false`로 store 값을 리셋해 stale 상태 방지

### 백오피스 격리

```
✅ features/admin/*  →  shared/*
❌ features/admin/*  →  features/{일반 feature}/*
❌ features/{일반}/* →  features/admin/*
```

백오피스와 일반 서비스 코드가 서로 의존하면 번들이 섞여 사용자에게 불필요한 코드가 전달됩니다. 공유 자산은 모두 `shared/`를 경유합니다.

---

## Next.js 서버/클라이언트 경계 규칙

### app/ 레이어 — 라우팅 진입점

- `app/` 파일은 라우팅 진입점과 메타데이터 선언만 담당
- 실제 UI/로직은 `worlds/`, `features/`에서 가져옴
- **Next.js 16에서 `dynamic + ssr:false`는 Server Component에서 사용 불가** → `{Scene}Loader.tsx` Client Component 래퍼 경유

### worlds/ 레이어 — 씬별 클라이언트 경계

- `{Scene}Loader.tsx`와 `{Scene}Canvas.tsx`에만 `'use client'` 선언
- 씬의 다른 파일들(`{Scene}Scene.tsx`, `*Mesh.tsx`, `use*Interaction.ts`)에는 불필요 (Canvas에서 자동 전파)
- `<Canvas>` 선언은 `{Scene}Canvas.tsx`에만
- `<Physics>` 선언은 `{Scene}Canvas.tsx` 내부에만

### features/ 레이어

- 3D가 필요한 feature: `*Visual.tsx`가 독립 `<Canvas>` 소유 + `'use client'` 선언
- 순수 UI feature: 서버 컴포넌트로 유지 가능

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
- `process.env` 직접 참조는 feature 코드에서 금지 → `shared/config/`를 통해 접근

---

## 라이브러리별 사용 규칙

### motion

- DOM 요소 애니메이션에 사용 (`motion.div`, `AnimatePresence` 등)
- R3F `<Canvas>` 내부에서 `motion.*` 사용 금지

### @react-three/rapier

물리 엔진은 캐릭터 이동, 충돌 감지, 디지털 트윈 설비 시뮬레이션에 사용합니다.

**Physics Provider**: `<Physics>`는 `{Scene}Canvas.tsx` 내 `<Canvas>` 바로 하위에 하나만 배치합니다. 씬 컴포넌트나 오브젝트에 중복 선언 금지.

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

- 2D 캔버스 기능에만 사용, `features/` 하위에 `*Stage.tsx` 파일로 배치
- `Stage > Layer > Shape` 구조 준수

### CSS 파일 구조

CSS 토큰 시스템은 `shared/styles/`에서 관리합니다.

```
shared/styles/
├── index.css           ← 전체 진입점 (app/layout.tsx에서 import)
├── tokens/
│   ├── primitive/      ← 원시 색상 팔레트, 간격 스케일 (직접 참조 금지)
│   └── semantic/       ← 역할 기반 토큰 (bg-surface-*, text-fg-*, border-*)
└── layers/
    ├── base.css        ← 전역 리셋 + Pretendard 폰트
    ├── theme.css       ← Tailwind 테마 통합
    └── utilities.css   ← 타이포그래피 유틸리티 클래스 (h1-b, body-r...)
```

CSS 레이어 로딩 순서:
1. Primitive tokens
2. Semantic tokens
3. Tailwind layers (theme → utilities → base)

- `app/layout.tsx`에서 `@/shared/styles/index.css` import
- 컴포넌트 스타일은 Tailwind utility classes로 처리 (별도 CSS 파일 금지)

---

## 디자인 시스템

이 프로젝트는 토큰 기반 2계층 디자인 시스템을 사용합니다.

### 구조

```
shared/styles/
├── tokens/
│   ├── primitive/      # 원시 값 (색상 팔레트, 간격 스케일) — 직접 참조 금지
│   └── semantic/       # 역할 기반 토큰 (surface, fg, primary...) — 컴포넌트에서 사용
└── layers/
    ├── base.css        # 전역 리셋 + Pretendard 폰트
    ├── theme.css       # Tailwind 테마 통합
    └── utilities.css   # 타이포그래피 유틸리티 클래스
```

### 색상 시스템

브랜드 색상은 CSS 변수로 정의되며, Tailwind 클래스로 등록됩니다.

| 토큰                    | 용도                       |
| ----------------------- | -------------------------- |
| `bg-primary-1`          | CTA 버튼 배경, 주요 강조   |
| `bg-primary-5`          | 활성 탭 배경 (연한 강조색) |
| `bg-surface-default`    | 기본 카드/화면 배경        |
| `bg-surface-subtle`     | 구분선 위 배경             |
| `text-fg-primary`       | 주요 본문 텍스트           |
| `text-fg-secondary`     | 보조 텍스트                |
| `text-fg-disabled`      | 비활성 텍스트              |
| `text-fg-inverse`       | 어두운 배경 위 텍스트      |
| `border-border-default` | 기본 테두리                |
| `text-primary-2`        | 링크, 활성 아이콘 색       |

### 타이포그래피

한국어 최적화 폰트 **Pretendard Variable**을 사용합니다.
`h1-b`, `body-r`, `caption-m` 등 유틸리티 클래스로 font-size, weight, line-height를 한번에 적용합니다.

```
h1-b (32px/700)   h2-b (24px/700)   h3-b (20px/700)   h4-b (16px/700)
body-l-b / body-l-m / body-l-r  (16px, weight: 700/500/400)
body-b  / body-m  / body-r      (14px, weight: 700/500/400)
caption-b / caption-m / caption-r (12px, weight: 700/500/400)
```

### 컴포넌트 계층

| 계층 | 위치 | 예시 |
|------|------|------|
| 헤드리스 프리미티브 | `@base-ui/react` (외부) | Dialog, Tooltip |
| 디자인 시스템 컴포넌트 | `shared/components/` | Button, Card |
| Feature 컴포넌트 | `features/*/` | FortuneModal, RelayDrawingPage |
| 레이아웃 | `shared/layouts/` | MainLayout |

### 스타일링 규칙

1. 컴포넌트에서는 **Semantic 토큰**만 사용 (`bg-surface-default`, `text-fg-primary`)
2. 타이포그래피는 **유틸리티 클래스** 사용 (`h2-b`, `body-r`) — raw Tailwind font 클래스 직접 조합 금지
3. 조건부 클래스 병합은 `cn()` 유틸리티 사용 (`clsx` + `tailwind-merge`)
4. 변형(variant)이 있는 컴포넌트는 **CVA** 사용

```tsx
// ✅ 올바른 사용
import { cn } from '@/shared/libs'
className="h2-b text-fg-primary"
className={cn("body-r text-fg-primary", isActive && "text-primary-2")}

// ❌ 금지
className="text-2xl font-bold"           // raw Tailwind font 클래스
className="bg-cream-50 text-brown-720"   // Primitive 토큰 직접 참조
```

---

## 핵심 금지사항

- `worlds/{scene}/{Scene}Canvas.tsx` 외에서 `<Canvas>` 선언 금지
- `worlds/{scene}/{Scene}Canvas.tsx` 외에서 `<Physics>` 선언 금지
- `_infra/`에 재사용 가능한 메시 추가 금지 → `_shared/mesh/`로
- `_shared/mesh/`에 단일 주체 컴포넌트 추가 금지 → `_infra/`로
- `index.ts` 배럴을 우회한 직접 경로 import 금지
- `features/` 간 직접 import 금지
- `worlds/` ↔ `features/` 컴포넌트/훅 직접 import 금지
- `features/admin/` ↔ 일반 `features/` 간 import 금지 (양방향)
- 단일 문자·무의미한 축약어 변수명 금지 (`p`, `u`, `BH` 등)
- `useFrame` 안에 무거운 연산 배치 금지
- `useFrame` 안에서 React `setState` 호출 금지 → `useRef` + Three.js 직접 조작
- R3F 씬 내 애니메이션 완료 감지를 `setInterval`/`setTimeout`으로 폴링 금지
- `TextureLoader` 사용 시 파일마다 `new LoadingManager()` 중복 선언 금지
- `useEffect` 본문에서 `setState` 동기 호출 금지 (React Compiler 오류)
- R3F `<Canvas>` 내부에서 `motion.*` 사용 금지
- 캐릭터에 `dynamic` RigidBody 사용 금지 → `kinematicPosition` 사용
- Rapier 도입 후 `position` 직접 수정 금지 → `setNextKinematicTranslation()` 사용
- Rapier 사용 시 경계를 `MathUtils.clamp`로 처리 금지
- `*.tsx` 컴포넌트 파일에 API 호출, 데이터 변환, 게임 로직 직접 작성 금지
- 불필요한 `'use client'` 선언 금지 (판단 기준: React 훅/이벤트 핸들러/브라우저 API 사용 여부)
- `<a>` 태그 사용 금지 → `<Link>`
- `<img>` 태그 사용 금지 → `<Image>`
- feature 코드에서 `process.env.X` 직접 참조 금지 → `shared/config/` 경유
- `NEXT_PUBLIC_` 없는 환경변수를 클라이언트 컴포넌트에서 참조 금지
- 자원 2개째 추가 시 폴더화 + `index.ts` 배럴 없이 PR merge 금지
- 일반 서비스 페이지를 `app/(service)/` 없이 `app/` 직하에 배치 금지
- Semantic 토큰 대신 Primitive 토큰 직접 참조 금지 (`bg-cream-50`, `text-brown-720` 등)
- 타이포그래피에 raw Tailwind font 클래스 직접 조합 금지 → `h1-b`, `body-r` 유틸리티 클래스 사용
- 조건부 클래스 병합 시 `cn()` 없이 문자열 연결 금지 → `cn()` 유틸리티 사용
- `shared/assets/` 대신 `public/`에 컴포넌트 import용 자산 배치 금지
