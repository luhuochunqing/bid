// Input: useProjectDetailNavigation after 067 removal (dead jump functions removed)
// Output: assertion that only live navigation helpers remain
// Pos: src/composables/projectDetail/ - project detail navigation spec
import { describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { useProjectDetailNavigation } from './useProjectDetailNavigation.js'

describe('useProjectDetailNavigation', () => {
  it('keeps only goBack/handleEdit and drops dead routes (067 验收导出剔除)', () => {
    const push = vi.fn()
    const context = {
      route: { params: { id: '42' } },
      router: { push },
    }

    const navigation = useProjectDetailNavigation(context)

    expect(Object.keys(navigation).sort()).toEqual(['goBack', 'handleEdit'])

    navigation.goBack()
    expect(push).toHaveBeenCalledWith('/project')

    navigation.handleEdit()
    expect(push).toHaveBeenCalledWith('/document/editor/42')
  })

  it('no longer exposes jumps to removed expense/bar/bid-result routes (067 验收导出剔除)', () => {
    const navigation = useProjectDetailNavigation({
      route: { params: { id: '1' } },
      router: { push: vi.fn() },
    })

    expect(navigation.goToExpensePage).toBeUndefined()
    expect(navigation.goToResultPage).toBeUndefined()
    expect(navigation.goToAssetManagement).toBeUndefined()
    expect(navigation.goToSiteDetail).toBeUndefined()
    expect(navigation.borrowUK).toBeUndefined()
    expect(navigation.viewSOP).toBeUndefined()
  })

  it('does not depend on assetCheckResult anymore (067 验收导出剔除)', () => {
    // 传入已废弃字段不应影响返回值
    const navigation = useProjectDetailNavigation({
      route: { params: { id: '7' } },
      router: { push: vi.fn() },
      project: ref({ name: 'X' }),
      assetCheckResult: ref({ site: { id: 9 } }),
    })

    expect(typeof navigation.goBack).toBe('function')
  })
})
