import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { dashboardApi } from '@/api'
import { PROJECT_STATUS_COLORS } from '../components/m1-trend-analysis/filterConstants.js'

export function useAnalyticsData() {
  const defaultDateRange = () => [new Date('2026-01-01'), new Date('2026-12-31')]
  const globalDateRange = ref(defaultDateRange())
  const m4DateRange = ref(defaultDateRange())

  const initialLoading = ref(true)
  const refreshing = ref(false)

  const m0Loading = ref(false)
  const m0Error = ref(false)
  const m1Loading = ref(false)
  const m1ChartLoading = ref(false)
  const m1Error = ref(false)
  const m2Loading = ref(false)
  const m2Error = ref(false)
  const m3Loading = ref(false)
  const m3Error = ref(false)
  const m4Loading = ref(false)
  const m4Error = ref(false)
  const trendDrillLoading = ref(false)

  const kpiCards = ref([])
  const customerTypeData = ref([])
  const projectTypeData = ref([])
  const competitorData = ref([])
  const trendDrillData = ref([])
  const trendChartOption = ref({})

  const trendFilters = reactive({
    timeDimension: 'month',
    departments: [],
    persons: [],
    regions: [],
    customerTypes: [],
    projectTypes: [],
    projectStatuses: [],
    tenderEntities: [],
    competitors: []
  })

  function formatAmount(val) {
    if (val == null) return '--'
    const num = Number(val)
    if (isNaN(num)) return '--'
    if (num >= 10000) {
      return (num / 10000).toFixed(1) + '万'
    }
    return num.toLocaleString()
  }

  function buildTrendChartOption(data, xAxisType) {
    const isStatusAxis = xAxisType === 'projectStatus'
    const categories = Array.isArray(data) ? data.map((d) => d.period || d.month || '-') : []
    const bidData = Array.isArray(data) ? data.map((d) => Number(d.count || d.bids || 0)) : []
    const winData = Array.isArray(data) ? data.map((d) => Number(d.wins || 0)) : []
    const rateData = Array.isArray(data) ? data.map((d) => Number(d.rate || d.changePercentage || 0)) : []

    if (isStatusAxis) {
      const STATUS_FALLBACK = '#2563EB'
      return {
        tooltip: { trigger: 'axis' },
        legend: { data: ['数量'] },
        xAxis: { type: 'category', data: categories },
        yAxis: { type: 'value', name: '数量' },
        series: [{
          name: '数量', type: 'bar', data: bidData,
          itemStyle: {
            color: (params) => PROJECT_STATUS_COLORS[params.name] || STATUS_FALLBACK
          }
        }]
      }
    }

    return {
      tooltip: { trigger: 'axis' },
      legend: { data: ['投标数', '中标数', '中标率'] },
      xAxis: { type: 'category', data: categories },
      yAxis: [
        { type: 'value', name: '数量' },
        { type: 'value', name: '比率', min: 0, max: 100 }
      ],
      series: [
        { name: '投标数', type: 'bar', data: bidData },
        { name: '中标数', type: 'bar', data: winData },
        { name: '中标率', type: 'line', yAxisIndex: 1, data: rateData }
      ]
    }
  }

  function formatDateStr(date) {
    if (!date) return null
    if (typeof date === 'string') return date.slice(0, 10)
    const d = new Date(date)
    const y = d.getFullYear()
    const m = String(d.getMonth() + 1).padStart(2, '0')
    const day = String(d.getDate()).padStart(2, '0')
    return `${y}-${m}-${day}`
  }

  async function loadM0Data() {
    m0Loading.value = true
    m0Error.value = false
    try {
      const dateStart = formatDateStr(globalDateRange.value?.[0])
      const dateEnd = formatDateStr(globalDateRange.value?.[1])
      const res = await dashboardApi.getEnhancedOverview(dateStart, dateEnd)
      const d = res?.data || {}
      const totalCount = Number(d.totalCount ?? 0)
      const biddingCount = Number(d.biddingCount ?? 0)
      const wonCount = Number(d.wonCount ?? 0)
      const winRate = d.winRate != null ? Number(d.winRate) : 0
      // PRD §5.3 接口不返回 change/todayNew 字段，trendText 留空不显示
      kpiCards.value = [
        { key: 'totalCount', label: '投标总数', value: String(totalCount), unit: '个', foot: '投标项目总数', trendText: '', trendDirection: '', colorClass: 'kpi-blue' },
        { key: 'biddingCount', label: '投标中', value: String(biddingCount), unit: '个', foot: '项目状态为投标中', trendText: '', trendDirection: '', colorClass: 'kpi-green' },
        { key: 'wonCount', label: '中标数', value: String(wonCount), unit: '个', foot: '项目状态为已中标', trendText: '', trendDirection: '', colorClass: 'kpi-orange' },
        { key: 'winRate', label: '中标率', value: winRate.toFixed(1), unit: '%', foot: '中标数 / 投标数', trendText: '', trendDirection: '', colorClass: 'kpi-purple' }
      ]
    } catch (e) {
      console.error('[M0] 加载KPI失败:', e)
      // PRD §5.4 接口失败：卡片数值区域显示「—」，不设 error 标志（有 fallback 数据）
      kpiCards.value = [
        { key: 'totalCount', label: '投标总数', value: '—', unit: '', foot: '投标项目总数', trendText: '', trendDirection: '', colorClass: 'kpi-blue' },
        { key: 'biddingCount', label: '投标中', value: '—', unit: '', foot: '项目状态为投标中', trendText: '', trendDirection: '', colorClass: 'kpi-green' },
        { key: 'wonCount', label: '中标数', value: '—', unit: '', foot: '项目状态为已中标', trendText: '', trendDirection: '', colorClass: 'kpi-orange' },
        { key: 'winRate', label: '中标率', value: '—', unit: '', foot: '中标数 / 投标数', trendText: '', trendDirection: '', colorClass: 'kpi-purple' }
      ]
    } finally {
      m0Loading.value = false
    }
  }

  async function loadM1Data() {
    m1Loading.value = true
    m1ChartLoading.value = true
    m1Error.value = false
    try {
      const params = {
        xAxis: 'time',
        startDate: formatDateStr(globalDateRange.value?.[0]),
        endDate: formatDateStr(globalDateRange.value?.[1]),
        timeDimension: trendFilters.timeDimension,
        departmentIds: trendFilters.departments.join(',') || undefined,
        userIds: trendFilters.persons.join(',') || undefined,
        regionIds: trendFilters.regions.join(',') || undefined,
        customerTypes: trendFilters.customerTypes.join(',') || undefined,
        projectTypes: trendFilters.projectTypes.join(',') || undefined,
        statuses: trendFilters.projectStatuses.join(',') || undefined,
        tenderEntities: trendFilters.tenderEntities.join(',') || undefined,
        competitorNames: trendFilters.competitors.join(',') || undefined
      }
      const res = await dashboardApi.getTrendsWithFilters(params)
      const data = Array.isArray(res?.data) ? res.data : []
      trendChartOption.value = buildTrendChartOption(data, 'time')
    } catch (e) {
      console.error('[M1] 加载趋势失败:', e)
      m1Error.value = true
    } finally {
      m1Loading.value = false
      m1ChartLoading.value = false
    }
  }

  async function loadM2Data() {
    m2Loading.value = true
    m2Error.value = false
    try {
      const params = {
        startDate: formatDateStr(globalDateRange.value?.[0]),
        endDate: formatDateStr(globalDateRange.value?.[1])
      }
      const res = await dashboardApi.getCustomerTypes(params)
      customerTypeData.value = Array.isArray(res?.data) ? res.data : []
    } catch (e) {
      console.error('[M2] 加载客户类型失败:', e)
      m2Error.value = true
    } finally {
      m2Loading.value = false
    }
  }

  async function loadM3Data() {
    m3Loading.value = true
    m3Error.value = false
    try {
      const params = {
        startDate: formatDateStr(globalDateRange.value?.[0]),
        endDate: formatDateStr(globalDateRange.value?.[1])
      }
      const res = await dashboardApi.getProjectTypes(params)
      projectTypeData.value = Array.isArray(res?.data) ? res.data : []
    } catch (e) {
      console.error('[M3] 加载项目类型失败:', e)
      m3Error.value = true
    } finally {
      m3Loading.value = false
    }
  }

  async function loadM4Data() {
    m4Loading.value = true
    m4Error.value = false
    try {
      const data = {
        startDate: m4DateRange.value?.[0] || null,
        endDate: m4DateRange.value?.[1] || null
      }
      const res = await dashboardApi.getCompetitorAnalysis(data)
      competitorData.value = Array.isArray(res?.data) ? res.data : []
    } catch (e) {
      console.error('[M4] 加载竞品分析失败:', e)
      m4Error.value = true
    } finally {
      m4Loading.value = false
    }
  }

  async function loadAllData() {
    initialLoading.value = true
    refreshing.value = true
    try {
      await Promise.all([
        loadM0Data(), loadM1Data(), loadM2Data(), loadM3Data()
      ])
    } finally {
      initialLoading.value = false
      refreshing.value = false
    }
  }

  function handleGlobalDateChange() {
    ElMessage.info('日期范围已更新，正在刷新数据...')
    loadAllData()
  }

  function handleGlobalDateReset() {
    globalDateRange.value = defaultDateRange()
    ElMessage.info('日期范围已重置，正在刷新数据...')
    loadAllData()
  }

  function handleM4DateChange() {
    loadM4Data()
  }

  function handleRefresh() {
    loadAllData()
  }

  function handleTrendFilterChange(newFilters) {
    Object.assign(trendFilters, newFilters)
    loadM1Data()
  }

  function handleTrendDrill() {
    trendDrillLoading.value = true
    setTimeout(() => {
      trendDrillLoading.value = false
    }, 500)
  }

  return {
    globalDateRange, m4DateRange,
    initialLoading, refreshing,
    m0Loading, m0Error, m1Loading, m1ChartLoading, m1Error,
    m2Loading, m2Error, m3Loading, m3Error, m4Loading, m4Error,
    trendDrillLoading,
    kpiCards, customerTypeData, projectTypeData, competitorData,
    trendDrillData, trendChartOption, trendFilters,
    loadM0Data, loadM1Data, loadM2Data, loadM3Data, loadM4Data, loadAllData,
    handleGlobalDateChange, handleGlobalDateReset, handleM4DateChange, handleRefresh,
    handleTrendFilterChange, handleTrendDrill
  }
}