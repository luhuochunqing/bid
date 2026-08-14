import { ref } from 'vue'
import { dashboardApi } from '@/api'

/**
 * PRD §6.2 M1 筛选区下拉选项加载。
 * - 初始化时调用 /api/analytics/filter-options 一次性加载 7 个维度选项
 * - 部门-人员联动：部门选值变化时调用 /api/analytics/filter-options/persons 刷新人员下拉
 * - 各 FilterSelect 内部通过 visibleOptions computed 做本地搜索过滤，无需远程搜索
 */
export function useFilterSearch() {
  const departmentOptions = ref([])
  const personOptions = ref([])
  const regionOptions = ref([])
  const customerTypeOptions = ref([])
  const projectTypeOptions = ref([])
  const tenderSubjectOptions = ref([])
  const competitorOptions = ref([])

  const loadingDepartments = ref(false)
  const loadingPersons = ref(false)
  const loadingRegions = ref(false)
  const loadingCustomerTypes = ref(false)
  const loadingProjectTypes = ref(false)
  const loadingTenderSubjects = ref(false)
  const loadingCompetitors = ref(false)

  // 后端返回 { label, value, count? } → 前端 { label, value }
  function mapOptions(items) {
    return (items || []).map((item) => ({
      label: item.label ?? item.name ?? item.value,
      value: item.value ?? item.id ?? item.label
    }))
  }

  /**
   * PRD §6.2 一次性加载全部维度选项（onMounted 调用）。
   * 项目状态由前端 PROJECT_STATUS_OPTIONS 常量提供，不在此加载。
   */
  async function loadAllFilterOptions() {
    loadingDepartments.value = true
    loadingPersons.value = true
    loadingRegions.value = true
    loadingCustomerTypes.value = true
    loadingProjectTypes.value = true
    loadingTenderSubjects.value = true
    loadingCompetitors.value = true
    try {
      const res = await dashboardApi.getFilterOptions()
      if (res?.success && res.data) {
        const d = res.data
        departmentOptions.value = mapOptions(d.department)
        personOptions.value = mapOptions(d.person)
        regionOptions.value = mapOptions(d.region)
        customerTypeOptions.value = mapOptions(d.customerType)
        projectTypeOptions.value = mapOptions(d.projectType)
        tenderSubjectOptions.value = mapOptions(d.tenderEntity)
        competitorOptions.value = mapOptions(d.competitor)
      }
    } catch {
      /* silent — 下拉为空，用户可重试 */
    } finally {
      loadingDepartments.value = false
      loadingPersons.value = false
      loadingRegions.value = false
      loadingCustomerTypes.value = false
      loadingProjectTypes.value = false
      loadingTenderSubjects.value = false
      loadingCompetitors.value = false
    }
  }

  /**
   * PRD §6.4 部门-人员联动：根据已选部门名称列表刷新人员下拉选项。
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
    departmentOptions, personOptions, regionOptions,
    customerTypeOptions, projectTypeOptions, tenderSubjectOptions, competitorOptions,
    loadingDepartments, loadingPersons, loadingRegions,
    loadingCustomerTypes, loadingProjectTypes, loadingTenderSubjects, loadingCompetitors,
    loadAllFilterOptions,
    refreshPersonsByDepartments
  }
}
