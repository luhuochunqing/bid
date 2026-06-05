import { describe, expect, it } from 'vitest'
import source from './TenderSearchCard.vue?raw'

describe('TenderSearchCard focus styles', () => {
  it('provides visible keyboard focus without removing custom MouseEvent', () => {
    // General focus removal should NOT use !important (allows focus-visible to override)
    expect(source).toContain('.tender-search-card :deep(.filter-select),')
    expect(source).toContain('.tender-search-card :deep(.search-input),')
    // base rule has outline: none without !important
    expect(source).toContain('outline: none;')
    // focus-visible uses !important so it reliably overrides the base rule
    expect(source).toContain('.tender-search-card :deep(.filter-select:focus-visible)')
    expect(source).toContain('outline: 2px solid var(--el-color-primary-light-3')
    // box-shadow focus ring for input/select containers
    expect(source).toContain('box-shadow: 0 0 0 2px var(--el-color-primary-light-3')
  })

  it('keeps Element Plus select active color gray inside the search card', () => {
    expect(source).toContain('.filter-select {')
    expect(source).toContain('--el-color-primary: var(--gray-200, #D0D0D0)')
  })
})
