#!/usr/bin/env node
/**
 * Claude Code Stop hook — 응답 종료 전 자동 실행되는 래퍼.
 * 실제 검증 로직은 frontend/scripts/verify.mjs에 위임한다.
 *
 * - stop_hook_active === true 이면 무한 루프 방지를 위해 즉시 통과
 * - verify.mjs가 exit 1 이면 → exit 2 로 변환 (Claude Code가 계속 작업하도록)
 */
import { execSync } from 'child_process'
import path from 'path'
import { fileURLToPath } from 'url'

const projectRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')

let rawInput = ''
process.stdin.setEncoding('utf8')
process.stdin.on('data', chunk => { rawInput += chunk })
process.stdin.on('end', () => {
  try {
    const input = JSON.parse(rawInput)

    // 무한 루프 방지
    if (input.stop_hook_active) process.exit(0)

    try {
      execSync('node frontend/scripts/verify.mjs', {
        encoding: 'utf8',
        cwd: projectRoot,
        stdio: 'pipe',
      })
      process.exit(0)
    } catch (err) {
      console.log(
        '자동 검증에 실패했습니다. 아래 오류를 모두 수정한 뒤 완료하세요.\n\n' +
        (err.stdout ?? err.message),
      )
      process.exit(2)
    }
  } catch {
    // 훅 스크립트 자체 오류는 작업을 막지 않음
    process.exit(0)
  }
})
