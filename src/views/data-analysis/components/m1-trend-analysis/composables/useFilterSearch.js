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

  // 后端返回 { label, value, count?, children? } → 前端 { label, value, children? }
  function mapOptions(items) {
    return (items || []).map((item) => {
      const node = {
        label: item.label ?? item.name ?? item.value,
        value: item.value ?? item.id ?? item.label
      }
      if (item.children && Array.isArray(item.children) && item.children.length > 0) {
        node.children = mapOptions(item.children)
      }
      return node
    })
  }

  /**
   * PRD §6.2 一次性加载全部维度选项（onMounted 调用）。
   * 项目状态、客户类型、项目类型、竞品公司、区域由前端常量提供，不在此加载。
   */
  async function loadAllFilterOptions() {
    loadingDepartments.value = true
    loadingTenderSubjects.value = true
    try {
      const res = await dashboardApi.getFilterOptions()
      if (res?.success && res.data) {
        const d = res.data
        departmentOptions.value = mapOptions(d.department)
        personOptions.value = mapOptions(d.person)
        tenderSubjectOptions.value = mapOptions(d.tenderEntity)
      }
    } catch (err) {
      console.error('[useFilterSearch] Failed to load all filter options:', err)
    } finally {
      loadingDepartments.value = false
      loadingTenderSubjects.value = false
    }
  }

  /**
   * PRD §6.4 部门-人员联动：根据已选部门名称列表刷新人员下拉选项。
   */
  async function refreshPersonsByDepartments(departmentNames) {
    loadingPersons.value = true
    try {
      const res = await dashboardApi.getPersonsByDepartments(departmentNames || [])
      if (res?.success && Array.isArray(res.data)) {
        personOptions.value = mapOptions(res.data)
      }
    } catch (err) {
      console.error('[useFilterSearch] Failed to refresh persons by departments:', err)
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
