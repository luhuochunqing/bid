// Input: Playwright process lifecycle and repository-local teardown scripts
// Output: cleaned API-backed E2E baseline when this run started it
// Pos: e2e/ - Playwright end-to-end coverage
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { execFileSync } from 'node:child_process'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const rootDir = path.resolve(__dirname, '..')
const stopScript = path.join(rootDir, 'scripts/test/stop-api-e2e-stack.sh')

export default async function globalTeardown() {
  if (process.env.PLAYWRIGHT_DISABLE_API_BOOTSTRAP === '1') {
    return
  }

  execFileSync('bash', [stopScript], {
    cwd: rootDir,
    stdio: 'inherit',
    env: process.env,
  })
}
