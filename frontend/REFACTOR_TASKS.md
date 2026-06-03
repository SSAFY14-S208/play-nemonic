# 폴더구조 개편 작업 과제

> Phase 1은 완료됨. Phase 2~10은 서로 독립된 피처를 수정하므로 **병렬 작업 가능**.  
> 각 Phase는 **별도 MR**로 제출. Phase 1 완료 브랜치를 base로 삼아 각자 브랜치를 딴다.

---

## 규칙 요약 (작업 전 필독)

`docs/structure.md`, `docs/naming.md`를 반드시 읽고 시작한다.

**핵심 변경 사항**:
- `*View` 컴포넌트 → `features/{name}/views/{ViewName}/index.tsx`
- View 내 독립 UI 블록 → `views/{ViewName}/sections/{SectionName}/index.tsx`
- 특정 View에서만 쓰이는 훅 → `views/{ViewName}/hooks/use*.ts`
- 여러 View에서 공유되는 feature-coupled 컴포넌트 → `components/` 유지
- 모달류 (`*Modal.tsx`) → feature root 또는 `components/` 유지

**분류 기준**:
```
"전체화면 단위를 조건부로 스왑하는가?" → views/
"View 내의 독립적인 UI 블록인가?"     → sections/
"특정 View에서만 호출되는 훅인가?"    → views/{View}/hooks/
"여러 View에서 공유되는 훅인가?"      → feature-level hooks/ 유지
```

**검증 체크리스트** (각 MR마다):
- [ ] `pnpm build` — 빌드 에러 0개
- [ ] `pnpm type-check` — 타입 에러 0개
- [ ] 해당 피처 화면 브라우저에서 golden path 확인
- [ ] `grep -r "\.module\.css" src/` → 결과 없음

---

## Phase 2 — `share` feature

**담당자**: \_\_\_\_\_\_\_\_  
**예상 시간**: 0.5일  
**파일 경로**: `src/features/share/`

### 작업 내용

`share` 피처는 `ShareSheet.tsx`(Modal)와 `shareStore.ts`만 있다.  
View가 필요한 "전체화면 전환 단위"가 없으므로 **구조 변경 불필요**.  
규칙 준수 여부 확인 후 완료 처리.

**확인 항목**:
- [ ] `index.ts` 배럴이 올바르게 외부 소비 경로를 노출하는지 확인
- [ ] `ShareSheet.tsx`가 feature root에 있고 `*Modal.tsx` 규칙과 일치하는지 확인

---

## Phase 3 — `hub` feature

**담당자**: \_\_\_\_\_\_\_\_  
**예상 시간**: 0.5일  
**파일 경로**: `src/features/hub/`

> Phase 1에서 `.module.css` → `hub.css` 변환 완료. CSS 변환 후 구조 검토.

### 작업 내용

`HubOnboardingTour`, `HubOverlay`, `MonitorGameInfoCard`는 3D 허브 위에 렌더되는 2D 오버레이.  
"전체화면을 조건부로 스왑하는 View"가 아니라 **feature-coupled components**로 분류 → 현재 위치 유지.

**확인 항목**:
- [ ] `index.ts` 배럴이 3개 컴포넌트를 올바르게 재export하는지 확인
- [ ] `useHubBgm.ts`가 feature-level 훅으로 현 위치가 적절한지 확인

---

## Phase 4 — `fortune` feature

**담당자**: \_\_\_\_\_\_\_\_  
**예상 시간**: 1일  
**파일 경로**: `src/features/fortune/`

### View 이동

| 현재 위치 | 이동 대상 | 비고 |
|-----------|-----------|------|
| `components/FortuneLoadingView.tsx` | `views/FortuneLoadingView/index.tsx` | `*View` 접미사 |
| `components/FortuneErrorView.tsx` | `views/FortuneErrorView/index.tsx` | `*View` 접미사 |

### Section 이동 (View 내 독립 UI 블록)

| 현재 위치 | 이동 대상 | 소속 View |
|-----------|-----------|-----------|
| `components/FortuneDrawPanel.tsx` | `views/FortuneMainView/sections/FortuneDrawPanel/index.tsx` | 메인 화면 |
| `components/FortuneDialoguePanel.tsx` | `views/FortuneMainView/sections/FortuneDialoguePanel/index.tsx` | 메인 화면 |
| `components/FortuneBirthForm.tsx` | `views/FortuneBirthView/sections/FortuneBirthForm/index.tsx` | 생년월일 입력 |
| `components/FortuneBirthOptionButton.tsx` | `views/FortuneBirthView/sections/FortuneBirthOptionButton/index.tsx` | 생년월일 입력 |
| `components/FortuneEntrySpotlightCover.tsx` | `views/FortuneEntryView/index.tsx` 또는 section | 엔트리 화면 |
| `components/FortuneResultCard.tsx` | `views/FortuneResultView/sections/FortuneResultCard/index.tsx` | 결과 화면 |
| `components/FortuneFloatingPanel.tsx` | `views/FortuneMainView/sections/FortuneFloatingPanel/index.tsx` | 메인 화면 |

### Components 유지 (여러 View에서 공유)

`FortuneBackToggle`, `FortuneBgmToggle`, `FortuneMagicBackdrop`, `FortuneLimitNotice`, `FortunePrintStatus`

### 훅 분류

- Feature-level 유지: `useFortuneActions`, `useFortuneSessionHydration`, `useFortuneAudio`, `useFortuneBgm`, `useFortuneExternalShare`
- View 전용 검토: `useFortuneTypewriterText`, `useFortunePrinterMotion`, `useFortuneReducedMotion` → 어느 View에서만 쓰이면 해당 View의 `hooks/`로 이동

---

## Phase 5 — `community-canvas` feature

**담당자**: \_\_\_\_\_\_\_\_  
**예상 시간**: 1일  
**파일 경로**: `src/features/community-canvas/`

### View 이동

| 현재 위치 | 이동 대상 |
|-----------|-----------|
| `components/CommunityWall.tsx` | `views/CommunityMainView/index.tsx` |

### Section 이동

| 현재 위치 | 이동 대상 |
|-----------|-----------|
| `components/CommunityMemoCard.tsx` | `views/CommunityMainView/sections/CommunityMemoCard/index.tsx` |
| `components/CommunityGalleryPicker.tsx` | `views/CommunityMainView/sections/CommunityGalleryPicker/index.tsx` |

### Components 유지 (모달류 — feature root 또는 components/)

`CommunityComposerModal`, `CommunityNicknameModal`, `CommunityMemoDetailModal`, `CommunityReportModal`, `CommunityMemoPrintRevealOverlay`

### 훅 분류

- `useCommunityCanvas` → CommunityMainView 전용이면 `views/CommunityMainView/hooks/`로 이동
- `useCommunityNickname`, `useCommunityComposer` → 해당 모달 전용이면 feature-level 유지 (모달은 별도 View 아님)

---

## Phase 6 — `phone` feature

**담당자**: \_\_\_\_\_\_\_\_  
**예상 시간**: 1일  
**파일 경로**: `src/features/phone/`

### Screen → View 변환

| 현재 위치 | 이동 대상 |
|-----------|-----------|
| `components/PhoneHomeScreen.tsx` | `views/PhoneHomeView/index.tsx` |
| `components/PhoneDrawingScreen.tsx` | `views/PhoneDrawingView/index.tsx` |
| `components/PhoneGalleryScreen.tsx` | `views/PhoneGalleryView/index.tsx` |
| `components/PhoneInquiryScreen.tsx` | `views/PhoneInquiryView/index.tsx` |

### drawing-screen/ 하위 파일 정리

`components/drawing-screen/` 하위 파일들을 `views/PhoneDrawingView/` 안으로 배치:
- 뷰 전용 훅 → `views/PhoneDrawingView/hooks/`
- UI 서브컴포넌트 → `views/PhoneDrawingView/sections/` 또는 컴포넌트로 평가

### Components 유지

`PhoneStatusBar`, `PhoneToast`, `PhoneFrame`, `PhonePrintFrame`, `PhoneCloseButton`, `PhoneMobileCloseButton`

### 훅 분류

- View 전용: `usePhoneDrawing` → `views/PhoneDrawingView/hooks/`, `usePhoneGallery` → `views/PhoneGalleryView/hooks/`, 등
- Feature-level 유지: `usePhoneToast`, `usePhoneClock`, `useNemonicImagePrint`

---

## Phase 7 — `flipbook` feature

**담당자**: \_\_\_\_\_\_\_\_  
**예상 시간**: 1.5일  
**파일 경로**: `src/features/flipbook/`

### View 이동

| 현재 위치 | 이동 대상 |
|-----------|-----------|
| `components/FlipbookEntranceView.tsx` | `views/FlipbookEntranceView/index.tsx` |
| `components/FlipbookLobbyView.tsx` | `views/FlipbookLobbyView/index.tsx` |
| `components/FlipbookDrawingView.tsx` | `views/FlipbookDrawingView/index.tsx` |
| `components/FlipbookBoothView.tsx` | `views/FlipbookBoothView/index.tsx` |
| `components/FlipbookResultView.tsx` | `views/FlipbookResultView/index.tsx` |

### View-scoped 훅 이동

| 현재 위치 | 이동 대상 |
|-----------|-----------|
| `hooks/useFlipbookEntranceIntro.ts` | `views/FlipbookEntranceView/hooks/` |
| `hooks/useFlipbookEntranceBgm.ts` | `views/FlipbookEntranceView/hooks/` |
| `hooks/useFlipbookEntrancePreload.ts` | `views/FlipbookEntranceView/hooks/` |
| `hooks/useFlipbookEntranceTimeline.ts` | `views/FlipbookEntranceView/hooks/` |
| `hooks/useFlipbookEntranceWheelFrames.ts` | `views/FlipbookEntranceView/hooks/` |
| `hooks/useFlipbookResultActions.ts` | `views/FlipbookResultView/hooks/` |
| `hooks/useFlipbookResultAutoCycle.ts` | `views/FlipbookResultView/hooks/` |
| `hooks/useFlipbookResultPlayback.ts` (있다면) | `views/FlipbookResultView/hooks/` |

> 이동 후 `hooks/index.ts` 배럴에서 이동된 훅 제거.  
> View 폴더에 `hooks/index.ts` 배럴 생성 필수.

### Feature-level 훅 유지

`useFlipbook`, `useFlipbookRealtimeConnection`, `useFlipbookSessionModel`, `useFlipbookTimer`, `useFlipbookNickname`, `useFlipbookGifDownload`, `useFlipbookPrintReveal`, `useResponsiveElementScale`

### Components 유지

`FlipbookPaperBackground`, `FlipbookStepTabs`, `FlipbookNicknameModal`, `result-print/` (Stage 파일)

---

## Phase 8 — `relay-drawing` feature

**담당자**: \_\_\_\_\_\_\_\_  
**예상 시간**: 1.5일  
**파일 경로**: `src/features/relay-drawing/`

### View 이동

| 현재 위치 | 이동 대상 |
|-----------|-----------|
| `components/RelayBoothView.tsx` | `views/RelayBoothView/index.tsx` |
| `components/RelayDrawingView.tsx` | `views/RelayDrawingView/index.tsx` |
| `components/RelayLobbyView.tsx` | `views/RelayLobbyView/index.tsx` |
| `components/RelayResultView.tsx` | `views/RelayResultView/index.tsx` |
| `components/RelayFinalizingView.tsx` | `views/RelayFinalizingView/index.tsx` |

### Section 이동

| 현재 위치 | 이동 대상 |
|-----------|-----------|
| `components/drawing-stage/` 전체 | `views/RelayDrawingView/sections/` |
| `components/result-view/` 전체 | `views/RelayResultView/sections/` |
| `components/round-transition/` 전체 | 등장하는 View(들)의 `sections/` 또는 `RelayDrawingView/sections/round-transition/` |

### Components 유지

`CountdownTimer`, `RoundProgressBar`, `RoundProgressPanel`, `LabelPaperCard`, `RelayArtworkCard`, `RelayLabelCard`, `RelayBgmToggle`, `RelayButton`, `RelayFloatingControls`, `RelayHowToPlayButton`, `RelayHowToPlayModalHost`, 모달류

### 훅 분류

각 `use*View*` 훅이 단일 View에서만 쓰인다면 해당 View의 `hooks/`로 이동. (예: `useRelayBooth` → `views/RelayBoothView/hooks/`)

---

## Phase 9 — `infinite-canvas` feature

**담당자**: \_\_\_\_\_\_\_\_  
**예상 시간**: 2일  
**파일 경로**: `src/features/infinite-canvas/`

> `infinity-runtime/` 이중 중첩 구조 존재 — 신중하게 접근.

### 외부 View 이동

| 현재 위치 | 이동 대상 |
|-----------|-----------|
| `components/InfiniteCanvasBoothView.tsx` | `views/InfiniteCanvasBoothView/index.tsx` |

### infinity-runtime/ 내부 View 이동

| 현재 위치 | 이동 대상 |
|-----------|-----------|
| `infinity-runtime/components/InfinityBoothView.tsx` | `infinity-runtime/views/InfinityBoothView/index.tsx` |
| `infinity-runtime/components/InfinityLobbyView.tsx` | `infinity-runtime/views/InfinityLobbyView/index.tsx` |
| `infinity-runtime/components/InfinityResultView.tsx` | `infinity-runtime/views/InfinityResultView/index.tsx` |
| `infinity-runtime/components/InfinityStageView.tsx` | `infinity-runtime/views/InfinityStageView/index.tsx` |

### stage/ 하위 Konva shape 컴포넌트 배치

`infinity-runtime/components/stage/` 하위 파일들 → `infinity-runtime/views/InfinityStageView/sections/` 또는 별도 `stage/` 폴더 유지 (팀 논의)

### 훅 분류

- View 전용: `useInfinityBooth`, `useInfinityLobby` 등 → 해당 View `hooks/`로 이동
- Feature-level 유지: `useInfinityRealtimeConnection`, `useInfinityHistory`, `useInfinityDrawing` 등 다중 View 관여 훅

---

## Phase 10 — `admin` feature

**담당자**: \_\_\_\_\_\_\_\_  
**예상 시간**: 1.5일  
**파일 경로**: `src/features/admin/`

> **격리 보장**: `features/admin/` ↔ 일반 `features/` 간 import 완전 금지 재확인.

각 admin 서브모듈 내부에 views/sections 구조 적용:

| 서브모듈 | 현재 Page 컴포넌트 | 대상 |
|---------|------------|------|
| `analytics/` | `AdminAnalyticsPage.tsx` | Page 유지, 내부 sections 분리 |
| `dashboard/` | `AdminDashboardPage.tsx` | Page 유지, 내부 sections 분리 |
| `cs-inquiries/` | 내부 컴포넌트들 | section 분리 평가 |
| `community-canvas/` | 내부 컴포넌트들 | section 분리 평가 |
| `gms-prompts/` | `GmsPromptsPage.tsx` | Page 유지, 내부 sections 분리 |
| `active-rooms/` | 내부 컴포넌트들 | section 분리 평가 |
| `backoffice-management/` | 내부 컴포넌트들 | section 분리 평가 |

---

## Phase 11 — `shared/` 레이어 감사

**담당자**: \_\_\_\_\_\_\_\_  
**예상 시간**: 0.5일  
**전제 조건**: Phase 2~10 모두 완료 후 수행

**확인 항목**:
- [ ] `shared/apis/` 파일명이 `{domain}Api.ts` 패턴인지 확인
- [ ] `shared/layouts/` 파일이 `{Name}Layout.tsx` 패턴인지 확인
- [ ] `process.env` 직접 참조가 feature/worlds 코드에 없는지: `grep -r "process\.env\." src/features src/worlds`
- [ ] 미사용 exports가 `shared/` 배럴에 남아있지 않은지 확인

---

## 공통 작업 절차

1. Phase 1 완료 브랜치에서 새 브랜치 생성: `git checkout -b fe/refactor/phase-{N}-{feature}`
2. `docs/structure.md`, `docs/naming.md` 숙지
3. 이동 대상 파일을 새 경로로 이동 후 import 경로 일괄 수정
4. 이동한 폴더에 `index.ts` 배럴 생성 (없는 경우)
5. 기존 배럴(`hooks/index.ts`, `components/index.ts`)에서 이동된 항목 제거
6. 상위 `features/{name}/index.ts`에서 새 경로 재export 확인
7. `pnpm build` + `pnpm type-check` 통과 확인
8. 브라우저에서 해당 피처 동작 확인

---

*작성일: 2026-05-25 | Phase 1 완료 브랜치: `fe/dev`*
