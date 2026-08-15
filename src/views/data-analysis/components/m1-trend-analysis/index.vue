<template>
  <div class="m1-trend-analysis">
    <FilterBar
      :department-options="departmentOptions"
      :person-options="personOptions"
      :tender-subject-options="tenderSubjectOptions"
      :loading-departments="loadingDepartments"
      :loading-persons="loadingPersons"
      :loading-tender-subjects="loadingTenderSubjects"
      @confirm="handleFilterConfirm"
      @reset="handleFilterReset"
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
  departmentOptions, personOptions,
  tenderSubjectOptions,
  loadingDepartments, loadingPersons,
  loadingTenderSubjects,
  loadAllFilterOptions,
  refreshPersonsByDepartments
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
  const isTimeAxis = !xAxis || xAxis === 'time'
  return {
    xAxis,
    startDate: formatDateStr(props.dateRange?.[0]),
    endDate: formatDateStr(props.dateRange?.[1]),
    // timeDimension 仅当 X 轴为时间维度时发送
    ...(isTimeAxis && f.timeDimension ? { timeDimension: f.timeDimension } : {}),
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

// PRD 6.4 部门-人员联动：部门选值变化时按已选部门刷新人员下拉选项
// 同时清空已选但不在新部门范围内的人员（PRD 6.4 "已选但不再在过滤范围内的人员：自动清除"）
const handleDepartmentChange = async (departments) => {
  await refreshPersonsByDepartments(departments || [])
  if (currentFilters.value?.persons?.length) {
    // 清空人员选值，避免保留不在新部门范围内的人员
    currentFilters.value.persons = []
  }
}

const handleBarClick = (params) => {
  openDrill(params.data, currentXAxisType.value, params.seriesName, currentFilters.value, props.dateRange)
}

// PRD §6.2 全局日期范围变化时自动重新加载趋势数据
watch(() => props.dateRange, () => {
  loadTrendData()
}, { deep: true })

onMounted(async () => {
  // PRD §6.2 一次性加载所有筛选维度选项
  await loadAllFilterOptions()
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
