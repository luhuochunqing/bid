// Input: useProjectDetailState.js
// Output: unit tests for loadError state + project computed
// Pos: src/composables/projectDetail/ - Composable test
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
import { describe, it, expect, beforeEach } from 'vitest'
import { reactive } from 'vue'
import { useProjectDetailState } from './useProjectDetailState.js'

function createContext(overrides = {}) {
  return {
    route: { params: { id: '138' } },
    userStore: {
      currentUser: { role: 'admin', name: 'admin' },
      userName: 'admin',
      hasPermission: () => true,
    },
    projectStore: reactive({ currentProject: null }),
    isDemoMode: false,
    isApiProject: { value: true },
    ...overrides,
  }
}

describe('useProjectDetailState', () => {
  let context

  beforeEach(() => {
    context = createContext()
  })

  it('初始 loading 为 true，loadError 为 null', () => {
    const state = useProjectDetailState(context)
    expect(state.loading.value).toBe(true)
    expect(state.loadError.value).toBeNull()
  })

  it('loadError 可被设置为 no-permission / not-found / network-error', () => {
    const state = useProjectDetailState(context)
    state.loadError.value = 'no-permission'
    expect(state.loadError.value).toBe('no-permission')
    state.loadError.value = 'not-found'
    expect(state.loadError.value).toBe('not-found')
    state.loadError.value = 'network-error'
    expect(state.loadError.value).toBe('network-error')
  })

  it('project computed 在 currentProject 为 null 时返回 null', () => {
    const state = useProjectDetailState(context)
    expect(state.project.value).toBeNull()
  })

  it('project computed 在 currentProject 设置后返回该项目', () => {
    const mockProject = { id: 138, name: '测试项目' }
    context.projectStore.currentProject = mockProject
    const state = useProjectDetailState(context)
    expect(state.project.value).toEqual(mockProject)
  })

  it('loadError 在 return 中被导出', () => {
    const state = useProjectDetailState(context)
    expect(state).toHaveProperty('loadError')
    expect(state.loadError.value).toBeNull()
  })
})
