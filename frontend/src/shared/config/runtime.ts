// 환경 의존 설정 — dev/staging/prod마다 다를 수 있는 값
export const runtime = {
  apiUrl: process.env.NEXT_PUBLIC_API_URL ?? '',
  isDev: process.env.NODE_ENV === 'development',
}
