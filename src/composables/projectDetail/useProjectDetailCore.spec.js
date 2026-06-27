// Input: task status code (todo/review/done)
// Output: Element Plus tag type + 中文文案
// Pos: src/composables/projectDetail/ - CO-361 三态模型前端映射回归
//   锁定 useProjectDetailCore 导出的 getTaskStatusType/getTaskStatusText 三态映射，
//   防止 IN_PROGRESS/doing 重新混入或映射回退。

import { describe, expect, it, vi } from 'vitest'

// project-utils / workbench-formatters 仅在 getStatusType/getStatusText 路径引用，
// getTaskStatus* 是纯映射，mock 掉外部依赖即可隔离测试。
vi.mock('@/views/Project/project-utils.js', () => ({
  getProjectStatusText: () => '',
  getProjectStatusType: () => 'info',
}))
vi.mock('@/views/Dashboard/workbench-formatters.js', () => ({
  getPriorityType: () => '',
  getPriorityLabel: () => '',
}))

import { useProjectDetailCore } from '@/composables/projectDetail/useProjectDetailCore.js'

const ctx = () => ({
  router: { push: () => {} },
  route: { params: {} },
  project: { value: null },
  isDemoMode: false,
  assistantPanelVisible: { value: false },
  showCompetitionIntel: { value: false },
  showROIAnalysis: { value: false },
  showComplianceCheck: { value: false },
  showVersionControl: { value: false },
  showCollaboration: { value: false },
  showAutoTasks: { value: false },
  showMobileCard: { value: false },
  assetCheckResult: { value: null },
  message: { info: () => {} },
})

describe('useProjectDetailCore — CO-361 三态任务状态映射', () => {
  const { getTaskStatusType, getTaskStatusText } = useProjectDetailCore(ctx())

  describe('getTaskStatusType', () => {
    it('todo → info', () => {
      expect(getTaskStatusType('todo')).toBe('info')
    })
    it('review → warning（替代原 doing/in-progress）', () => {
      expect(getTaskStatusType('review')).toBe('warning')
    })
    it('done → success', () => {
      expect(getTaskStatusType('done')).toBe('success')
    })
    it('未知状态兜底 info', () => {
      expect(getTaskStatusType('whatever')).toBe('info')
    })
    it('doing 已下线，不再映射为 warning', () => {
      expect(getTaskStatusType('doing')).toBe('info')
    })
  })

  describe('getTaskStatusText', () => {
    it('todo → 待办', () => {
      expect(getTaskStatusText('todo')).toBe('待办')
    })
    it('review → 待审核', () => {
      expect(getTaskStatusText('review')).toBe('待审核')
    })
    it('done → 已完成', () => {
      expect(getTaskStatusText('done')).toBe('已完成')
    })
    it('未知状态原样返回', () => {
      expect(getTaskStatusText('whatever')).toBe('whatever')
    })
    it('doing 已下线，不再映射为 进行中', () => {
      expect(getTaskStatusText('doing')).toBe('doing')
    })
  })
})
