import { describe, expect, it } from 'vitest'
import { execFileSync } from 'node:child_process'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'

const scriptPath = path.resolve(process.cwd(), 'scripts/check-e2e-selectors.mjs')

function initRepoWithSpec(specContent) {
  const repoDir = fs.mkdtempSync(path.join(os.tmpdir(), 'e2e-selector-guard-'))
  const e2eDir = path.join(repoDir, 'e2e')
  fs.mkdirSync(e2eDir, { recursive: true })
  const specPath = path.join(e2eDir, 'sample.spec.js')
  fs.writeFileSync(specPath, specContent, 'utf8')
  return { repoDir, specPath: path.relative(repoDir, specPath) }
}

function runGuard(repoDir, specPath) {
  try {
    const output = execFileSync('node', [scriptPath, specPath], {
      cwd: repoDir,
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'pipe'],
    })
    return { exitCode: 0, output }
  } catch (error) {
    return {
      exitCode: error.status ?? 1,
      output: String(error.stdout || '') + String(error.stderr || ''),
    }
  }
}

function runGuardWithStreams(repoDir, specPath) {
  try {
    const stdout = execFileSync('node', [scriptPath, specPath], {
      cwd: repoDir,
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'pipe'],
    })
    return { exitCode: 0, stdout, stderr: '' }
  } catch (error) {
    return {
      exitCode: error.status ?? 1,
      stdout: String(error.stdout || ''),
      stderr: String(error.stderr || ''),
    }
  }
}

describe('check-e2e-selectors', () => {
  it('passes focused locator patterns used by stable specs', () => {
    const { repoDir, specPath } = initRepoWithSpec(`
      import { test, expect } from '@playwright/test'
      import { byTestId, tid } from './utils/testid.js'

      test('stable archive flow', async ({ page }) => {
        await page.goto('/knowledge/archive')
        await page.waitForResponse(resp => resp.url().includes('/api/archive') && resp.ok())
        await expect(page.locator(byTestId(tid('project-archive', 'table')))).toBeVisible()
        await page.waitForURL(/archive/)
      })
    `)

    const result = runGuard(repoDir, specPath)
    expect(result.exitCode).toBe(0)
    expect(result.output).toMatch(/e2e-selectors: passed\./)
  })

  it('blocks waitForTimeout and networkidle waits', () => {
    const { repoDir, specPath } = initRepoWithSpec(`
      import { test } from '@playwright/test'

      test('fragile archive flow', async ({ page }) => {
        await page.goto('/knowledge/archive')
        await page.waitForLoadState('networkidle')
        await page.waitForTimeout(1000)
      })
    `)

    const result = runGuard(repoDir, specPath)
    expect(result.exitCode).toBe(1)
    expect(result.output).toMatch(/\[networkidle-wait\]/)
    expect(result.output).toMatch(/\[waitForTimeout\]/)
  })

  it('warns when getByRole lacks an accessible name filter', () => {
    const { repoDir, specPath } = initRepoWithSpec(`
      import { test, expect } from '@playwright/test'

      test('ambiguous role selector', async ({ page }) => {
        await page.goto('/knowledge/archive')
        await expect(page.getByRole('button')).toBeVisible()
      })
    `)

    const result = runGuardWithStreams(repoDir, specPath)
    const combinedOutput = result.stdout + result.stderr
    expect(result.exitCode).toBe(0)
    expect(combinedOutput).toMatch(/passed with 1 warning\(s\)/)
  })
})
