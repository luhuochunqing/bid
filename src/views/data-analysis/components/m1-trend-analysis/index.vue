<template>
  <div class="m1-trend-analysis">
    <FilterBar
      :department-options="departmentOptions"
      :person-options="personOptions"
      :region-options="regionOptions"
      :customer-type-options="customerTypeOptions"
      :project-type-options="projectTypeOptions"
      :tender-subject-options="tenderSubjectOptions"
      :competitor-options="competitorOptions"
      :loading-departments="loadingDepartments"
      :loading-persons="loadingPersons"
      :loading-regions="loadingRegions"
      :loading-customer-types="loadingCustomerTypes"
      :loading-project-types="loadingProjectTypes"
      :loading-tender-subjects="loadingTenderSubjects"
      :loading-competitors="loadingCompetitors"
      @confirm="handleFilterConfirm"
      @reset="handleFilterReset"
      @search-department="searchDepartment"
      @search-person="searchPerson"
      @search-region="searchRegion"
      @search-customer-type="searchCustomerType"
      @search-project-type="searchProjectType"
      @search-project-status="searchProjectStatus"
      @search-tender-subject="searchTenderSubject"
      @search-competitor="searchCompetitor"
      @department-change="handleDepartmentChange"
    />

    <TrendChart
      :data="trendData"
      :x-axis-type="currentXAxisType"
      :loading="chartLoading"
      :error="chartError"
      @bar-click="handleBarClick"
      @retry="loadTrendData"
    />

    <DrillModal
      v-model="drillVisible"
      :loading="drillLoading"
      :title="drillTitle"
      :items="drillItems"
      :summary="drillSummary"
      :pagination="drillPagination"
      @page-change="changePage"
      @navigate-project="navigateProject"
    />
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useFilterSearch } from './composables/useFilterSearch.js'
import { useDrillDown } from './composables/useDrillDown.js'
import { dashboardApi } from '@/api'
import { notifyErrorUnlessRateLimit } from '@/api/error-utils.js'
import FilterBar from './FilterBar.vue'
import TrendChart from './TrendChart.vue'
import DrillModal from './DrillModal.vue'

const {
  departmentOptions, personOptions, regionOptions,
  customerTypeOptions, projectTypeOptions, tenderSubjectOptions, competitorOptions,
  loadingDepartments, loadingPersons, loadingRegions,
  loadingCustomerTypes, loadingProjectTypes, loadingTenderSubjects, loadingCompetitors,
  searchDepartment, searchPerson, searchRegion,
  searchCustomerType, searchProjectType, searchProjectStatus,
  searchTenderSubject, searchCompetitor
} = useFilterSearch()

const {
  drillVisible, drillLoading, drillTitle,
  drillItems, drillSummary, drillPagination,
  openDrill, changePage, navigateProject
} = useDrillDown()

const chartLoading = ref(false)
const chartError = ref('')
const trendData = ref([])
const currentFilters = ref(null)
const currentXAxisType = ref('time')

const chartEmpty = computed(() => {
  return !chartLoading.value && !chartError.value && trendData.value.length === 0
})

const handleDepartmentChange = () => {
  personOptions.value = []
}

const handleFilterConfirm = async (payload) => {
  currentFilters.value = payload.filters
  currentXAxisType.value = payload.xAxisDimensions[0] || 'time'
  await loadTrendData()
}

const handleFilterReset = () => {
  currentFilters.value = null
  currentXAxisType.value = 'time'
  trendData.value = []
  chartError.value = ''
}

const loadTrendData = async () => {
  chartLoading.value = true
  chartError.value = ''

  try {
    const params = {
      xAxis: currentXAxisType.value,
      ...(currentFilters.value?.timeDimension ? { timeDimension: currentFilters.value.timeDimension } : {}),
      ...(currentFilters.value?.departments?.length ? { departments: currentFilters.value.departments.join(',') } : {}),
      ...(currentFilters.value?.persons?.length ? { persons: currentFilters.value.persons.join(',') } : {}),
      ...(currentFilters.value?.regions?.length ? { regions: currentFilters.value.regions.join(',') } : {}),
      ...(currentFilters.value?.customerTypes?.length ? { customerTypes: currentFilters.value.customerTypes.join(',') } : {}),
      ...(currentFilters.value?.projectTypes?.length ? { projectTypes: currentFilters.value.projectTypes.join(',') } : {}),
      ...(currentFilters.value?.projectStatuses?.length ? { projectStatuses: currentFilters.value.projectStatuses.join(',') } : {}),
      ...(currentFilters.value?.tenderSubjects?.length ? { tenderSubjects: currentFilters.value.tenderSubjects.join(',') } : {}),
      ...(currentFilters.value?.competitors?.length ? { competitors: currentFilters.value.competitors.join(',') } : {})
    }

    const response = await dashboardApi.getTrendsWithFilters(params)
    if (!response?.success) {
      throw new Error(response?.msg || '加载趋势数据失败')
    }

    const data = response.data || []
    trendData.value = Array.isArray(data) ? data : []
  } catch (error) {
    chartError.value = error?.message || '数据加载失败，请稍后重试'
    trendData.value = []
    notifyErrorUnlessRateLimit(error, '趋势数据加载失败')
  } finally {
    chartLoading.value = false
  }
}

const handleBarClick = (params) => {
  openDrill(params.data, currentXAxisType.value)
}

onMounted(async () => {
  await loadTrendData()
})
</script>

<style scoped>
.m1-trend-analysis {
  padding: 0;
}

.m1-trend-analysis > * + * {
  margin-top: 16px;
}
</style>