import { describe, expect, it } from 'vitest'
import source from './TenderSearchCard.vue?raw'

describe('TenderSearchCard focus styles', () => {
  it('keeps search inputs full width and avoids primary blue select accents', () => {
    expect(source).toContain('.search-input, .filter-select { width: 100%;')
    expect(source).toContain('--focus-ring-color: transparent;')
    expect(source).toContain('--el-input-hover-border-color: var(--gray-200, #D0D0D0);')
    expect(source).toContain('.filter-select { --el-color-primary: var(--gray-200, #D0D0D0)')
    expect(source).toContain('--el-select-input-focus-border-color: var(--gray-200, #D0D0D0);')
  })

  it('keeps Element Plus select active color gray inside the search card', () => {
    expect(source).toContain('.filter-select { --el-color-primary: var(--gray-200, #D0D0D0)')
    expect(source).toContain('--el-color-primary-light-3: var(--gray-200, #D0D0D0)')
  })
})
