# Frontend

## 기술 스택

| 분류 | 기술 |
|------|------|
| 프레임워크 | Next.js 16 (App Router) |
| 언어 | TypeScript |
| 스타일 | Tailwind CSS v4 |
| 3D | @react-three/fiber · @react-three/drei · @react-three/rapier |
| 상태 관리 | Zustand |
| HTTP 클라이언트 | ky |
| UI 프리미티브 | @base-ui/react |
| 2D 캔버스 | konva · react-konva |
| 아이콘 | lucide-react |
| 애니메이션 | motion |
| 컴포넌트 변형 | class-variance-authority (CVA) |
| 클래스 병합 | clsx · tailwind-merge |
| 폰트 | Pretendard Variable |
| 패키지 매니저 | pnpm |
| React Compiler | babel-plugin-react-compiler 활성화 |

---

## 에이전트 지시 문서 구조

AI 에이전트(Claude Code, Codex 등)가 이 프로젝트에서 작업할 때 읽는 지시 파일 체계입니다.

### 왜 이 구조인가

단일 지시 파일이 비대해지면 에이전트의 컨텍스트 윈도우가 꽉 차 규칙을 놓치는 문제가 발생합니다. 이를 해결하기 위해 태스크 유형별로 파일을 분리하고, 에이전트가 해당 태스크에 필요한 파일만 읽도록 라우팅합니다.

### 진입점 파일

| 파일 | 대상 에이전트 | 내용 |
|------|--------------|------|
| [`CLAUDE.md`](CLAUDE.md) | Claude Code | 라우팅 헤더 + 스택 + 설치 명령 (35줄) |
| [`AGENTS.md`](AGENTS.md) | Codex 등 기타 에이전트 | CLAUDE.md와 동일한 내용 (항상 동기화 유지) |

두 파일 모두 인라인 규칙 없이 **"어떤 태스크에 어떤 파일을 읽어라"** 라는 라우팅만 담고 있습니다.

### 태스크 → 파일 라우팅

에이전트는 매 태스크마다 아래 네 파일을 필수로 읽고, 태스크 유형에 맞는 파일을 추가로 읽습니다.

**필수 (모든 태스크):**
- [`docs/rules.md`](docs/rules.md) — 카테고리별 금지 규칙
- [`docs/structure.md`](docs/structure.md) — 레이어 개념, 계층 구조, 폴더 트리
- [`docs/naming.md`](docs/naming.md) — 폴더·파일 네이밍, 배치 빠른 참조, 배럴 규칙
- [`docs/checklist.md`](docs/checklist.md) — 태스크 완료 전 검증 체크리스트

**태스크별 추가 읽기:**

| 태스크 유형 | 읽을 파일 |
|------------|----------|
| 컴포넌트 · 훅 작업 | [`docs/component.md`](docs/component.md) |
| API 연동 · 상태 관리 | [`docs/api.md`](docs/api.md) |
| UI · 스타일링 | [`docs/design.md`](docs/design.md) |
| 3D 씬 · R3F · Rapier | [`docs/r3f.md`](docs/r3f.md) |
| Konva · react-konva | [`docs/konva.md`](docs/konva.md) |
| DOM 애니메이션 · motion · CSS 키프레임 | [`docs/animation.md`](docs/animation.md) |
| 커밋 메시지 추천 | [`docs/commit.md`](docs/commit.md) |
| GitLab MR 제목 · 내용 추천 | [`docs/merge-request.md`](docs/merge-request.md) |
| 로그 수집 · 이벤트 트래킹 | [`docs/logging.md`](docs/logging.md) + [`docs/logging-events.md`](docs/logging-events.md) |

### docs/ 파일별 담당 영역

```
docs/
├── rules.md            ← 카테고리별 금지 규칙 전체 목록
├── checklist.md        ← 태스크 완료 전 검증 체크리스트
├── structure.md        ← 레이어 개념, 계층 구조(Page→View→Section→Component), 폴더 트리
├── naming.md           ← 폴더·파일 네이밍, 접미사 컨벤션, 배치 빠른 참조, 배럴·폴더화 규칙
├── component.md        ← UI/로직 분리, 컴포넌트 분리 기준, 훅 추출 기준
├── api.md              ← ky 클라이언트, 인증 흐름, 도메인 API 네이밍, 환경 변수
├── design.md           ← 디자인 시스템, 토큰 계층, 타이포그래피, cn(), CVA
├── r3f.md              ← Rapier 물리, R3F 패턴 (Three.js, useFrame, LoadingManager)
├── konva.md            ← Konva Stage 구성 규칙, SSR 경계
├── animation.md        ← motion 사용 기준, CSS @keyframes 허용 조건, 커스텀 폰트
├── commit.md           ← 커밋 메시지 형식, type 정의, 커밋 분리 기준
├── merge-request.md    ← GitLab MR 제목 형식, 내용 템플릿, 섹션별 작성 규칙
├── logging.md          ← 이벤트 스키마, 전송 정책, 샘플링, PII 규칙, 구현 체크리스트
└── logging-events.md   ← 이벤트 카탈로그 (유입/세션/funnel/이탈/UI참여/성능오류)
```

### 로그 수집 개요

프론트엔드 로그는 "사용자가 어디서 들어와서, 어떤 화면을 거쳐, 어느 단계에서 전환하거나 이탈했는지"를 추적합니다.

**수집 대상:** 유입(referrer, UTM), 세션/페이지 전환, funnel 전환 및 이탈, UI 참여, JS 오류, 네트워크 실패, Web Vitals

**수집하지 않음:** API 성공/실패, HTTP status, API latency, 응답/요청 본문 (백엔드 책임)

로그는 `POST /api/logs/client` 엔드포인트로 배치 전송합니다. 버퍼 50개 또는 5초마다 flush하며, 페이지 종료 시 `navigator.sendBeacon`으로 잔여 이벤트를 전송합니다. 오류 이벤트는 즉시 flush합니다.

funnel 흐름 추적에는 `flow_id`(funnel 단위 UUID)를, 단일 fetch 추적에는 `trace_id`(요청 단위 UUID)를 사용합니다. 두 값 모두 fetch 헤더(`X-Flow-Id`, `X-Trace-Id`)로 백엔드에 전달되어 프론트 이벤트와 백엔드 로그를 join할 수 있습니다.

구현 위치: `shared/libs/logger.ts`

### 지시 파일 수정 시 주의사항

- `docs/*.md` 수정 시 [`docs/checklist.md`](docs/checklist.md)의 검증 항목과 어긋나지 않는지 확인합니다.
- `CLAUDE.md`와 `AGENTS.md`는 항상 동일한 내용을 유지합니다.
- 지시 파일 본문은 **영어**로 작성합니다 (에이전트 이해도를 위해).
- **컨벤션이 특정 케이스를 커버하지 못할 경우** — 해당 feature 안에서 로컬 예외를 만들지 않습니다. 팀 논의 후 지시 파일을 전역으로 수정해 모든 feature에 동일하게 적용합니다.

---

## 폴더 구조

### 레이어 개념

| 레이어 | 정의 | 폴더 |
|--------|------|------|
| App | 라우팅 진입점 · 메타데이터만 담당 | `app/` |
| World | 3D 씬 — 씬당 한 폴더 | `worlds/` |
| View | 런타임 조건에 따라 Page 안에서 교체되는 전체화면 단위 | `features/{name}/views/` |
| Feature | 비즈니스 기능 단위 — 2개 이상 뷰에서 쓰일 때 승격 | `features/` |
| Shared | 레이어 무관 공용 자원 | `shared/` |

**의존 방향:** `app → worlds · features → shared` (단방향)

```
src/
├── app/
│   ├── layout.tsx
│   ├── (service)/             # URL에 미노출 — 일반 서비스 라우트 그룹
│   │   ├── page.tsx           # /
│   │   ├── hub/page.tsx       # /hub
│   │   ├── relay-drawing/page.tsx
│   │   ├── infinite-canvas/page.tsx
│   │   ├── flipbook/page.tsx
│   │   └── share/[id]/page.tsx
│   └── admin/                 # /admin/* — 백오피스 (URL 노출)
│       ├── layout.tsx         # AdminAuthGuard + 사이드바
│       └── {section}/page.tsx
│
├── worlds/                    # 3D 씬 코드 — 씬당 한 폴더
│   ├── landing/
│   │   ├── LandingLoader.tsx  # 'use client' + dynamic(ssr:false)
│   │   ├── LandingCanvas.tsx  # <Canvas> + <Physics>
│   │   ├── LandingScene.tsx   # 씬 루트
│   │   └── objects/
│   ├── hub/
│   │   └── ...
│   ├── _infra/                # 씬당 1개인 단일 주체 (Lighting, Character)
│   └── _shared/mesh/          # 씬 안 다중 배치 가능한 재사용 메시
│
├── features/                  # 도메인 기능 단위
│   ├── relay-drawing/
│   ├── fortune/
│   ├── share/
│   └── admin/
│
└── shared/
    ├── apis/                  # 도메인별 API 함수 (개별 export)
    ├── assets/                # 번들러 관리 정적 자산 (svg, glb, mp3)
    ├── components/            # 공용 UI 컴포넌트 (컴포넌트마다 폴더)
    ├── config/                # 환경 설정 — process.env는 여기서만
    ├── constants/             # 환경 무관 전역 상수
    ├── hooks/                 # 재사용 React 훅
    ├── layouts/               # 공유 레이아웃 컴포넌트
    ├── libs/                  # 외부 라이브러리 래퍼 (apiClient, cn)
    ├── stores/                # 전역 Zustand store
    ├── styles/                # CSS 토큰 시스템 진입점
    ├── types/                 # 공유 TypeScript 타입
    └── utils/                 # 순수 유틸리티 함수
```

### 3D 씬 4단계 진입점 패턴

```
[1] app/(service)/page.tsx         Server Component — 라우팅 + OG 메타데이터
        ↓ import
[2] worlds/{scene}/{Scene}Loader   'use client' + dynamic(ssr:false) 래퍼
        ↓ dynamic import
[3] worlds/{scene}/{Scene}Canvas   <Canvas> + <Physics> 소유
        ↓
[4] worlds/{scene}/{Scene}Scene    씬 루트 — 오브젝트 조립
```

> Next.js 16에서 `dynamic + ssr:false`는 Server Component에서 직접 사용 불가 → Loader 레이어가 반드시 필요합니다.

---

## 주요 컨벤션 요약

> 전체 규칙은 [`docs/rules.md`](docs/rules.md)를 참고하세요.

### 파일 / 폴더 네이밍

| 종류 | 형식 | 예시 |
|------|------|------|
| React 컴포넌트 | `PascalCase.tsx` | `LandingScene.tsx`, `FortuneModal.tsx` |
| 훅 | `use{Name}.ts` | `useCharacterControls.ts` |
| 스토어 | `{name}Store.ts` | `fortuneStore.ts` |
| 유틸 / 상수 | `camelCase.ts` / `constants.ts` | `apiUnwrap.ts`, `constants.ts` |
| 배럴 | `index.ts` (고정) | `index.ts` |
| 씬 도메인 폴더 | `lowercase` | `landing/`, `hub/` |
| feature 폴더 | `kebab-case` | `relay-drawing/` |
| 인프라/공유 | `_prefix` | `_infra/`, `_shared/` |

### 파일 접미사 컨벤션

| 접미사 | 의미 |
|--------|------|
| `{Scene}Loader.tsx` | 3D 라우트 진입점 — `'use client'` + `dynamic(ssr:false)` |
| `{Scene}Canvas.tsx` | `<Canvas>` + `<Physics>` 소유 |
| `{Scene}Scene.tsx` | 씬 루트 — 오브젝트 조립 |
| `*Mesh.tsx` | 3D 지오메트리 단위 |
| `*Page.tsx` | 2D 라우트 진입점 |
| `*Modal.tsx` | DOM 오버레이 모달 |
| `*Stage.tsx` | Konva `<Stage>` 소유 2D 캔버스 |
| `*Visual.tsx` | 독립 `<Canvas>`를 소유하는 3D 컴포넌트 |
| `use*Interaction.ts` | 씬 상호작용 훅 (거리 감지 + 키 이벤트) |

### 자원 폴더화 규칙

> 같은 자원 타입 파일이 2개째 추가될 때 즉시 폴더화 + `index.ts` 배럴을 만든다.

```
# 훅 1개 → 평면 파일
features/relay-drawing/useRelayDrawing.ts

# 2번째 훅 추가 시 → 즉시 폴더화 (같은 PR에서)
features/relay-drawing/hooks/
├── index.ts
├── useRelayDrawing.ts
└── useRelayDrawingHistory.ts
```

이 규칙은 PR merge 차단 사유입니다.

### 배럴 import 기준

```ts
// 외부 소비자 → feature public 배럴
import { useRelayDrawing } from "@/features/relay-drawing";

// feature 내부 → 리소스 폴더 배럴 또는 평면 파일
import { useRelayDrawing } from "../hooks";      // 리소스 폴더 배럴
import { RELAY_STAGE_SIZE } from "../constants"; // 평면 파일

// 금지 — 배럴 우회 직접 참조
import { useRelayDrawing } from "@/features/relay-drawing/hooks/useRelayDrawing";
```

### 인증 모델

이 프로젝트는 두 가지 인증 흐름을 사용합니다:

| 흐름 | ky 클라이언트 | 식별자 | 대상 도메인 |
|------|--------------|--------|------------|
| 일반 사용자 | `api` (apiClient.ts) | `Anonymous-User-UUID` 헤더 (자동 주입) | user, gallery, community 등 |
| 백오피스 | `adminApi` (adminApiClient.ts) | `Authorization: Bearer` (자동 주입 + 401 재발급) | admins, auth/logout |

도메인 API 함수는 **식별자 인자를 받지 않습니다** — 인터셉터가 store에서 읽어 주입합니다.

> 상세 내용: [`docs/api.md`](docs/api.md)

### config/ vs constants/

| 위치 | 기준 | 예시 |
|------|------|------|
| `shared/config/` | 환경(dev/prod)마다 값이 다를 수 있음 | `apiUrl`, `pollingIntervalMs` |
| `shared/constants/` | 환경 무관, 항상 같은 값 | `CHARACTER_HEIGHT`, `WALK_SPEED` |

feature 코드에서 `process.env.X` 직접 참조 금지 → 반드시 `shared/config/` 경유.

---

## URL 매핑

| URL | 진입점 컴포넌트 | 도메인 |
|-----|----------------|--------|
| `/` | `LandingLoader` | 3D |
| `/hub` | `HubLoader` + 모달들 | 3D + DOM |
| `/relay-drawing` | `RelayDrawingPage` | 2D (Konva) |
| `/infinite-canvas` | `InfiniteCanvasPage` | 2D (Konva) |
| `/flipbook` | `FlipbookPage` | 2D |
| `/share/[id]` | `SharePage` | 일반 |
| `/admin/*` | `features/admin/{section}Page` | 백오피스 |
