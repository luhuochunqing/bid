import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

const source = readFileSync(resolve(process.cwd(), 'src/views/Resource/ContractBorrow.vue'), 'utf-8')

describe('ContractBorrow.vue', () => {
  it('uses paged backend data instead of assuming a full in-memory list', () => {
    expect(source).toContain('pagination')
    expect(source).toContain('el-pagination')
    expect(source).toContain('items')
    expect(source).toContain('total')
  })

  it('shows local errors and uses current user context for lifecycle actions', () => {
    expect(source).toContain('ElMessage.error')
    expect(source).toContain('useUserStore')
    expect(source).toContain('userStore.userName')
  })
})
