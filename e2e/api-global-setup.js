// Input: Playwright process lifecycle and repository-local bootstrap scripts
// Output: ready API-backed E2E baseline or a single actionable startup failure
// Pos: e2e/ - Playwright end-to-end coverage
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { execFileSync } from 'node:child_process'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { ensureManagedStackReady, resolveApiBaseUrl, resolveWebBaseUrl } from './api-baseline.js'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const rootDir = path.resolve(__dirname, '..')
const startScript = path.join(rootDir, 'scripts/test/start-api-e2e-stack.sh')

export default async function globalSetup() {
  if (process.env.PLAYWRIGHT_DISABLE_API_BOOTSTRAP === '1') {
    return
  }

  const status = await ensureManagedStackReady()
  if (status.ready) {
    return
  }

  try {
    execFileSync('bash', [startScript], {
      cwd: rootDir,
      stdio: 'inherit',
      env: process.env,
    })
  } catch (error) {
    throw new Error(
      [
        'Unable to prepare the API-backed Playwright baseline.',
        `Expected API health at ${resolveApiBaseUrl()}/actuator/health`,
        `Expected web app at ${resolveWebBaseUrl()}`,
        'You can inspect .rehearsal/backend.log and .rehearsal/frontend.log for details.',
        `Bootstrap script: ${startScript}`,
        `Original error: ${error instanceof Error ? error.message : String(error)}`,
      ].join('\n'), { cause: error }
    )
  }
}
