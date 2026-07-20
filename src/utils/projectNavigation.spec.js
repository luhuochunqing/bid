// Input: projectNavigation.js
// Output: unit tests for navigateToProject / navigateToProjectList
// Pos: src/utils/__tests__/ - Utility test
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
import { describe, it, expect, vi, beforeEach } from 'vitest'

// Mock element-plus 的 ElMessage（使用 vi.hoisted 确保 mock 变量在 vi.mock 之前初始化）
const { mockWarning } = vi.hoisted(() => ({
  mockWarning: vi.fn(),
}))

vi.mock('element-plus', () => ({
  ElMessage: {
    warning: mockWarning,
    success: vi.fn(),
    error: vi.fn(),
    info: vi.fn()
  }
}))

import { navigateToProject, navigateToProjectList, PROJECT_NOT_LINKED_MESSAGE } from './projectNavigation.js'

describe('navigateToProject', () => {
  let mockRouter

  beforeEach(() => {
    mockRouter = { push: vi.fn() }
    mockWarning.mockClear()
  })

  it('调用 router.push 正确参数（数字 ID）', () => {
    navigateToProject(mockRouter, 138)
    expect(mockRouter.push).toHaveBeenCalledWith({
      name: 'ProjectDetail',
      params: { id: '138' }
    })
    expect(mockWarning).not.toHaveBeenCalled()
  })

  it('调用 router.push 正确参数（字符串 ID）', () => {
    navigateToProject(mockRouter, '250')
    expect(mockRouter.push).toHaveBeenCalledWith({
      name: 'ProjectDetail',
      params: { id: '250' }
    })
  })

  it('projectId 为 null 时不调用 router.push，显示 warning', () => {
    navigateToProject(mockRouter, null)
    expect(mockRouter.push).not.toHaveBeenCalled()
    expect(mockWarning).toHaveBeenCalledWith(PROJECT_NOT_LINKED_MESSAGE)
  })

  it('projectId 为 undefined 时不调用 router.push，显示 warning', () => {
    navigateToProject(mockRouter, undefined)
    expect(mockRouter.push).not.toHaveBeenCalled()
    expect(mockWarning).toHaveBeenCalledWith(PROJECT_NOT_LINKED_MESSAGE)
  })

  it('projectId 为空字符串时不调用 router.push，显示 warning', () => {
    navigateToProject(mockRouter, '')
    expect(mockRouter.push).not.toHaveBeenCalled()
    expect(mockWarning).toHaveBeenCalledWith(PROJECT_NOT_LINKED_MESSAGE)
  })

  it('projectId 为 0 时不调用 router.push，显示 warning', () => {
    navigateToProject(mockRouter, 0)
    expect(mockRouter.push).not.toHaveBeenCalled()
    expect(mockWarning).toHaveBeenCalledWith(PROJECT_NOT_LINKED_MESSAGE)
  })

  it('传 stage 时走 ProjectDetailStage 路径参数路由（/project/:id/:stage）', () => {
    navigateToProject(mockRouter, 138, { stage: 'drafting' })
    expect(mockRouter.push).toHaveBeenCalledWith({
      name: 'ProjectDetailStage',
      params: { id: '138', stage: 'drafting' }
    })
    expect(mockWarning).not.toHaveBeenCalled()
  })

  it('不传 stage 时走 ProjectDetail 基础路由（无 query 残留）', () => {
    navigateToProject(mockRouter, 138)
    expect(mockRouter.push).toHaveBeenCalledWith({
      name: 'ProjectDetail',
      params: { id: '138' }
    })
  })
})

describe('navigateToProjectList', () => {
  it('调用 router.push 跳转到 /project', () => {
    const mockRouter = { push: vi.fn() }
    navigateToProjectList(mockRouter)
    expect(mockRouter.push).toHaveBeenCalledWith('/project')
  })
})

describe('PROJECT_NOT_LINKED_MESSAGE', () => {
  it('导出常量值为"该标讯未关联项目"', () => {
    expect(PROJECT_NOT_LINKED_MESSAGE).toBe('该标讯未关联项目')
  })
})
