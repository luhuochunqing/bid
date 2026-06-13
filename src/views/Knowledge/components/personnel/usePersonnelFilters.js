import { ref, reactive, watch } from 'vue'
import { ElMessage } from 'element-plus'

export function usePersonnelFilters(personnelApi) {
  const records = ref([])
  const loading = ref(false)

  const filters = reactive({
    keyword: '',
    status: '',
    gender: '',
    highestEducations: [],
    studyForms: [],
    majorKeyword: '',
    entryDateFrom: null,
    entryDateTo: null,
    certificateKeyword: '',
    certificateStatuses: []
  })

  const entryDateRange = ref(null)

  let searchTimer = null

  function debouncedLoad(delay = 350) {
    clearTimeout(searchTimer)
    searchTimer = setTimeout(() => loadData(), delay)
  }

  async function loadData() {
    loading.value = true
    try {
      const { data } = await personnelApi.getList(filters)
      records.value = data || []
    } catch {
      ElMessage.error('加载失败')
    } finally {
      loading.value = false
    }
  }

  function onEntryDateRangeChange(val) {
    if (Array.isArray(val) && val.length === 2) {
      filters.entryDateFrom = val[0]
      filters.entryDateTo = val[1]
    } else {
      filters.entryDateFrom = null
      filters.entryDateTo = null
    }
    loadData()
  }

  function resetFilters() {
    Object.assign(filters, {
      keyword: '',
      status: '',
      gender: '',
      highestEducations: [],
      studyForms: [],
      majorKeyword: '',
      entryDateFrom: null,
      entryDateTo: null,
      certificateKeyword: '',
      certificateStatuses: []
    })
    entryDateRange.value = null
    loadData()
  }

  watch(
    () => [
      filters.keyword,
      filters.majorKeyword,
      filters.certificateKeyword,
      filters.gender,
      filters.status,
      filters.highestEducations,
      filters.studyForms,
      filters.certificateStatuses,
      filters.entryDateFrom,
      filters.entryDateTo
    ],
    () => {
      const isText = filters.keyword || filters.majorKeyword || filters.certificateKeyword
      if (isText && (filters.keyword?.length > 0 || filters.majorKeyword?.length > 0 || filters.certificateKeyword?.length > 0)) {
        debouncedLoad(320)
      } else {
        loadData()
      }
    },
    { deep: true }
  )

  return {
    records,
    loading,
    filters,
    entryDateRange,
    loadData,
    debouncedLoad,
    resetFilters,
    onEntryDateRangeChange
  }
}
