// Input: workbenchApi, userStore menuPermissions
// Output: deadline stats state, computed metrics, and load function
// Pos: src/views/Dashboard/ - Dashboard feature composables
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { computed, ref } from 'vue'
import { workbenchApi as defaultWorkbenchApi } from '@/api'
import {
  normalizeDeadlineStats,
  selectDeadlineMetrics,
} from '@/views/Dashboard/workbench-deadline-core.js'

export function useWorkbenchDeadline({
  menuPermissionsRef,
  workbenchApi = defaultWorkbenchApi,
} = {}) {
  const deadlineStats = ref(null)
  const deadlineMetricsLoading = ref(false)
  const deadlineMetricsError = ref('')

  const deadlineMetrics = computed(() => {
    if (!deadlineStats.value) return []
    const perms = menuPermissionsRef?.value || []
    return selectDeadlineMetrics(perms, deadlineStats.value)
  })

  async function loadDeadlineStats() {
    deadlineMetricsLoading.value = true
    deadlineMetricsError.value = ''
    try {
      const response = await workbenchApi.getDeadlineStats()
      if (response?.success) {
        // P1 fix: normalize raw API payload to guard against null / missing fields /
        // string-typed numbers. selectDeadlineMetrics/buildMetrics rely on this shape.
        deadlineStats.value = normalizeDeadlineStats(response.data || {})
      } else {
        deadlineMetricsError.value = '截止节点数据暂时不可用'
      }
    } catch {
      deadlineMetricsError.value = '截止节点数据暂时不可用，请稍后重试'
    } finally {
      deadlineMetricsLoading.value = false
    }
  }

  return { deadlineStats, deadlineMetricsLoading, deadlineMetricsError, deadlineMetrics, loadDeadlineStats }
}
