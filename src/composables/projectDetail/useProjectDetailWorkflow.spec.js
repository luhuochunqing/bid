import { describe, it, expect, vi } from 'vitest'
import { useProjectDetailWorkflow } from './useProjectDetailWorkflow.js'

// Mock stores
const mockUserStore = {
  userName: 'admin',
  currentUser: { id: 1, name: 'admin' },
  users: []
}
const mockRoute = { params: { id: 'P001' } }
const mockProjectStore = {
  currentProject: null,
  fetchProject: vi.fn(),
  getProjectById: vi.fn()
}

vi.mock('@/api', () => ({
  projectApi: { getProject: vi.fn().mockResolvedValue(null) }
}))

describe('useProjectDetailWorkflow — userStore.users 死代码移除验证', () => {
  it('availableReviewers 返回空数组（userStore.users 已移除）', () => {
    const workflow = useProjectDetailWorkflow({
      userStore: mockUserStore,
      route: mockRoute,
      projectStore: mockProjectStore,
      isApiProject: true
    })
    expect(workflow.availableReviewers.value).toEqual([])
  })

  it('composable 可正常初始化', () => {
    const workflow = useProjectDetailWorkflow({
      userStore: mockUserStore,
      route: mockRoute,
      projectStore: mockProjectStore,
      isApiProject: true
    })
    expect(workflow.bidProcess).toBeDefined()
    expect(workflow.reviewers).toBeDefined()
    expect(workflow.draftForm).toBeDefined()
  })
})
