import { describe, expect, it } from 'vitest'
import { useProjectCreateModel } from './useProjectCreateModel.js'

function createModel(overrides = {}) {
  return useProjectCreateModel({
    route: { query: {}, ...overrides.route },
    userStore: { currentUser: { id: 9, name: '小王' }, ...overrides.userStore },
    projectStore: {},
    router: { push: () => {} },
  })
}

describe('useProjectCreateModel task payloads', () => {
  it('normalizes valid manual task rows for real project task API', () => {
    const model = createModel()
    model.taskForm.tasks = [
      { name: '  商务响应文件  ', owner: '张经理', deadline: '2026-06-01', priority: 'high', status: 'TODO' },
      { name: '', owner: '小王', deadline: '', priority: 'medium', status: 'TODO' },
      { name: '技术方案', owner: '', deadline: '', priority: 'low', status: 'TODO' },
    ]

    expect(model.buildTaskCreatePayloads()).toEqual([
      {
        title: '商务响应文件',
        description: '',
        assigneeName: '张经理',
        priority: 'HIGH',
        dueDate: '2026-06-01T00:00:00',
      },
      {
        title: '技术方案',
        description: '',
        assigneeName: '',
        priority: 'LOW',
        dueDate: null,
      },
    ])
  })

  it('does not emit empty placeholder task as a real task', () => {
    const model = createModel()

    expect(model.buildTaskCreatePayloads()).toEqual([])
  })
})
