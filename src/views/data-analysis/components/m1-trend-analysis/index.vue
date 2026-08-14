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
import { ref, onMounted, watch } from 'vue'
import { useFilterSearch } from './composables/useFilterSearch.js'
import { useDrillDown } from './composables/useDrillDown.js'
import { dashboardApi } from '@/api'
import { notifyErrorUnlessRateLimit } from '@/api/error-utils.js'
import FilterBar from './FilterBar.vue'
import TrendChart from './TrendChart.vue'
import DrillModal from './DrillModal.vue'

// PRD §6.2 M1 接收父组件全局日期范围，用于趋势查询的 startDate/endDate
const props = defineProps({
  dateRange: { type: Array, default: null }
})

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
const currentXAxisDimensions = ref([])

// PRD 6.3 X 轴判断优先级
const resolveXAxis = (dims) => {
  if (!dims || dims.length === 0) return 'time'
  if (dims.includes('dept') && dims.includes('person')) return 'person'
  if (dims.includes('dept')) return 'dept'
  if (dims.includes('person')) return 'person'
  return dims[0]
}

// 日期格式化
const formatDateStr = (date) => {
  if (!date) return null
  if (typeof date === 'string') return date.slice(0, 10)
  const d = new Date(date)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

// 筛选字段 → 后端 API 参数映射（PRD 6.7）
const buildApiParams = (filters, xAxis) => {
  const f = filters || {}
  return {
    xAxis,
    startDate: formatDateStr(props.dateRange?.[0]),
    endDate: formatDateStr(props.dateRange?.[1]),
    ...(f.timeDimension ? { timeDimension: f.timeDimension } : {}),
    ...(f.departments?.length ? { departmentIds: f.departments.join(',') } : {}),
    ...(f.persons?.length ? { userIds: f.persons.join(',') } : {}),
    ...(f.regions?.length ? { regionIds: f.regions.join(',') } : {}),
    ...(f.customerTypes?.length ? { customerTypes: f.customerTypes.join(',') } : {}),
    ...(f.projectTypes?.length ? { projectTypes: f.projectTypes.join(',') } : {}),
    ...(f.projectStatuses?.length ? { statuses: f.projectStatuses.join(',') } : {}),
    ...(f.tenderSubjects?.length ? { tenderEntities: f.tenderSubjects.join(',') } : {}),
    ...(f.competitors?.length ? { competitorNames: f.competitors.join(',') } : {})
  }
}

// 后端返回数据 → 图表数据格式适配
const adaptTrendData = (raw) => {
  if (!raw) return []
  const categories = raw.categories || []
  const bids = raw.bids || raw.bidSeries || []
  const wins = raw.wins || raw.winSeries || []
  const rate = raw.rate || raw.winRateSeries || []
  return categories.map((cat, i) => ({
    label: cat,
    bidCount: bids[i] ?? 0,
    winCount: wins[i] ?? 0,
    winRate: rate[i] ?? 0
  }))
}

const loadTrendData = async () => {
  chartLoading.value = true
  chartError.value = ''
  try {
    const params = buildApiParams(currentFilters.value, currentXAxisType.value)
    const response = await dashboardApi.getTrendsWithFilters(params)
    if (!response?.success) {
      throw new Error(response?.msg || '加载趋势数据失败')
    }
    trendData.value = adaptTrendData(response.data)
  } catch (error) {
    chartError.value = error?.message || '数据加载失败，请稍后重试'
    trendData.value = []
    notifyErrorUnlessRateLimit(error, '趋势数据加载失败')
  } finally {
    chartLoading.value = false
  }
}

const handleFilterConfirm = async (payload) => {
  currentFilters.value = payload.filters
  currentXAxisDimensions.value = payload.xAxisDimensions || []
  currentXAxisType.value = resolveXAxis(currentXAxisDimensions.value)
  await loadTrendData()
}

const handleFilterReset = () => {
  currentFilters.value = null
  currentXAxisDimensions.value = []
  currentXAxisType.value = 'time'
  trendData.value = []
  chartError.value = ''
}

// PRD 6.4 部门-人员联动：部门变化时清空人员选项，用户重新搜索
const handleDepartmentChange = () => {
  personOptions.value = []
}

const handleBarClick = (params) => {
  openDrill(params.data, currentXAxisType.value, params.seriesName, currentFilters.value, props.dateRange)
}

// PRD §6.2 全局日期范围变化时自动重新加载趋势数据
watch(() => props.dateRange, () => {
  loadTrendData()
}, { deep: true })

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
