import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  dashboardApi,
  isFeatureUnavailableResponse,
  getFeaturePlaceholder
} from '@/api'
import { notifyFeatureUnavailable } from '@/utils/featureFeedback'

export const metricTypeByCardKey = {
  bids: 'projects',
  winRate: 'win-rate',
  amount: 'revenue',
  cost: 'projects'
}

export const metricTitleMap = {
  revenue: '中标金额明细',
  'win-rate': '中标率明细',
  team: '人员绩效明细',
  projects: '进行中项目明细'
}

export const metricDrillDownColumnMap = {
  revenue: [
    { key: 'title', label: '标讯名称', minWidth: 240 },
    { key: 'subtitle', label: '来源/区域', minWidth: 120 },
    { key: 'status', label: '状态', width: 120, type: 'status' },
    { key: 'ownerName', label: '关联项目', minWidth: 200 },
    { key: 'score', label: 'AI评分', width: 100 },
    { key: 'amount', label: '金额(万)', width: 120, type: 'amount' },
    { key: 'createdAt', label: '创建时间', minWidth: 140, type: 'datetime' },
    { key: 'deadline', label: '截止时间', minWidth: 140, type: 'datetime' }
  ],
  'win-rate': [
    { key: 'title', label: '标讯名称', minWidth: 220 },
    { key: 'subtitle', label: '关联项目', minWidth: 200 },
    { key: 'outcome', label: '结果', width: 120, type: 'status' },
    { key: 'ownerName', label: '负责人', width: 120 },
    { key: 'amount', label: '金额(万)', width: 120, type: 'amount' },
    { key: 'rate', label: '命中率', width: 100, type: 'rate' },
    { key: 'createdAt', label: '创建时间', minWidth: 140, type: 'datetime' }
  ],
  team: [
    { key: 'title', label: '成员', width: 120 },
    { key: 'subtitle', label: '邮箱/部门', minWidth: 180 },
    { key: 'role', label: '角色', width: 120, type: 'status' },
    { key: 'count', label: '参与项目', width: 100 },
    { key: 'wonCount', label: '中标项目', width: 100 },
    { key: 'activeProjectCount', label: '进行中项目', width: 110 },
    { key: 'managedProjectCount', label: '负责项目', width: 100 },
    { key: 'completedTaskCount', label: '已完成任务', width: 110 },
    { key: 'overdueTaskCount', label: '逾期任务', width: 100 },
    { key: 'taskCompletionRate', label: '任务完成率', width: 110, type: 'rate' },
    { key: 'rate', label: '中标率', width: 100, type: 'rate' },
    { key: 'score', label: '绩效分', width: 90 },
    { key: 'amount', label: '累计金额(万)', width: 140, type: 'amount' }
  ],
  projects: [
    { key: 'title', label: '项目名称', minWidth: 220 },
    { key: 'subtitle', label: '标讯/客户', minWidth: 220 },
    { key: 'status', label: '状态', width: 120, type: 'status' },
    { key: 'ownerName', label: '负责人', width: 120 },
    { key: 'teamSize', label: '团队规模', width: 100 },
    { key: 'amount', label: '预算(万)', width: 120, type: 'amount' },
    { key: 'createdAt', label: '开始时间', minWidth: 140, type: 'datetime' },
    { key: 'deadline', label: '截止时间', minWidth: 140, type: 'datetime' }
  ]
}

function buildEmptyResponse(size = 10) {
  return {
    items: [],
    summary: { totalCount: 0, totalAmount: 0 },
    filters: { dimensions: [] },
    pagination: { page: 1, size, total: 0, totalPages: 0, hasNext: false }
  }
}

export function useMetricDrillDown({ route, router, dateRange }) {
  const drawerVisible = ref(false)
  const drawerType = ref('')
  const drawerTitle = ref('')
  const loading = ref(false)
  const response = ref(null)
  const placeholder = ref(null)
  const filterValues = ref({})
  const paginationState = ref({ page: 1, size: 10 })

  const items = computed(() => response.value?.items || [])
  const summary = computed(() => response.value?.summary || {})
  const dimensions = computed(() => response.value?.filters?.dimensions || [])
  const pagination = computed(() => response.value?.pagination || paginationState.value)
  const columns = computed(() => metricDrillDownColumnMap[drawerType.value] || [])
  const hasRowAction = computed(() => ['revenue', 'win-rate', 'projects'].includes(drawerType.value))

  function buildParams() {
    const [startDate, endDate] = Array.isArray(dateRange.value) ? dateRange.value : []
    const params = { page: paginationState.value.page, size: paginationState.value.size }

    if (startDate) params.startDate = new Date(startDate).toISOString().slice(0, 10)
    if (endDate) params.endDate = new Date(endDate).toISOString().slice(0, 10)

    Object.entries(filterValues.value).forEach(([key, value]) => {
      if (value && value !== 'ALL') {
        if (key === 'status' && drawerType.value === 'projects') {
          params.status = value
          return
        }
        params[key] = value
      }
    })

    return params
  }

  async function openDrillDown(type, options = {}) {
    drawerType.value = type
    drawerTitle.value = metricTitleMap[type] || '明细'
    drawerVisible.value = true
    loading.value = true
    placeholder.value = null

    if (options.resetPaging !== false) {
      paginationState.value = { page: 1, size: paginationState.value.size || 10 }
    }
    if (options.filters) {
      filterValues.value = { ...options.filters }
    }

    try {
      const result = await dashboardApi.getDrillDown(type, buildParams())
      if (isFeatureUnavailableResponse(result)) {
        drawerVisible.value = false
        drawerType.value = ''
        drawerTitle.value = ''
        response.value = buildEmptyResponse(paginationState.value.size)
        ElMessage.info('当前版本暂不开放该分析明细')
        placeholder.value = notifyFeatureUnavailable(result, {
          fallback: {
            title: '下钻明细当前不可用',
            hint: '当前无法返回该指标的明细数据，请稍后重试或联系管理员检查分析服务。'
          }
        }) || getFeaturePlaceholder(result)
        return
      }
      if (!result?.success) {
        throw new Error(result?.msg || '加载下钻明细失败')
      }
      response.value = result.data

      const nextFilters = {}
      ;(result.data?.filters?.dimensions || []).forEach((dimension) => {
        nextFilters[dimension.key] = filterValues.value[dimension.key] || dimension.selectedValue || 'ALL'
      })
      filterValues.value = nextFilters
    } catch (error) {
      ElMessage.error(error?.message || '加载下钻明细失败')
    } finally {
      loading.value = false
    }
  }

  async function syncQuery(type, extraQuery = {}) {
    if (!type) return
    await router.replace({
      path: '/analytics/dashboard',
      query: { ...route.query, drilldown: type, ...extraQuery }
    })
  }

  async function clearQuery() {
    const nextQuery = { ...route.query }
    delete nextQuery.drilldown
    delete nextQuery.status
    delete nextQuery.role
    delete nextQuery.outcome
    await router.replace({ path: '/analytics/dashboard', query: nextQuery })
  }

  async function handleOverviewClick(metricKey) {
    const type = metricTypeByCardKey[metricKey]
    if (!type) return
    await syncQuery(type)
  }

  async function handleFilterChange(key, value) {
    filterValues.value = { ...filterValues.value, [key]: value }
    paginationState.value = { ...paginationState.value, page: 1 }

    const extraQuery = {}
    if (key === 'status') extraQuery.status = value === 'ALL' ? undefined : value
    if (key === 'role') extraQuery.role = value === 'ALL' ? undefined : value
    if (key === 'outcome') extraQuery.outcome = value === 'ALL' ? undefined : value
    await syncQuery(drawerType.value, extraQuery)
  }

  async function reload() {
    if (!drawerType.value) return
    await openDrillDown(drawerType.value, { resetPaging: false })
  }

  async function handlePageChange(page) {
    paginationState.value = { ...paginationState.value, page }
    await openDrillDown(drawerType.value, { resetPaging: false })
  }

  async function handleClose() {
    response.value = null
    placeholder.value = null
    drawerType.value = ''
    drawerTitle.value = ''
    filterValues.value = {}
    paginationState.value = { page: 1, size: 10 }
    await clearQuery()
  }

  function handleRowAction(row) {
    if (drawerType.value === 'projects') {
      router.push({ name: 'ProjectDetail', params: { id: row.id } })
      return
    }
    const projectId = row.relatedId || row.id
    if (projectId) {
      router.push({ name: 'ProjectDetail', params: { id: projectId } })
    }
  }

  return {
    drawerVisible,
    drawerType,
    drawerTitle,
    loading,
    placeholder,
    items,
    summary,
    dimensions,
    filterValues,
    pagination,
    columns,
    hasRowAction,
    paginationState,
    openDrillDown,
    handleOverviewClick,
    handleFilterChange,
    reload,
    handlePageChange,
    handleClose,
    handleRowAction
  }
}
