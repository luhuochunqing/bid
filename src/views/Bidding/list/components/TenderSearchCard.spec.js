import { describe, expect, it } from 'vitest'
import source from './TenderSearchCard.vue?raw'
import { SOURCE_FILTER_OPTIONS } from '../constants.js'

describe('TenderSearchCard source filters', () => {
  it('uses backend source values for the source platform filter', () => {
    expect(SOURCE_FILTER_OPTIONS).toEqual(['人工录入', 'CRM创建', '第三方平台'])
    expect(SOURCE_FILTER_OPTIONS).not.toContain('CRM商机转入')
    expect(SOURCE_FILTER_OPTIONS).not.toContain('第三方标讯平台名称')
    expect(SOURCE_FILTER_OPTIONS).not.toContain('CRM 创建')
  })
})

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
