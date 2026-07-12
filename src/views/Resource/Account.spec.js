// Input: Account.vue source code
// Output: static assertions for N+1 elimination and 429 safety
// Pos: src/views/Resource/ - Account page regression tests
import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

const source = readFileSync(resolve(process.cwd(), 'src/views/Resource/Account.vue'), 'utf-8')

describe('Account.vue N+1 elimination (spec 035 root cause)', () => {
  it('loadAccounts does NOT call loadDetailsInBatches or getDetail', () => {
    // loadAccounts 函数体内不应出现 getDetail 或 loadDetailsInBatches 调用
    const loadAccountsBlock = source.match(/const loadAccounts[\s\S]*?^}/m)?.[0] || ''
    expect(loadAccountsBlock).not.toContain('loadDetailsInBatches')
    expect(loadAccountsBlock).not.toContain('getDetail')
    expect(loadAccountsBlock).not.toContain('loadAccountDetail')
  })

  it('does not define DETAIL_CONCURRENCY or loadDetailsInBatches', () => {
    expect(source).not.toContain('DETAIL_CONCURRENCY')
    expect(source).not.toContain('loadDetailsInBatches')
  })

  it('loadAccounts directly assigns list response data to accounts.value', () => {
    expect(source).toMatch(/accounts\.value\s*=\s*Array\.isArray\(res\.data\)/)
  })
})

describe('Account.vue 429 safety', () => {
  it('imports notifyErrorUnlessRateLimit for 429-aware error handling', () => {
    expect(source).toContain('notifyErrorUnlessRateLimit')
  })

  it('loadAccountDetail uses notifyErrorUnlessRateLimit instead of ElMessage.error', () => {
    const detailBlock = source.match(/const loadAccountDetail[\s\S]*?^}/m)?.[0] || ''
    expect(detailBlock).toContain('notifyErrorUnlessRateLimit')
    expect(detailBlock).not.toMatch(/ElMessage\.error\s*\(\s*e\.message/)
  })

  it('loadAccounts catch block uses notifyErrorUnlessRateLimit', () => {
    const loadAccountsBlock = source.match(/const loadAccounts[\s\S]*?^}/m)?.[0] || ''
    expect(loadAccountsBlock).toContain('notifyErrorUnlessRateLimit')
  })
})
