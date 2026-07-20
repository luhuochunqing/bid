// Input: workbenchApi, userStore menuPermissions
// Output: deadline stats state, deadline items (list data), computed metrics, and load functions
// Pos: src/views/Dashboard/ - Dashboard feature composables
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { computed, ref } from 'vue'
import { workbenchApi as defaultWorkbenchApi } from '@/api'
import {
  buildDeadlinePanelsFromItems,
  normalizeDeadlineItems,
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

  // CO-593: 截止时间列表数据（按 period 拉取的真实条目，替代旧版基于日历事件的伪造数据）
  const deadlineItems = ref(null)
  const deadlineItemsLoading = ref(false)
  const deadlineItemsError = ref('')

  // CO-593: 请求竞态保护——Tab 快速切换时，旧请求返回若已被新请求取代则丢弃，
  // 避免 UI 出现"显示今天数据但 Tab 高亮在本月"的不一致
  let itemsRequestId = 0

  const deadlineMetrics = computed(() => {
    if (!deadlineStats.value) return []
    const perms = menuPermissionsRef?.value || []
    return selectDeadlineMetrics(perms, deadlineStats.value)
  })

  /**
   * 截止时间模块列表数据（按 period 映射为 UI 列结构）。
   * 形状：{ signup: [], opening: [], deposit: [] }（供 DeadlinePanels.vue 直接使用）
   */
  const deadlinePanels = computed(() => {
    if (!deadlineItems.value) return { signup: [], opening: [], deposit: [] }
    return buildDeadlinePanelsFromItems(deadlineItems.value)
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

  /**
   * CO-593: 拉取截止时间列表条目（按 period 时间窗过滤）。
   * @param {string} period 'today' | 'week' | 'month'
   */
  async function loadDeadlineItems(period = 'week') {
    const requestId = ++itemsRequestId
    deadlineItemsLoading.value = true
    deadlineItemsError.value = ''
    try {
      const response = await workbenchApi.getDeadlineItems(period)
      // 竞态保护：若期间又触发了新请求，本次结果丢弃
      if (requestId !== itemsRequestId) return
      if (response?.success) {
        deadlineItems.value = normalizeDeadlineItems(response.data || {})
      } else {
        deadlineItemsError.value = '截止时间数据暂时不可用'
      }
    } catch {
      if (requestId !== itemsRequestId) return
      deadlineItemsError.value = '截止时间数据暂时不可用，请稍后重试'
    } finally {
      if (requestId === itemsRequestId) {
        deadlineItemsLoading.value = false
      }
    }
  }

  return {
    deadlineStats,
    deadlineMetricsLoading,
    deadlineMetricsError,
    deadlineMetrics,
    loadDeadlineStats,
    // CO-593:
    deadlineItems,
    deadlineItemsLoading,
    deadlineItemsError,
    deadlinePanels,
    loadDeadlineItems,
  }
}
