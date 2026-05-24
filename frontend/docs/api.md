# API & Data Fetching

## Two Auth Flows — Two Separate Clients

The codebase has two distinct authentication flows. Each has its own ky client. Both flows send their identifier as an **HTTP header**, and domain functions take **no identifier argument** — interceptors inject it from a Zustand store.

| Client     | File                           | Domains                                                      | Auth header                          | Source store                       |
|------------|--------------------------------|--------------------------------------------------------------|--------------------------------------|------------------------------------|
| `api`      | `shared/libs/apiClient.ts`     | user, gallery, community, share, relay, invite, flipbook, file | `Anonymous-User-UUID: {userUuid}`    | `useUserStore` (localStorage)      |
| `adminApi` | `shared/libs/adminApiClient.ts`| admins, `auth/logout`                                        | `Authorization: Bearer {accessToken}`| `useAdminAuthStore` (sessionStorage)|

### General User Flow — Anonymous UUID Header

The backend issues `userUuid` on first visit. Every subsequent request is identified via the `Anonymous-User-UUID` header. There is no JWT or session cookie.

- The `api` `beforeRequest` hook reads `useUserStore.getState().userUuid` and injects it as `Anonymous-User-UUID` when present.
- Some endpoints don't need the header (`POST /users/anonymous` runs before issuance so the store is empty; `GET /community/{id}` is public). When the store is null the hook skips injection — no special-casing needed.
- Domain functions do NOT take `userUuid` as an argument. Body/query never contains a `userUuid` field — the backend reads only the header.
- Other users' UUIDs (e.g. a kick target's `targetUserUuid`) are domain data, not identity, and stay in the request body.

### Backoffice Flow — Admin Bearer Token

`auth/*` and `admins/*` require `Authorization: Bearer {accessToken}`. The backoffice uses a separate ky instance (`adminApi`) whose hooks:
- inject the access token from `useAdminAuthStore` on every request (`beforeRequest`),
- catch a 401 response, call `auth/reissue` with the stored refresh token, update the store, and retry the original request once (`afterResponse`),
- de-duplicate concurrent reissue calls so multiple in-flight 401s share a single refresh.

Admin domain functions have **no `accessToken` argument** — the token is injected automatically. `auth/login` and `auth/reissue` must use the regular `api` (calling them with `adminApi` would recurse on 401). Only `auth/logout` uses `adminApi`.

## Three Layers

```
shared/libs/apiClient.ts        ← user-facing ky transport
shared/libs/adminApiClient.ts   ← backoffice ky transport (token auto-inject + 401 reissue retry)
shared/utils/apiUnwrap.ts       ← ApiResponse<T> unwrap helper
shared/apis/apiError.ts         ← ApiError class (bound to backend envelope)
shared/apis/{domain}Api.ts      ← individual function exports per endpoint
shared/stores/userStore.ts      ← Zustand persist (localStorage) — userUuid
shared/stores/adminAuthStore.ts ← Zustand persist (sessionStorage) — admin tokens
shared/hooks/useUserBootstrap.ts← calls postAnonymousVerify → postAnonymous on mount
shared/components/UserBootstrap ← mounted once in app/layout.tsx
```

## apiClient Transport

```ts
// shared/libs/apiClient.ts
const client = ky.create({
  prefix: `${runtime.apiUrl}/api/v1`,
  timeout: 30_000,
  hooks: {
    beforeRequest: [({ request }) => {
      const userUuid = useUserStore.getState().userUuid;
      if (userUuid) request.headers.set('Anonymous-User-UUID', userUuid);
    }],
  },
});

export const api = { get, post, put, patch, delete: del, postForm };
```

`PATCH` is a first-class method (partial updates: nickname, birth-info). `postForm` is for multipart/form-data uploads (relay submissions). Never mix JSON and multipart calls.

## adminApiClient Transport

```ts
// shared/libs/adminApiClient.ts
const adminClient = ky.create({
  prefix: `${runtime.apiUrl}/api/v1`,
  timeout: 30_000,
  hooks: {
    beforeRequest: [({ request }) => {
      const accessToken = useAdminAuthStore.getState().accessToken;
      if (accessToken) request.headers.set('Authorization', `Bearer ${accessToken}`);
    }],
    afterResponse: [async ({ request, response }) => {
      if (response.status !== 401) return;
      if (request.url.includes('/auth/reissue')) return;
      const newAccessToken = await refreshAccessToken();
      if (!newAccessToken) return;
      const retry = request.clone();
      retry.headers.set('Authorization', `Bearer ${newAccessToken}`);
      return fetch(retry);
    }],
  },
});
export const adminApi = { /* same shape as api */ };
```

- Concurrent 401s share a single in-flight reissue (deduped via module-scoped `pendingReissue` promise).
- If reissue itself fails, the store is cleared. Hooks reading `useAdminAuthStore.accessToken === null` should redirect to the admin login page.

## Choosing a Client for a New Domain

| Backend OpenAPI security              | Client     | Identifier arg on domain functions |
|---------------------------------------|------------|------------------------------------|
| `Anonymous-User-UUID` header or none  | `api`      | None — store auto-injects header   |
| `bearerAuth` (admin token)            | `adminApi` | None — store auto-injects header   |
| Bootstrap endpoints (`login`, `reissue`) | `api`   | None — no caller identity yet      |

## ApiResponse Envelope + apiUnwrap

All backend responses: `ApiResponse<T> = { success, message, data, errors? }`. `apiUnwrap` converts `success: false` to a thrown `ApiError` and returns `data: T`.

```ts
// shared/apis/userApi.ts
import { api } from "@/shared/libs";
import { apiUnwrap } from "@/shared/utils";
import type { ApiResponse, AnonymousUserResponse } from "@/shared/types";

export const postAnonymous = () =>
  apiUnwrap(api.post<ApiResponse<AnonymousUserResponse>>("users/anonymous"));

export const patchAnonymousNickname = (payload: AnonymousUserNicknameRequest) =>
  apiUnwrap(api.patch<ApiResponse<AnonymousUserNicknameResponse>>("users/anonymous/nickname", payload));
```

Catch-side handling in a feature hook:

```ts
import { ApiError } from "@/shared/apis";
import { HTTPError } from "ky";

try {
  await patchAnonymousNickname({ nickname: "망고" });
} catch (error) {
  if (error instanceof ApiError) {
    setNicknameError(error.errors?.nickname);  // 200 + success:false
  } else if (error instanceof HTTPError) {
    // 4xx/5xx HTTP error
  }
}
```

## Domain API Naming — Individual Exports, Never Grouped Objects

`{httpMethod}{ResourcePath}` camelCase. Domain prefix obvious from context is dropped. Single resource = singular noun; list = `List` suffix.

| HTTP  | Path                            | Function                  |
|-------|---------------------------------|---------------------------|
| POST  | `/users/anonymous`              | `postAnonymous`           |
| POST  | `/users/anonymous/verify`       | `postAnonymousVerify`     |
| POST  | `/users/anonymous/birth-info`   | `postAnonymousBirthInfo`  |
| PATCH | `/users/anonymous/birth-info`   | `patchAnonymousBirthInfo` |
| PATCH | `/users/anonymous/nickname`     | `patchAnonymousNickname`  |
| GET   | `/users/anonymous/profile`      | `getAnonymousProfile`     |
| GET   | `/gallery` (list)               | `getGalleryList`          |
| GET   | `/gallery/{galleryId}` (single) | `getGallery`              |
| DELETE| `/gallery/{galleryId}`          | `deleteGallery`           |
| GET   | `/community/memos`              | `getCommunityMemoList`    |
| POST  | `/community/memos`              | `postCommunityMemo`       |
| GET   | `/community/memos/{memoId}`     | `getCommunityMemo`        |
| PATCH | `/community/memos/{memoId}`     | `patchCommunityMemo`      |
| DELETE| `/community/memos/{memoId}`     | `deleteCommunityMemo`     |
| POST  | `/community/memos/{memoId}/reports` | `postCommunityMemoReport` |

Signatures: first arg is path param, then domain data. Never the caller's identifier.
Examples: `getGallery(galleryId)`, `patchRelayRoomSettings(roomCode, timeLimitSeconds)`, `postRelayRoomKick(roomCode, targetUserUuid)`.

```ts
// feature hook imports only what it needs
import { postAnonymous, getGalleryList } from "@/shared/apis";
```

Do NOT group into objects (`userApi.foo()`).

## User Identity Bootstrap

- `userUuid` lives in `useUserStore` (Zustand `persist`, **localStorage**, key `nemonic-user`). No separate localStorage sync code needed.
- Root `app/layout.tsx` mounts `<UserBootstrap />` once. After hydration, `useUserBootstrap` calls `postAnonymousVerify` (if uuid stored) or `postAnonymous` (cold start).
- Pages/features must NOT call verify/createAnonymous directly.

## Admin Auth Bootstrap

- Admin tokens live in `useAdminAuthStore` (Zustand `persist`, **sessionStorage**, key `nemonic-admin-auth`). sessionStorage is intentional — tokens must not survive tab close on shared devices. Do NOT switch to localStorage.
- Login: `postLogin` → `useAdminAuthStore.setTokens(loginResponse)`. Interceptor injects token from then on.
- Logout: `postLogout(refreshToken)` → backend blacklist → `useAdminAuthStore.clear()`.
- `app/admin/layout.tsx` `<AdminAuthGuard>` redirects to `/admin/login` when `accessToken === null`.

Use native `fetch` directly only in server components for OG metadata generation (Next.js cache control).

## Environment Variables

- `NEXT_PUBLIC_*`: accessible in client components (e.g. `NEXT_PUBLIC_API_URL`)
- No prefix: server-only — always `undefined` in client components
- All env access must go through `shared/config/` — never reference `process.env.X` directly in feature code

```ts
// shared/config/runtime.ts
export const runtime = {
  apiUrl: process.env.NEXT_PUBLIC_API_URL!,
  isDev: process.env.NODE_ENV === "development",
};
```
