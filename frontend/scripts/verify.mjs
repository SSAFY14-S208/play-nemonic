#!/usr/bin/env node
/**
 * 독립 실행 가능한 프론트엔드 코드 검증 스크립트.
 * Claude Code Stop hook과 Codex 양쪽에서 공용으로 사용한다.
 *
 * 사용법:
 *   node scripts/verify.mjs              # git status로 변경 파일 자동 감지
 *   node scripts/verify.mjs src/a.ts     # 특정 파일만 검사
 */
import { execSync } from 'child_process'
import path from 'path'
import { fileURLToPath } from 'url'

const frontendDir = path.dirname(path.dirname(fileURLToPath(import.meta.url)))
const projectRoot = path.resolve(frontendDir, '..')

// 검사 대상 파일 결정
let targetFiles = process.argv.slice(2)

if (targetFiles.length === 0) {
  try {
    const gitOut = execSync('git status --porcelain frontend/', {
      encoding: 'utf8',
      cwd: projectRoot,
    })
    targetFiles = gitOut
      .split('\n')
      .map(line => line.slice(3).trim())
      .filter(file => file.endsWith('.ts') || file.endsWith('.tsx'))
  } catch {
    console.log('git status 실행 실패 — 변경 파일 감지를 건너뜁니다.')
  }
}

if (targetFiles.length === 0) {
  console.log('검증할 변경된 TypeScript 파일이 없습니다.')
  process.exit(0)
}

const failures = []

// ── TypeScript 타입 검사 ───────────────────────────────────────────
try {
  execSync('npx tsc --noEmit', { encoding: 'utf8', cwd: frontendDir, stdio: 'pipe' })
  console.log('✓ TypeScript: 오류 없음')
} catch (err) {
  failures.push('### TypeScript 타입 오류\n```\n' + (err.stdout ?? err.message) + '\n```')
}

// ── ESLint (수정된 src/ 파일만) ───────────────────────────────────
const srcFiles = targetFiles
  .filter(file => file.startsWith('frontend/src/'))
  .map(file => file.replace('frontend/', ''))

if (srcFiles.length > 0) {
  try {
    execSync(`npx eslint ${srcFiles.join(' ')} --max-warnings 0`, {
      encoding: 'utf8',
      cwd: frontendDir,
      stdio: 'pipe',
    })
    console.log('✓ ESLint: 오류 없음')
  } catch (err) {
    failures.push('### ESLint 오류\n```\n' + (err.stdout ?? err.message) + '\n```')
  }
}

if (failures.length > 0) {
  console.log('\n검증 실패 — 아래 오류를 수정하세요:\n\n' + failures.join('\n\n'))
  process.exit(1)
}

console.log('\n모든 검증 통과')
process.exit(0)