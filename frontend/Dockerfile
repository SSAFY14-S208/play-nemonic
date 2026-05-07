# ============================================================
# Next.js 16 Standalone Dockerfile (pnpm)
#
# 빌드 최적화:
# - builder 단일 스테이지에서 install + build (pnpm symlink 구조와
#   Docker stage 간 COPY 충돌 회피)
# - corepack으로 pnpm 버전 pin (재현 가능한 빌드)
# - 최종 runner는 .next/standalone만 복사해 이미지 크기 최소화
# - non-root user (보안)
# ============================================================

# ===== Stage 1: Builder =====
FROM node:20-alpine AS builder

# 일부 native 모듈에 필요
RUN apk add --no-cache libc6-compat

# corepack으로 pnpm 활성화 + 버전 pin
# (latest 사용 시 빌드 시점마다 pnpm 버전이 바뀌어 결과가 흔들리는 문제 방지)
RUN corepack enable && \
    corepack prepare pnpm@10.25.0 --activate

WORKDIR /app

# 의존성 파일만 먼저 복사 → 캐시 레이어 활용
# pnpm-workspace.yaml, .npmrc는 있을 수도 없을 수도 있어서 * 사용
COPY package.json pnpm-lock.yaml* pnpm-workspace.yaml* .npmrc* ./

# --frozen-lockfile: lockfile과 package.json 불일치 시 실패 (재현 가능한 빌드)
RUN pnpm install --frozen-lockfile

# 소스 복사
COPY . .

# Next.js telemetry 비활성화
ENV NEXT_TELEMETRY_DISABLED=1

# 빌드 메모리 여유 (Next.js 빌드는 메모리 많이 씀)
ENV NODE_OPTIONS="--max-old-space-size=4096"

# 빌드 (next.config의 output: 'standalone' 필요)
RUN pnpm build

# ===== Stage 2: Runner =====
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
