import { ref } from 'vue'
import { dashboardApi } from '@/api'

/**
 * PRD §6.2 M1 筛选区下拉选项加载。
 * - 初始化时调用 /api/analytics/filter-options 一次性加载部门/人员/区域/招标主体选项
 * - 客户类型、项目类型、竞品公司由前端常量提供，不在此加载
 * - 部门-人员联动：部门选值变化时调用 /api/analytics/filter-options/persons 刷新人员下拉
 * - 各 FilterSelect 内部通过 visibleOptions computed 做本地搜索过滤，无需远程搜索
 */
export function useFilterSearch() {
  const departmentOptions = ref([])
  const personOptions = ref([])
  const tenderSubjectOptions = ref([])

  const loadingDepartments = ref(false)
  const loadingPersons = ref(false)
  const loadingTenderSubjects = ref(false)

  // 后端返回 { label, value, count? } → 前端 { label, value }
  function mapOptions(items) {
    return (items || []).map((item) => ({
      label: item.label ?? item.name ?? item.value,
      value: item.value ?? item.id ?? item.label
    }))
  }

  /**
   * PRD §6.2 一次性加载全部维度选项（onMounted 调用）。
   * 项目状态、客户类型、项目类型、竞品公司、区域由前端常量提供，不在此加载。
   */
  async function loadAllFilterOptions() {
    loadingDepartments.value = true
    loadingPersons.value = true
    loadingTenderSubjects.value = true
    try {
      const res = await dashboardApi.getFilterOptions()
      if (res?.success && res.data) {
        const d = res.data
        departmentOptions.value = mapOptions(d.department)
        personOptions.value = mapOptions(d.person)
        tenderSubjectOptions.value = mapOptions(d.tenderEntity)
      }
    } catch {
      /* silent — 下拉为空，用户可重试 */
    } finally {
      loadingDepartments.value = false
      loadingPersons.value = false
      loadingTenderSubjects.value = false
    }
  }

  /**
   * PRD 6.4 部门-人员联动：根据已选部门名称列表刷新人员下拉选项。
   * departmentNames 为空数组或 null 时返回全部人员。
   */
  async function refreshPersonsByDepartments(departmentNames) {
    loadingPersons.value = true
    try {
      const res = await dashboardApi.getPersonsByDepartments(departmentNames || [])
      if (res?.success && Array.isArray(res.data)) {
        personOptions.value = mapOptions(res.data)
      }
    } catch {
      /* silent */
    } finally {
      loadingPersons.value = false
    }
  }

  return {
    departmentOptions, personOptions,
    tenderSubjectOptions,
    loadingDepartments, loadingPersons,
    loadingTenderSubjects,
    loadAllFilterOptions,
    refreshPersonsByDepartments
  }
}
