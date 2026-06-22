// Input: dashboard summary API, role/count refs, router/message deps
// Output: workbench metrics state/actions composable for Dashboard Workbench
// Pos: src/views/Dashboard/ - dashboard feature composables
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { computed, ref } from 'vue'
import { dashboardApi as defaultDashboardApi } from '@/api'
import { getMetricRouteTarget, getRoleMetrics } from '@/views/Dashboard/workbench-core.js'
import { hasAnyPermission } from '@/utils/permission'

const noopMessage = {
  info: () => {},
}

const readRef = (source, fallback) => source?.value ?? fallback

export function useWorkbenchMetrics({
  api = defaultDashboardApi,
  router,
  message = noopMessage,
  currentUserRoleRef,
  pendingCountRef,
  pendingApprovalsTotalCountRef,
  myProjectCountRef,
  completedTodoCountRef,
  icons = {},
  menuPermissionsRef,
} = {}) {
  const summaryStats = ref(null)
  const metricsLoading = ref(true)
  const metricsError = ref('')

  const metricContext = computed(() => ({
    summaryStats: summaryStats.value,
    myProjectCount: readRef(myProjectCountRef, 0),
    pendingCount: readRef(pendingCountRef, 0),
    pendingApprovalsTotalCount: readRef(pendingApprovalsTotalCountRef, 0),
    completedTodoCount: readRef(completedTodoCountRef, 0),
  }))

  const iconize = (metric) => ({
    ...metric,
    icon: icons[metric.icon] || metric.icon,
  })

  const canLoadSummary = computed(() =>
    hasAnyPermission(menuPermissionsRef?.value || [], ['analytics', 'dashboard:view_metric_cards'])
  )

  const loadWorkbenchSummary = async () => {
    if (!canLoadSummary.value) return null
    metricsError.value = ''
    try {
      const result = await api.getSummary()
      if (result?.success) {
        summaryStats.value = result.data
      } else {
        metricsError.value = result?.msg || '指标数据暂时不可用'
      }
      return summaryStats.value
    } catch {
      summaryStats.value = null
      metricsError.value = '指标数据暂时不可用，请稍后重试'
      return null
    }
  }

  const adminMetrics = computed(() => getRoleMetrics('admin', metricContext.value).map(iconize))
  const biddingMetrics = computed(() => getRoleMetrics('manager', metricContext.value).map(iconize))
  const staffMetrics = computed(() => getRoleMetrics('staff', metricContext.value).map(iconize))

  const metrics = computed(() => {
    const role = readRef(currentUserRoleRef, 'staff')
    return getRoleMetrics(role, metricContext.value).map(iconize)
  })

  const handleMetricClick = (item) => {
    const targetRoute = getMetricRouteTarget(item)
    if (targetRoute) {
      router?.push(targetRoute)
      return
    }
    message.info?.(`${item?.label || '该指标'} 暂无详情页`)
  }

  return {
    summaryStats,
    metricsLoading,
    metricsError,
    canLoadSummary,
    adminMetrics,
    biddingMetrics,
    staffMetrics,
    metrics,
    loadWorkbenchSummary,
    handleMetricClick,
  }
}
