// 환경 의존 설정 — dev/staging/prod마다 다를 수 있는 값
export const runtime = {
  apiUrl: process.env.NEXT_PUBLIC_API_URL ?? '',
  websocketUrl: process.env.NEXT_PUBLIC_WEBSOCKET_URL ?? '',
  isDev: process.env.NODE_ENV === 'development',
  fortuneMockEnabled: process.env.NEXT_PUBLIC_FORTUNE_MOCK_ENABLED === 'true',
  loggingEnabled: process.env.NEXT_PUBLIC_LOGGING_ENABLED !== 'false',
  grafanaUrl: process.env.NEXT_PUBLIC_GRAFANA_URL ?? '',
  opensearchDashboardsUrl: process.env.NEXT_PUBLIC_OPENSEARCH_DASHBOARDS_URL ?? '',
}
