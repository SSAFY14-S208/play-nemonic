# Frontend — Claude Code Instructions

## 기술 스택

Next.js 16 (App Router) + TypeScript + Tailwind CSS v4
@react-three/fiber · @react-three/drei · @react-three/rapier · three · Zustand
ky · @base-ui/react · lucide-react · motion
konva · react-konva
class-variance-authority · clsx · tailwind-merge
폰트: Pretendard Variable
패키지 매니저: **pnpm**
React Compiler (babel-plugin-react-compiler) 활성화됨

---

## 초기 의존성 설치 (반드시 준수)

**기술 스택에 명시된 라이브러리는 프로젝트 초기화 시점에 모두 설치해야 합니다.**
나중에 필요할 것 같은 라이브러리를 빠뜨리면 peer 의존성 충돌이 발생하므로, 아래 명령을 그대로 실행합니다.

```bash
pnpm add next react react-dom @react-three/fiber @react-three/drei @react-three/rapier three
pnpm add @base-ui/react ky lucide-react motion konva react-konva zustand
pnpm add class-variance-authority clsx tailwind-merge
pnpm add -D typescript @types/node @types/react @types/react-dom @types/three eslint eslint-config-next tailwindcss @tailwindcss/postcss postcss
```

### 설치 확인 체크리스트

새 프로젝트를 시작하거나 기존 프로젝트에 참여할 때, `package.json`의 `dependencies`에 아래 항목이 모두 있는지 확인합니다. 하나라도 없으면 즉시 설치합니다.

| 패키지                  | 용도                                   |
| ----------------------- | -------------------------------------- |
| `@react-three/fiber`    | R3F — Three.js React 렌더러            |
| `@react-three/drei`     | R3F 헬퍼 (Grid, Sky, OrbitControls 등) |
| `@react-three/rapier`   | 물리 엔진 — 충돌·경계·센서 처리        |
| `three`                 | Three.js 코어                          |
| `zustand`               | 전역 상태 관리                         |
| `ky`                    | HTTP 클라이언트                        |
| `@base-ui/react`        | 헤드리스 UI 프리미티브                 |
| `lucide-react`          | 아이콘                                 |
| `motion`                | DOM 애니메이션                         |
| `konva` + `react-konva`       | 2D 캔버스                              |
| `class-variance-authority`   | 컴포넌트 변형(variant) 관리            |
| `clsx`                       | 조건부 클래스 병합                     |
| `tailwind-merge`             | Tailwind 클래스 충돌 없는 병합         |

---

## 폴더 구조

```
src/
├── app/                             # Next.js App Router — 라우팅 진입점만 담당
│   ├── layout.tsx
│   ├── (service)/                   # 일반 서비스 라우트 그룹 (URL 미노출)
│   │   ├── layout.tsx               # (선택) 서비스 공통 layout
│   │   ├── page.tsx                 # URL: /       → 랜딩 3D
│   │   ├── hub/page.tsx             # URL: /hub    → 허브 3D
│   │   ├── relay-drawing/page.tsx
│   │   ├── infinite-canvas/page.tsx
│   │   ├── flipbook/page.tsx
│   │   └── share/[id]/page.tsx
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
│   └── _shared/
│       └── mesh/                   # 씬 안에 여러 개 배치 가능한 재사용 메시
│           ├── index.ts
│           └── {Name}Mesh.tsx
│
├── features/
│   └── {feature-name}/
│       ├── index.ts
│       ├── {FeatureName}Page.tsx     # 라우트 진입점 (URL이 있는 페이지)
│       ├── {FeatureName}Modal.tsx    # DOM 오버레이 모달
│       ├── {FeatureName}Stage.tsx    # Konva Stage 소유 (선택)
│       ├── {FeatureName}Visual.tsx   # 독립 <Canvas> 소유 (선택)
│       ├── use{FeatureName}.ts       # 비즈니스 로직 훅
│       └── {featureName}Store.ts    # feature 전용 Zustand store (선택)
│
├── shared/
│   ├── apis/                        # 백엔드 API 호출 함수 (도메인별 분리)
│   │   ├── index.ts
│   │   └── {domain}Api.ts
│   ├── assets/                      # 번들러 관리 정적 자산 (svg, glb, mp3) — public/ 아님
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
│   ├── hooks/
│   │   ├── index.ts
│   │   └── use{Name}.ts
│   ├── layouts/                     # 페이지 레이아웃 컴포넌트
│   │   ├── index.ts
│   │   └── {Name}Layout.tsx
│   ├── libs/
│   │   ├── index.ts
│   │   ├── apiClient.ts
│   │   └── cn.ts                    # cn() — clsx + tailwind-merge 래퍼
│   ├── stores/
│   │   ├── index.ts
│   │   └── {name}Store.ts
│   ├── styles/                      # CSS 토큰 시스템 진입점
│   │   ├── index.css                # 전체 CSS 진입점 (@import "tailwindcss" 포함)
│   │   ├── tokens/
│   │   │   ├── primitive/           # 원시 값 (색상 팔레트, 간격 스케일)
│   │   │   └── semantic/            # 역할 기반 토큰 (surface, fg, primary...)
│   │   └── layers/
│   │       ├── base.css             # 전역 리셋
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
> **`_shared/mesh/`** — 씬 안에 여러 개 배치 가능한 재사용 컴포넌트
> **`shared/assets/`** — `public/`이 아닌 번들러가 관리하는 정적 자산 (svg, glb, mp3)
> **`shared/config/`** — `process.env` 직접 참조 대신 환경 설정을 한 곳에서 관리
> **`shared/styles/`** — CSS 토큰 시스템. `app/layout.tsx`에서 `@/shared/styles/index.css` import
> **`shared/layouts/`** — 여러 페이지에서 공유하는 레이아웃 컴포넌트
> **`shared/libs/cn.ts`** — `cn()` 유틸리티 (`clsx` + `tailwind-merge` 래퍼)

---

## 폴더 네이밍

| 위치               | 케이스       | 예시                                                                                                                    |
| ------------------ | ------------ | ----------------------------------------------------------------------------------------------------------------------- |
| 최상위 도메인 폴더 | `lowercase`  | `worlds/`, `features/`, `shared/`                                                                                       |
| 씬 / 도메인 하위   | `lowercase`  | `landing/`, `hub/`                                                                                                      |
| 도구 하위 (복수형) | `lowercase`  | `apis/`, `assets/`, `components/`, `config/`, `constants/`, `hooks/`, `layouts/`, `libs/`, `stores/`, `styles/`, `types/`, `utils/`, `objects/` |
| feature 단위       | `kebab-case` | `relay-drawing/`, `label-printer/`                                                                                      |
| 인프라 공유        | `_prefix`    | `_infra/`, `_shared/`                                                                                                   |

---

## 변수·파라미터 네이밍

코드를 읽는 사람이 주석 없이도 의미를 파악할 수 있어야 합니다.

### 금지: 단일 문자·무의미한 축약어

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

### 규칙

- **상수**: 역할이 드러나는 `SCREAMING_SNAKE_CASE` — `PRINTER_BODY_HEIGHT`, `HUD_DISTANCE`, `IDENTITY_QUATERNION`
- **지역 변수**: `camelCase`로 의미 전달 — `doorOpenProgress`, `uniformScale`, `cameraForward`
- **파라미터**: 함수 시그니처만 봐도 역할이 명확해야 함 — `delta` (not `d`), `progress` (not `p`)
- **루프 변수 예외**: `i`, `j`는 단순 인덱스에 한해 허용. 그 외 단일 문자 금지.

```ts
// ❌ 금지
const BH = 0.7;
const p = doorOpenProgress.current;
meshRef.current.scale.x = u * (1 - p);

// ✅ 올바른 예
const BODY_HEIGHT = 0.7;
const progress = doorOpenProgress.current;
meshRef.current.scale.x = uniformScale * (1 - progress);
```

---

## 파일 네이밍

| 종류                 | 케이스                | 예시                                 |
| -------------------- | --------------------- | ------------------------------------ |
| React 컴포넌트       | `PascalCase.tsx`      | `LandingScene.tsx`, `FortuneModal.tsx` |
| 훅                   | `camelCase.ts`        | `useCharacterControls.ts`            |
| 스토어               | `camelCase.ts`        | `fortuneStore.ts`                    |
| 상수                 | `constants.ts` (고정) |                                      |
| 유틸                 | `camelCase.ts`        | `utils.ts`                           |
| 배럴                 | `index.ts` (고정)     |                                      |

### 파일명 접미사

| 접미사               | 의미                                         | 위치                         |
| -------------------- | -------------------------------------------- | ---------------------------- |
| `{Scene}Loader.tsx`  | 3D 라우트 진입점. `'use client'` + `dynamic(ssr:false)` | `worlds/{scene}/`   |
| `{Scene}Canvas.tsx`  | `<Canvas>` + `<Physics>` 소유                | `worlds/{scene}/`            |
| `{Scene}Scene.tsx`   | 씬 루트. Canvas 컨텍스트 안에서 오브젝트 조립 | `worlds/{scene}/`           |
| `*Mesh.tsx`          | 3D 지오메트리 단위. `<Canvas>` 선언 없음     | `worlds/`, `features/`       |
| `*Page.tsx`          | 2D 라우트 진입점 (URL이 있는 페이지)         | `features/`                  |
| `*Modal.tsx`         | DOM 오버레이 모달                            | `features/`                  |
| `*Stage.tsx`         | Konva `<Stage>` 소유 2D 캔버스               | `features/`                  |
| `*Visual.tsx`        | 독립 `<Canvas>`를 소유하는 3D 컴포넌트       | `features/`                  |
| `use*Interaction.ts` | 씬 상호작용 훅 (거리 감지 + 키 이벤트)       | `worlds/{scene}/`            |
| `use*.ts`            | 그 외 React 훅                               | `features/`, `shared/hooks/` |

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

- `features/` 간 직접 import 금지 → `shared/stores/` 경유
- `worlds/`↔`features/` 컴포넌트/훅 직접 import 금지
- `shared/`는 어느 레이어에서도 참조 가능

### feature-level store 예외

씬의 근접 감지 결과처럼 특정 feature에만 해당하는 트리거 상태는 해당 `features/{feature}/` 안에 store를 두고, `worlds/{scene}/use*Interaction.ts`가 직접 write합니다.

```
worlds/hub/useHubInteraction.ts
  ├──write──▶ features/fortune/fortuneStore.ts
  └──write──▶ features/label-printer/labelPrinterStore.ts
```

- `use*Interaction.ts`의 feature store write는 예외적으로 허용
- 단, feature 컴포넌트/훅 import는 여전히 금지
- **씬 이탈 시 `active = false`로 store 값을 리셋해 stale 상태 방지**

### 백오피스 격리

```
✅ features/admin/*  →  shared/*
❌ features/admin/*  →  features/{일반 feature}/*
❌ features/{일반}/* →  features/admin/*
```

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
- **Next.js 16에서 `dynamic + ssr: false`는 Server Component에서 사용 불가** → `{Scene}Loader.tsx` Client Component 래퍼로 분리
- 일반 서비스 페이지는 `app/(service)/` 하위에 배치 (`app/` 직하에 배치 금지)

```tsx
// worlds/landing/LandingLoader.tsx  ← Client Component 래퍼
"use client";
import dynamic from "next/dynamic";
const LandingCanvas = dynamic(() => import("./LandingCanvas"), { ssr: false });
export default function LandingLoader() {
  return <LandingCanvas />;
}

// app/(service)/page.tsx  ← Server Component (ssr:false 코드 없음)
import LandingLoader from "@/worlds/landing/LandingLoader";
export default function Page() {
  return <LandingLoader />;
}
```

```tsx
// app/(service)/share/[id]/page.tsx
export async function generateMetadata({ params }) {
  // OG 태그만 여기서 처리 — 백엔드 API 호출 가능
  const result = await fetch(
    `${process.env.API_URL}/results/${params.id}`,
  ).then((r) => r.json());
  return { openGraph: { title: result.name, images: [result.imageUrl] } };
}
export default function SharePage({ params }) {
  return <SharePage id={params.id} />; // 실제 UI는 features/에서
}
```

### 공통 레이아웃 — app/layout.tsx에만

페이지 간 공통 UI(Header, Footer, Sidebar 등)는 반드시 `app/layout.tsx`에 작성합니다.
각 `page.tsx`에 공통 UI를 반복 작성하지 않습니다.

특정 라우트 그룹에만 다른 레이아웃이 필요한 경우 중첩 `layout.tsx`를 사용합니다.

### worlds/ — 씬별 클라이언트 경계

- 각 씬의 `{Scene}Loader.tsx`와 `{Scene}Canvas.tsx`에만 `'use client'` 선언
- 씬의 다른 파일들(`{Scene}Scene.tsx`, `*Mesh.tsx`, `use*Interaction.ts`)에는 불필요 (Canvas에서 자동 전파)
- `<Canvas>` 선언은 `{Scene}Canvas.tsx`에만 — 씬 컴포넌트나 오브젝트에 금지
- `<Physics>` 선언은 `{Scene}Canvas.tsx` 내부에만

### features/ — 명시적 선언

- 3D가 필요한 feature: `*Visual.tsx`에 `'use client'` 선언
- 순수 UI feature: 서버 컴포넌트 유지 가능

---

## API 클라이언트 규칙

이 프로젝트는 Spring 백엔드와 협업합니다. DB를 직접 조작하지 않으며, 모든 데이터는 백엔드 API를 통해서만 접근합니다.

### 인증 모델 — 익명 UUID

JWT/세션 쿠키를 사용하지 않습니다. 서버는 첫 진입 시 `userUuid`를 발급하고, 이후 모든 식별은 다음 두 위치 중 하나로 이뤄집니다.

- **요청 본문(body)** 의 `userUuid` 필드: POST / PUT / PATCH
- **쿼리 파라미터(query)** 의 `userUuid`: GET / DELETE
- 일부 엔드포인트는 `userUuid` 불필요(`POST /users/anonymous`, `GET /community/{id}` 등)

`Authorization` 헤더 / Bearer 토큰 / `localStorage.getItem('token')` 패턴을 다시 도입하지 않습니다. `apiClient`(트랜스포트)는 인증 모델을 모르고, 도메인 API 계층(`shared/apis/{domain}Api.ts`)에서 호출자가 store에서 읽은 `userUuid`를 명시 인자로 받아 주입합니다. 도메인 API는 **객체로 묶지 않고 개별 함수로 export**합니다(아래 네이밍 규칙 참조).

### apiClient 트랜스포트

```ts
// shared/libs/apiClient.ts
import ky from "ky";
import { runtime } from "@/shared/config";

type Query = Record<string, string | number | boolean>;

const client = ky.create({
  prefix: `${runtime.apiUrl}/api/v1`,
  timeout: 30_000,
});

export const api = {
  get: <T>(path: string, searchParams?: Query) =>
    client.get(path, searchParams ? { searchParams } : undefined).json<T>(),
  post: <T>(path: string, body?: unknown) =>
    client.post(path, body !== undefined ? { json: body } : undefined).json<T>(),
  put: <T>(path: string, body?: unknown) =>
    client.put(path, body !== undefined ? { json: body } : undefined).json<T>(),
  patch: <T>(path: string, body?: unknown) =>
    client.patch(path, body !== undefined ? { json: body } : undefined).json<T>(),
  delete: <T>(path: string, searchParams?: Query) =>
    client.delete(path, searchParams ? { searchParams } : undefined).json<T>(),
};
```

- 모든 API 호출은 `api.get/post/put/patch/delete`를 통해서만 진행
- 서버 컴포넌트에서 Next.js 캐싱(`next: { revalidate }`)이 필요한 경우에만 native `fetch` 직접 사용 허용
- `useEffect` 안에서 `fetch`를 직접 호출하는 패턴 금지 → 훅으로 분리
- feature 코드에서 `process.env.X` 직접 참조 금지 → `shared/config/` 경유

### 도메인 API 계층 — `shared/apis/`

백엔드 OpenAPI tag 1개당 프론트 파일 1개로 매핑합니다. 파일명은 camelCase 컨벤션 그대로(`userApi.ts`, `galleryApi.ts`, `communityApi.ts`).

- 응답은 모두 `ApiResponse<T> = { success, message, data, errors }` 봉투로 옵니다. `apiUnwrap` 헬퍼(`shared/utils/apiUnwrap.ts`)로 `success === false`를 `ApiError` throw로 변환하고 `data: T`만 반환합니다. `ApiError` 클래스는 백엔드 봉투에 묶인 도메인 타입이라 `shared/apis/apiError.ts`에 두고, `apiUnwrap`은 일반 Promise 변환 유틸이라 `shared/utils/`에 분리되어 있습니다.
- 도메인 함수는 `userUuid`를 **명시 인자**로 받습니다. feature 훅이 `useUserStore`에서 읽어 전달.
- **객체로 묶지 않고 개별 함수로 export**합니다.

#### 함수 네이밍 규칙

`{httpMethod}{ResourcePath}` 형태의 camelCase. `users/`처럼 도메인 prefix가 자명한 경우 생략하고, 의미가 드러나는 segment부터 PascalCase로 이어 붙입니다.

| HTTP | 경로 | 함수명 |
| --- | --- | --- |
| POST | `/users/anonymous` | `postAnonymous` |
| POST | `/users/anonymous/verify` | `postAnonymousVerify` |
| POST | `/users/anonymous/birth-info` | `postAnonymousBirthInfo` |
| PATCH | `/users/anonymous/birth-info` | `patchAnonymousBirthInfo` |
| PATCH | `/users/anonymous/nickname` | `patchAnonymousNickname` |
| GET | `/users/anonymous/profile` | `getAnonymousProfile` |
| GET | `/gallery` (목록) | `getGalleryList` |
| GET | `/gallery/{galleryId}` (단건) | `getGallery` |
| DELETE | `/gallery/{galleryId}` | `deleteGallery` |
| GET | `/community/{communityId}` | `getCommunity` |

원칙:
- 단건/리스트가 같은 GET에서 갈리면 단건은 단수형(`getGallery`), 리스트는 `List` 접미사(`getGalleryList`).
- path parameter를 받는 함수는 첫 인자로 그 id를, 그다음 `userUuid` 등 부가 인자를 받습니다.

```ts
// shared/apis/userApi.ts (예시)
export const postAnonymous = () =>
  apiUnwrap(api.post<ApiResponse<AnonymousUserResponse>>("users/anonymous"));

export const postAnonymousVerify = (userUuid: string) =>
  apiUnwrap(api.post<ApiResponse<AnonymousUserVerifyResponse>>("users/anonymous/verify", { userUuid }));

export const patchAnonymousNickname = (userUuid: string, nickname: string) =>
  apiUnwrap(api.patch<ApiResponse<AnonymousUserNicknameResponse>>("users/anonymous/nickname", { userUuid, nickname }));

export const getAnonymousProfile = (userUuid: string) =>
  apiUnwrap(api.get<ApiResponse<AnonymousUserProfileResponse>>("users/anonymous/profile", { userUuid }));
```

호출 측은 필요한 함수만 직접 import합니다.

```ts
import { postAnonymous, postAnonymousVerify } from "@/shared/apis";
```

### 사용자 식별 부트스트랩

- `userUuid`는 `useUserStore`(Zustand `persist` 미들웨어)에서 단일하게 관리됩니다. 별도의 `localStorage` 동기화 코드를 추가로 작성하지 않습니다.
- 루트 `app/layout.tsx`에 `<UserBootstrap />`(`'use client'`) 1회 마운트 — `useUserBootstrap`이 persist hydration 후 `postAnonymousVerify` 또는 `postAnonymous`를 호출해 store/스토리지를 동기화합니다.
- 페이지/feature가 직접 verify/create를 호출하지 않습니다.

---

## 라이브러리별 사용 규칙

### motion

- DOM 요소 애니메이션에 사용 (`motion.div`, `AnimatePresence` 등)
- R3F `<Canvas>` 내부에서 `motion.*` 사용 금지

### @react-three/rapier

물리 엔진은 캐릭터 이동 제어, 충돌 감지, 디지털 트윈 설비 시뮬레이션에 사용합니다.

#### Physics Provider 배치

`<Physics>`는 각 씬의 `{Scene}Canvas.tsx` 내 `<Canvas>` 바로 하위에 하나만 배치합니다. 씬 컴포넌트나 오브젝트에 중복 선언 금지.

```tsx
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
```

디지털 트윈처럼 중력이 의미 없는 평면 이동 씬은 `gravity={[0, 0, 0]}`으로 설정합니다.

#### RigidBody 타입 선택 기준

| 타입                    | 사용 대상                                                      |
| ----------------------- | -------------------------------------------------------------- |
| `kinematicPosition`     | 캐릭터, 외부 데이터(WebSocket/API)로 위치가 갱신되는 설비·로봇 |
| `fixed`                 | 바닥, 벽, 고정 구조물                                          |
| `dynamic`               | 물리적으로 자유 반응이 필요한 물체 (자유낙하, 밀림 등)         |
| `sensor: true` Collider | 충돌 이벤트만 감지, 물리 반응 없음 (근접 감지 범위)            |

`kinematicPosition`은 이동을 코드가 제어하고 충돌 처리는 물리 엔진이 담당합니다.
`dynamic`을 캐릭터에 쓰면 물리 연산이 이동을 방해하므로 **절대 금지**합니다.

#### 캐릭터 패턴

```tsx
// worlds/_infra/Character.tsx
import { RigidBody, CapsuleCollider } from "@react-three/rapier";

export default function Character() {
  const rb = useRef<RapierRigidBody>(null);
  useCharacterMovement(rb);
  return (
    <RigidBody ref={rb} type="kinematicPosition" colliders={false}>
      <CapsuleCollider args={[0.4, 0.4]} />
      <group>{/* 3D 모델 */}</group>
    </RigidBody>
  );
}
```

이동 훅에서는 `setNextKinematicTranslation()`으로 위치를 갱신합니다.

```ts
// worlds/_infra/hooks/useCharacterMovement.ts
useFrame((_, delta) => {
  if (!rb.current) return;
  const pos = rb.current.translation();
  // 다음 위치 계산 후
  rb.current.setNextKinematicTranslation({ x: nextX, y: pos.y, z: nextZ });
});
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
<RigidBody
  type="fixed"
  sensor
  onIntersectionEnter={onEnter}
  onIntersectionExit={onExit}
>
  <SphereCollider args={[3]} />
</RigidBody>
```

감지 이벤트 처리 로직은 `use{Scene}Interaction.ts`에 위치합니다.

#### KinematicCharacterController와 충돌 처리

캐릭터는 `KinematicCharacterController`의 `computeColliderMovement`로 이동합니다.
이 메서드는 Rapier World에 등록된 **모든 Collider**를 대상으로 충돌을 계산합니다.

| mesh 상태                                                               | 캐릭터 충돌             |
| ----------------------------------------------------------------------- | ----------------------- |
| `RigidBody` + Collider 있음 (`fixed` / `kinematicPosition` / `dynamic`) | 막힘 ✅                 |
| 일반 `<mesh>` (RigidBody 없음)                                          | 통과 ❌                 |
| `sensor: true` Collider                                                 | 통과 — 이벤트만 발생 ❌ |

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
import { RigidBody } from "@react-three/rapier";

<RigidBody type="fixed">
  <mesh rotation={[-Math.PI / 2, 0, 0]}>
    <planeGeometry args={[50, 50]} />
    <meshStandardMaterial color="#6b8f52" />
  </mesh>
</RigidBody>;
```

보이지 않는 경계벽도 `fixed` RigidBody + `CuboidCollider`로 구성합니다.

### Three.js / R3F 패턴

#### `useFrame` 안에서 React `setState` 호출 금지

`useFrame`은 매 프레임(60fps) 실행되므로, 내부에서 `useState` setter를 호출하면 초당 60번 리렌더가 유발됩니다.
애니메이션 값은 `useRef`로 관리하고, Three.js 객체는 ref를 통해 직접 조작합니다.

```tsx
// ❌ 금지 — 60fps 리렌더 유발
const [intensity, setIntensity] = useState(0);
useFrame(({ clock }) => {
  setIntensity(Math.sin(clock.elapsedTime * 5));
});

// ✅ 올바른 방식 — Three.js 객체를 ref로 직접 조작
const lightRef = useRef<PointLight>(null);
useFrame(({ clock }) => {
  if (!lightRef.current) return;
  lightRef.current.intensity = Math.sin(clock.elapsedTime * 5);
});
```

#### R3F 씬 내 `setInterval`/`setTimeout` 애니메이션 폴링 금지

R3F 씬 안에서 애니메이션 완료를 `setInterval`로 폴링하면 R3F 루프와 독립된 타이머가 생겨 동기화가 어렵습니다.
`useFrame` 내부 조건 검사로 대체합니다.

```ts
// ❌ 금지
const check = setInterval(() => {
  if (Math.abs(mesh.position.y - target) < 0.01) {
    clearInterval(check);
    onComplete();
  }
}, 50);

// ✅ 올바른 방식 — useFrame 내 조건 검사
const checking = useRef(false);
// (애니메이션 시작 시) checking.current = true
useFrame(() => {
  if (!checking.current) return;
  if (Math.abs(mesh.position.y - target) < 0.01) {
    checking.current = false;
    onComplete();
  }
});
```

#### `TextureLoader` 사용 시 `LoadingManager` 격리

`TextureLoader`를 기본 `DefaultLoadingManager`에 연결하면 drei의 `useProgress`가 간섭받아 로딩 진행도가 오염됩니다.
격리된 매니저는 **씬 공유 모듈 파일 상단에 싱글턴으로 선언**하고, 같은 씬 내 여러 파일에서 import해 사용합니다.

```ts
// worlds/{scene}/textureLoader.ts — 씬 단위 공유 싱글턴
import { LoadingManager } from "three";
export const isolatedManager = new LoadingManager();

// ❌ 금지 — 각 파일에 중복 선언
const isolatedManager = new LoadingManager(); // DrawingDoorMesh.tsx
const isolatedManager = new LoadingManager(); // useOnboardingPrinterInteraction.ts
```

#### `useEffect` 내 `setState` 동기 호출 금지 (React Compiler 규칙)

React Compiler(`babel-plugin-react-compiler`) 활성화 상태에서 `useEffect` 본문 안에서 `setState`를 동기 호출하면 컴파일 오류가 발생합니다.
상태 초기화가 필요한 경우 async IIFE 안에서 처리합니다.

```ts
// ❌ 금지 — 동기 setState
useEffect(() => {
  if (!url) {
    setTexture(null); // 오류: synchronous setState in effect
    return;
  }
  // ...
}, [url]);

// ✅ 올바른 방식 — async IIFE 안에서 처리
useEffect(() => {
  let cancelled = false;
  (async () => {
    if (!url) {
      if (!cancelled) setTexture(null);
      return;
    }
    // ...
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

CSS 레이어 로딩 순서 (`shared/styles/index.css`):
1. Primitive tokens
2. Semantic tokens
3. Tailwind layers (theme → utilities → base)

- `app/layout.tsx`에서 `@/shared/styles/index.css` import
- 컴포넌트 스타일은 Tailwind utility classes로 처리 (별도 CSS 파일 금지)

---

## 디자인 시스템

### 토큰 계층 구조

```
Primitive Tokens → Semantic Tokens
(원시 값)          (역할 기반 매핑)
```

- **Primitive** (`shared/styles/tokens/primitive/`): 원시 값 (HSL 채널, px 수치). 컴포넌트에서 직접 참조 금지.
- **Semantic** (`shared/styles/tokens/semantic/`): 역할 기반 매핑 (`primary`, `surface`, `fg` 등). 컴포넌트에서 이것만 참조.

### 토큰 사용 규칙

**컴포넌트에서는 반드시 Semantic 토큰을 사용하세요. Primitive 토큰 직접 사용 금지.**

```tsx
// ✅ 올바른 사용 — Semantic 토큰
className="bg-surface-default text-fg-primary border-border-default"

// ❌ 금지 — Primitive 직접 참조
className="bg-cream-50 text-brown-720"
```

### 색상 토큰

| 토큰                   | 용도                            |
| ---------------------- | ------------------------------- |
| `bg-primary-1`         | CTA 버튼 배경, 주요 강조        |
| `bg-primary-5`         | 활성 탭 배경 (연한 강조색)      |
| `bg-surface-default`   | 기본 카드/화면 배경             |
| `bg-surface-subtle`    | 구분선 위 배경                  |
| `text-fg-primary`      | 주요 본문 텍스트                |
| `text-fg-secondary`    | 보조 텍스트                     |
| `text-fg-disabled`     | 비활성 텍스트                   |
| `text-fg-inverse`      | 어두운 배경 위 텍스트           |
| `border-border-default`| 기본 테두리                     |
| `text-primary-2`       | 링크, 활성 아이콘 색            |

### 타이포그래피 유틸리티 클래스

폰트 관련 속성(font-family, size, line-height, weight)을 묶은 유틸리티 클래스를 사용합니다.
**raw Tailwind font 클래스 직접 조합 금지** (`text-2xl font-bold` 등).

```
h1-b (32px/700)   h2-b (24px/700)   h3-b (20px/700)   h4-b (16px/700)
body-l-b / body-l-m / body-l-r  (16px, weight: 700/500/400)
body-b  / body-m  / body-r      (14px, weight: 700/500/400)
caption-b / caption-m / caption-r (12px, weight: 700/500/400)
```

```tsx
// ✅ 올바른 사용
className="h2-b text-fg-primary"
className="body-r text-fg-secondary"

// ❌ 금지 — raw Tailwind font 클래스 직접 조합
className="text-2xl font-bold leading-tight"
```

### 간격 (Spacing)

CSS 변수 기반 스케일은 Tailwind 스케일과 1:1 대응됩니다.

```
--space-1: 4px   → p-1, gap-1
--space-2: 8px   → p-2, gap-2
--space-4: 16px  → p-4, gap-4  (기본 카드 내부)
--space-6: 24px  → p-6         (섹션 내부)
```

### Border Radius

```
rounded-[var(--radius-sm)]  : 4px  — 배지, 태그
rounded-[var(--radius-md)]  : 8px  — 버튼, 입력
rounded-[var(--radius-lg)]  : 12px — 카드
rounded-[var(--radius-xl)]  : 16px — 모달, 바텀시트
rounded-full                : 원형 — 아바타
```

### Shadow

```
shadow-sm      : 기본 카드
shadow-md      : 드롭다운
shadow-lg      : 모달
shadow-soft-lg : 네비게이션 바 등 부드러운 그림자
```

### Z-Index

```
z-[var(--z-dropdown)] : 200  — 드롭다운
z-[var(--z-sticky)]   : 300  — 고정 헤더
z-[var(--z-overlay)]  : 400  — 딤 배경
z-[var(--z-modal)]    : 500  — 모달
z-[var(--z-toast)]    : 600  — 토스트
```

### cn() 유틸리티

`shared/libs/cn.ts`에 `cn()` 함수를 두고 모든 조건부 클래스 병합에 사용합니다.

```ts
// shared/libs/cn.ts
import { clsx, type ClassValue } from 'clsx'
import { twMerge } from 'tailwind-merge'

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}
```

```tsx
// ✅ 올바른 사용
import { cn } from '@/shared/libs'

<div className={cn("body-r text-fg-primary", isActive && "text-primary-2")} />

// ❌ 금지 — 문자열 연결로 클래스 병합
<div className={`body-r text-fg-primary ${isActive ? "text-primary-2" : ""}`} />
```

### CVA 패턴 (컴포넌트 변형)

변형(variant)이 있는 컴포넌트는 `class-variance-authority`를 사용합니다.

```tsx
import { cva, type VariantProps } from 'class-variance-authority'
import { cn } from '@/shared/libs'

const buttonVariants = cva(
  'body-b rounded-[var(--radius-md)] transition-colors',
  {
    variants: {
      variant: {
        primary: 'bg-primary-1 text-fg-inverse',
        secondary: 'bg-surface-subtle text-fg-primary border border-border-default',
      },
      size: {
        sm: 'px-3 py-1.5',
        md: 'px-4 py-2',
      },
    },
    defaultVariants: { variant: 'primary', size: 'md' },
  }
)

interface ButtonProps
  extends React.ButtonHTMLAttributes<HTMLButtonElement>,
    VariantProps<typeof buttonVariants> {}

export function Button({ className, variant, size, ...props }: ButtonProps) {
  return <button className={cn(buttonVariants({ variant, size }), className)} {...props} />
}
```

### 컴포넌트 폴더 구조

`shared/components/` 하위 컴포넌트는 컴포넌트마다 폴더를 사용합니다.

```
shared/components/
├── index.ts
└── Button/
    ├── Button.tsx
    ├── Button.types.ts   ← 복잡한 Props인 경우
    └── index.ts
```

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
- feature 코드에서 `process.env.X` 직접 참조 금지 → `shared/config/` 경유

```ts
// shared/config/runtime.ts
export const runtime = {
  apiUrl: process.env.NEXT_PUBLIC_API_URL!,
  isDev: process.env.NODE_ENV === "development",
};

// ✅ feature 코드에서는 config를 통해 접근
import { runtime } from "@/shared/config";
// ❌ 직접 참조 금지
process.env.NEXT_PUBLIC_API_URL;
```

---

## 자원 폴더화 규칙

단위 파일이 **1개면 평면 파일**로 두고, **2번째 파일이 추가되는 시점에 즉시 폴더 + `index.ts` 배럴을 만듭니다** (같은 PR, merge 차단 조건).

```
# 시작 — 훅 1개: 평면이 정답
features/relay-drawing/
└── useRelayDrawing.ts

# 2번째 훅 추가 — 즉시 폴더화 (같은 PR에서)
features/relay-drawing/
└── hooks/
    ├── index.ts               ← 배럴 (필수!)
    ├── useRelayDrawing.ts     ← 기존 파일 이동
    └── useRelayDrawingHistory.ts
```

자원 타입: 훅(`use*.ts`) · 상수 · 유틸 · 타입 · 컴포넌트
각 타입은 독립적으로 진화합니다 — 훅이 폴더화되어도 상수가 1개면 상수는 평면 유지.

**PR merge 차단 조건:**
- 같은 자원 타입 파일 2개 이상 + 폴더화 없음
- 폴더가 있으나 `index.ts` 배럴 없음

---

## 배럴 export

모든 폴더는 `index.ts`를 통해 외부에 단일 진입점을 제공합니다.
내부 파일 경로 직접 참조 금지 (`_infra/` 및 `use*Interaction.ts`의 feature store import는 예외).

```ts
// ✅ 올바른 import
import { useInteractiveObject } from "@/features/interaction-sheet";

// ❌ 금지 — 내부 경로 직접 참조
import { useInteractiveObject } from "@/features/interaction-sheet/useInteractiveObject";
```

---

## 핵심 금지사항 (반드시 준수)

- `*.tsx`에 API 호출, 데이터 변환, 게임 로직 직접 작성 금지 → 훅/스토어로 분리
- `useEffect` 안에서 `fetch` 직접 호출 금지 → 훅으로 분리
- `{Scene}Canvas.tsx` 외에서 `<Canvas>` 선언 금지
- `{Scene}Canvas.tsx` 외에서 `<Physics>` 선언 금지
- Server Component에서 `dynamic + ssr: false` 사용 금지 → `{Scene}Loader.tsx` Client Component 래퍼로 분리
- 일반 서비스 페이지를 `app/(service)/` 없이 `app/` 직하에 배치 금지
- `_infra/`에 재사용 가능한 메시 추가 금지 → `_shared/mesh/`로
- `_shared/mesh/`에 단일 주체 컴포넌트 추가 금지 → `_infra/`로
- `index.ts` 배럴 우회 import 금지
- `features/` 간 직접 import 금지
- `worlds/`↔`features/` 컴포넌트/훅 직접 import 금지
- `features/admin/` ↔ 일반 `features/` 간 import 금지 (양방향)
- 단일 문자·무의미한 축약어 변수명 금지 (`p`, `u`, `BH` 등)
- `useFrame` 안에 무거운 연산 배치 금지
- `useFrame` 안에서 React `setState` 호출 금지 → `useRef` + Three.js 객체 직접 조작으로 대체
- R3F 씬 내 애니메이션 완료 감지를 `setInterval`/`setTimeout`으로 폴링 금지 → `useFrame` 조건 검사로 대체
- `TextureLoader` 사용 시 파일마다 `new LoadingManager()` 중복 선언 금지 → 씬 공유 `textureLoader.ts` 싱글턴에서 import
- `useEffect` 본문에서 `setState` 동기 호출 금지 (React Compiler 오류) → async IIFE 안에서 처리
- R3F `<Canvas>` 내부에서 `motion.*` 사용 금지
- Rapier 도입 후 `groupRef.current.position` 직접 수정 금지 → `setNextKinematicTranslation()` 사용
- 캐릭터에 `dynamic` RigidBody 사용 금지 → `kinematicPosition` 사용
- Rapier 사용 시 경계를 `MathUtils.clamp`로 처리 금지 → `fixed` RigidBody + Collider로 처리
- 불필요한 `'use client'` 선언 금지 (판단 기준은 위 참고)
- `<a>` 태그 사용 금지 → `<Link>`
- `<img>` 태그 사용 금지 → `<Image>`
- `page.tsx`에 공통 UI 반복 작성 금지 → `layout.tsx`로
- feature 코드에서 `process.env.X` 직접 참조 금지 → `shared/config/` 경유
- `NEXT_PUBLIC_` 없는 환경변수를 클라이언트 컴포넌트에서 참조 금지
- 자원 2개째 추가 시 폴더화 + `index.ts` 배럴 없이 PR merge 금지
- `shared/assets/` 대신 `public/`에 컴포넌트 import용 자산 배치 금지
- Semantic 토큰 대신 Primitive 토큰 직접 참조 금지 (`bg-cream-50`, `text-brown-720` 등)
- 타이포그래피에 raw Tailwind font 클래스 직접 조합 금지 → `h1-b`, `body-r` 등 유틸리티 클래스 사용
- 조건부 클래스 병합 시 `cn()` 없이 문자열 연결 금지 → `cn()` 유틸리티 사용
