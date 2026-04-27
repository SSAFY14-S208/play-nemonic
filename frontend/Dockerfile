# ============================================================
# Next.js 16 Standalone Dockerfile
# 
# 빌드 최적화:
# - 멀티 스테이지로 최종 이미지 크기 최소화
# - 의존성 레이어 분리로 캐시 최대 활용
# - npm ci 사용 (package-lock.json 기반, 재현 가능한 빌드)
# - non-root user (보안)
# ============================================================

# ===== Stage 1: Dependencies =====
FROM node:20-alpine AS deps

# npm 패키지 빌드용 도구 (일부 native 모듈 필요)
RUN apk add --no-cache libc6-compat

WORKDIR /app

# 의존성 파일만 먼저 복사 → 캐시 레이어 활용
COPY package.json package-lock.json* ./

RUN npm ci --frozen-lockfile

# ===== Stage 2: Builder =====
FROM node:20-alpine AS builder

WORKDIR /app

# 의존성 복사
COPY --from=deps /app/node_modules ./node_modules

# 소스 복사
COPY . .

# Next.js telemetry 비활성화 (선택)
ENV NEXT_TELEMETRY_DISABLED=1

# 빌드 (next.config.js의 output: 'standalone' 필요)
RUN npm run build

# ===== Stage 3: Runner =====
FROM node:20-alpine AS runner

WORKDIR /app

ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1
ENV PORT=3000
ENV HOSTNAME="0.0.0.0"

# non-root 사용자 생성
RUN addgroup --system --gid 1001 nodejs && \
    adduser --system --uid 1001 nextjs

# Standalone 빌드 결과물 복사
# .next/standalone에 server.js + 필요한 node_modules만 들어있음
COPY --from=builder --chown=nextjs:nodejs /app/.next/standalone ./
COPY --from=builder --chown=nextjs:nodejs /app/.next/static ./.next/static
COPY --from=builder --chown=nextjs:nodejs /app/public ./public

USER nextjs

EXPOSE 3000

# Standalone server 실행
CMD ["node", "server.js"]
