// Input: Account.vue source code
// Output: static assertions for 429-safe detail loading and rate-limit fallbacks
// Pos: src/views/Resource/ - Account page regression tests
import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

const source = readFileSync(resolve(process.cwd(), 'src/views/Resource/Account.vue'), 'utf-8')

describe('Account.vue rate-limit safety', () => {
  it('imports notifyErrorUnlessRateLimit for 429-aware error handling', () => {
    expect(source).toContain('notifyErrorUnlessRateLimit')
  })

  it('handles 429 in loadAccounts without raw AxiosError toast', () => {
    expect(source).toContain('notifyErrorUnlessRateLimit(e,')
    expect(source).not.toMatch(/catch\s*\([^)]*\)\s*\{[^}]*ElMessage\.error\s*\(\s*e\.message/)
  })

  it('handles 429 in loadAccountDetail without raw AxiosError toast', () => {
    expect(source).toMatch(/const loadAccountDetail[\s\S]*?notifyErrorUnlessRateLimit/)
  })

  it('does not let a single detail failure break the whole list', () => {
    expect(source).toContain('loadAccountDetail(row).catch(() => row)')
  })

  it('uses serial detail loading to avoid request burst', () => {
    expect(source).toMatch(/DETAIL_CONCURRENCY\s*=\s*1/)
  })
})
