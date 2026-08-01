// Input: useProjectCreateModel 的 customFields 收集与回显
// Output: CO-601 US1 — buildApiProjectPayload 携带 customFields（basic/detail 双 scope）；loadProjectData 摊平回显
// Pos: src/views/Project/create/composables/ - 创建向导 model 单元测试
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { describe, expect, it, vi, beforeEach } from 'vitest'

vi.mock('element-plus', () => ({
  ElMessage: { info: vi.fn(), success: vi.fn(), error: vi.fn(), warning: vi.fn() },
}))

import { useProjectCreateModel } from './useProjectCreateModel.js'

function createModel(overrides = {}) {
  return useProjectCreateModel({
    route: { query: { tenderId: '7' }, ...overrides.route },
    userStore: { currentUser: { id: 9, name: '小王' }, ...overrides.userStore },
    projectStore: {
      projects: [],
      getProjects: vi.fn().mockResolvedValue(),
      ...overrides.projectStore,
    },
    router: { push: vi.fn() },
  })
}

describe('useProjectCreateModel customFields — CO-601 创建向导自定义字段', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('buildApiProjectPayload 按 scope 收集自定义字段（schema 减预置清单），预置 key 不进入', () => {
    const model = createModel()
    model.setCustomFieldsSchema('project.basic', [
      { key: 'name' }, // 预置
      { key: 'budgetLevel' }, // 自定义
    ])
    model.setCustomFieldsSchema('project.detail', [
      { key: 'description' }, // 预置
      { key: 'siteVisitDone' }, // 自定义
    ])
    model.basicForm.name = '项目A'
    model.basicForm.deadline = '2026-09-01'
    model.basicForm.budgetLevel = '重点客户'
    model.detailForm.description = '描述'
    model.detailForm.siteVisitDone = true

    const payload = model.buildApiProjectPayload()

    expect(payload.customFields).toEqual({
      'project.basic': { budgetLevel: '重点客户' },
      'project.detail': { siteVisitDone: true },
    })
  })

  it('buildApiProjectPayload 无自定义值时省略 customFields 键', () => {
    const model = createModel()
    model.basicForm.name = '项目A'
    model.basicForm.deadline = '2026-09-01'

    const payload = model.buildApiProjectPayload()

    expect(payload.customFields).toBeUndefined()
  })

  it('loadProjectData 把 customFields 按 scope 摊平进 basicForm/detailForm，预置 key 不被脏数据覆盖', async () => {
    const projectStore = {
      projects: [{
        id: 5,
        name: '项目A',
        customer: '客户X',
        customFields: {
          'project.basic': { budgetLevel: '重点客户', name: '脏数据覆盖尝试' },
          'project.detail': { siteVisitDone: true },
        },
      }],
      getProjects: vi.fn().mockResolvedValue(),
    }
    const model = createModel({ projectStore })

    await model.loadProjectData(5)

    expect(model.basicForm.budgetLevel).toBe('重点客户')
    // 预置 key（name）以 DTO 权威值为准，customFields 中的撞 key 脏数据被忽略
    expect(model.basicForm.name).toBe('项目A')
    expect(model.detailForm.siteVisitDone).toBe(true)
  })

  it('loadProjectData 老项目无 customFields 时不报错', async () => {
    const projectStore = {
      projects: [{ id: 5, name: '老项目', customer: '客户Y' }],
      getProjects: vi.fn().mockResolvedValue(),
    }
    const model = createModel({ projectStore })

    await model.loadProjectData(5)

    expect(model.basicForm.name).toBe('老项目')
  })
})
