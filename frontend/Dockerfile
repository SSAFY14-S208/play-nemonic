# ============================================================
# Next.js 16 Standalone Dockerfile (pnpm)
#
# 빌드 최적화:
# - 멀티 스테이지로 최종 이미지 크기 최소화
# - corepack으로 pnpm 활성화 (Node 16.13+ 내장 기능)
# - 의존성 레이어 분리로 캐시 최대 활용
# - non-root user (보안)
# ============================================================

# ===== Stage 1: Dependencies =====
FROM node:20-alpine AS deps

# 일부 native 모듈에 필요
RUN apk add --no-cache libc6-compat

# corepack으로 pnpm 활성화 (npm i -g 대신 권장 방식)
# package.json의 packageManager 필드를 자동으로 인식
RUN corepack enable && \
    corepack prepare pnpm@latest --activate

WORKDIR /app

# 의존성 파일만 먼저 복사 → 캐시 레이어 활용
# pnpm-workspace.yaml, .npmrc는 있을 수도 없을 수도 있어서 * 사용
COPY package.json pnpm-lock.yaml* pnpm-workspace.yaml* .npmrc* ./

# --frozen-lockfile: lockfile과 package.json 불일치 시 실패 (재현 가능한 빌드)
RUN pnpm install --frozen-lockfile

# ===== Stage 2: Builder =====
FROM node:20-alpine AS builder

# corepack 활성화
RUN corepack enable && \
    corepack prepare pnpm@latest --activate

WORKDIR /app

# 의존성 복사 (deps 스테이지에서 설치한 것)
COPY --from=deps /app/node_modules ./node_modules

# 소스 복사
COPY . .

# Next.js telemetry 비활성화
ENV NEXT_TELEMETRY_DISABLED=1

# 빌드 메모리 여유 (Next.js 빌드는 메모리 많이 씀)
ENV NODE_OPTIONS="--max-old-space-size=4096"

# 빌드 (next.config의 output: 'standalone' 필요)
RUN pnpm build

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
# pnpm으로 빌드해도 standalone 결과물 형식은 동일 (npm/yarn/pnpm 호환)
COPY --from=builder --chown=nextjs:nodejs /app/.next/standalone ./
COPY --from=builder --chown=nextjs:nodejs /app/.next/static ./.next/static
COPY --from=builder --chown=nextjs:nodejs /app/public ./public

USER nextjs

EXPOSE 3000

# Standalone server 실행
CMD ["node", "server.js"]
