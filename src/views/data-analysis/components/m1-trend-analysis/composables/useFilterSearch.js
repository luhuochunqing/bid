import { ref } from 'vue'
import { dashboardApi } from '@/api'

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

  function mapOptions(items) {
    return (items || []).map((item) => ({
      label: item.name || item.label,
      value: item.id ?? item.value
    }))
  }

  const searchDepartment = async (query) => {
    loadingDepartments.value = true
    try {
      const res = await dashboardApi.searchOptions('department', query)
      if (res?.success) departmentOptions.value = mapOptions(res.data)
    } catch { /* silent */ }
    finally { loadingDepartments.value = false }
  }

  const searchPerson = async (query) => {
    loadingPersons.value = true
    try {
      const res = await dashboardApi.searchOptions('person', query)
      if (res?.success) personOptions.value = mapOptions(res.data)
    } catch { /* silent */ }
    finally { loadingPersons.value = false }
  }

  const searchRegion = async (query) => {
    loadingRegions.value = true
    try {
      const res = await dashboardApi.searchOptions('region', query)
      if (res?.success) regionOptions.value = mapOptions(res.data)
    } catch { /* silent */ }
    finally { loadingRegions.value = false }
  }

  const searchCustomerType = async (query) => {
    loadingCustomerTypes.value = true
    try {
      const res = await dashboardApi.searchOptions('customerType', query)
      if (res?.success) customerTypeOptions.value = mapOptions(res.data)
    } catch { /* silent */ }
    finally { loadingCustomerTypes.value = false }
  }

  const searchProjectType = async (query) => {
    loadingProjectTypes.value = true
    try {
      const res = await dashboardApi.searchOptions('projectType', query)
      if (res?.success) projectTypeOptions.value = mapOptions(res.data)
    } catch { /* silent */ }
    finally { loadingProjectTypes.value = false }
  }

  const searchProjectStatus = async () => { /* 固化的8种状态，无需远程搜索 */ }

  const searchTenderSubject = async (query) => {
    loadingTenderSubjects.value = true
    try {
      const res = await dashboardApi.searchOptions('tenderSubject', query)
      if (res?.success) tenderSubjectOptions.value = mapOptions(res.data)
    } catch { /* silent */ }
    finally { loadingTenderSubjects.value = false }
  }

  const searchCompetitor = async (query) => {
    loadingCompetitors.value = true
    try {
      const res = await dashboardApi.searchOptions('competitor', query)
      if (res?.success) competitorOptions.value = mapOptions(res.data)
    } catch { /* silent */ }
    finally { loadingCompetitors.value = false }
  }

  return {
    departmentOptions, personOptions, regionOptions,
    customerTypeOptions, projectTypeOptions, tenderSubjectOptions, competitorOptions,
    loadingDepartments, loadingPersons, loadingRegions,
    loadingCustomerTypes, loadingProjectTypes, loadingTenderSubjects, loadingCompetitors,
    searchDepartment, searchPerson, searchRegion,
    searchCustomerType, searchProjectType, searchProjectStatus,
    searchTenderSubject, searchCompetitor
  }
}