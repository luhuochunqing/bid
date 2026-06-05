import { computed, ref } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import { useWorkbenchMetrics } from '@/views/Dashboard/useWorkbenchMetrics.js'

describe('useWorkbenchMetrics', () => {
  it('loads dashboard summary and exposes admin metrics', async () => {
    const api = {
      getSummary: vi.fn().mockResolvedValue({
        success: true,
        data: {
          totalTenders: 12,
          activeProjects: 3,
          totalBudget: 2500000,
          successRate: 66.6,
        },
      }),
    }

    const metrics = useWorkbenchMetrics({
      api,
      currentUserRoleRef: ref('admin'),
      icons: { TrendCharts: 'trend', Flag: 'flag', Document: 'doc', Briefcase: 'briefcase' },
    })

    await metrics.loadWorkbenchSummary()

    expect(api.getSummary).toHaveBeenCalledTimes(1)
    expect(metrics.summaryStats.value.activeProjects).toBe(3)
    expect(metrics.metrics.value.map((item) => item.value)).toEqual(['¥250万', '66.6%', '12条', '3个'])
  })

  it('uses dependent count refs for manager and staff metrics', () => {
    const role = ref('manager')
    const pendingCount = computed(() => 5)
    const approvalCount = ref(2)
    const projectCount = ref(4)
    const completedCount = ref(7)

    const metrics = useWorkbenchMetrics({
      api: { getSummary: vi.fn() },
      currentUserRoleRef: role,
      pendingCountRef: pendingCount,
      pendingApprovalsTotalCountRef: approvalCount,
      myProjectCountRef: projectCount,
      completedTodoCountRef: completedCount,
    })

    expect(metrics.metrics.value.map((item) => item.value)).toContain('5项')
    expect(metrics.metrics.value.map((item) => item.value)).toContain('2项')

    role.value = 'staff'
    expect(metrics.metrics.value.map((item) => item.value)).toContain('7项')
    expect(metrics.metrics.value.map((item) => item.value)).toContain('4个')
  })

  it('falls back to null summary when summary API rejects', async () => {
    const metrics = useWorkbenchMetrics({
      api: { getSummary: vi.fn().mockRejectedValue(new Error('network')) },
    })

    await expect(metrics.loadWorkbenchSummary()).resolves.toBeNull()
    expect(metrics.summaryStats.value).toBeNull()
  })

  it('routes known metric drilldowns and shows info for unknown metrics', () => {
    const router = { push: vi.fn() }
    const message = { info: vi.fn() }
    const metrics = useWorkbenchMetrics({ router, message })

    metrics.handleMetricClick({ key: 'myProjects', label: '负责项目' })
    metrics.handleMetricClick({ key: 'unknown', label: '未知指标' })

    expect(router.push).toHaveBeenCalledWith('/project')
    expect(message.info).toHaveBeenCalledWith('未知指标 暂无详情页')
  })
})
