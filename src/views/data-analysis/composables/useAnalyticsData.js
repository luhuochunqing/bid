import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { dashboardApi } from '@/api'

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
    customerTypes: [],
    projectTypes: [],
    industries: [],
    regions: [],
    tenderEntities: [],
    projectLeaders: [],
    bidResults: [],
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

  function buildTrendChartOption(data, dimension) {
    return {
      tooltip: { trigger: 'axis' },
      legend: { data: ['投标数', '中标数', '中标率'] },
      xAxis: {
        type: 'category',
        data: Array.isArray(data) ? data.map((d) => d.period || d.month || '-') : []
      },
      yAxis: [
        { type: 'value', name: '数量' },
        { type: 'value', name: '比率', min: 0, max: 100 }
      ],
      series: [
        {
          name: '投标数',
          type: 'bar',
          data: Array.isArray(data) ? data.map((d) => Number(d.count || d.bids || 0)) : []
        },
        {
          name: '中标数',
          type: 'bar',
          data: Array.isArray(data) ? data.map((d) => Number(d.wins || 0)) : []
        },
        {
          name: '中标率',
          type: 'line',
          yAxisIndex: 1,
          data: Array.isArray(data) ? data.map((d) => Number(d.rate || d.changePercentage || 0)) : []
        }
      ]
    }
  }

  async function loadM0Data() {
    m0Loading.value = true
    m0Error.value = false
    try {
      const res = await dashboardApi.getOverview()
      const data = res?.data || {}
      kpiCards.value = [
        {
          key: 'bids', label: '年度投标数',
          value: String(data.totalBids ?? '--'),
          trendText: data.totalBidsChange || '--', trend: 0, colorClass: 'green'
        },
        {
          key: 'winRate', label: '中标率',
          value: data.winRate != null ? data.winRate + '%' : '--',
          trendText: data.winRateChange || '--', trend: 0, colorClass: 'blue'
        },
        {
          key: 'amount', label: '中标金额',
          value: data.totalAmount != null ? formatAmount(data.totalAmount) : '--',
          trendText: data.totalAmountChange || '--', trend: 0, colorClass: 'orange'
        },
        {
          key: 'cost', label: '投入费用',
          value: data.totalCost != null ? formatAmount(data.totalCost) : '--',
          trendText: data.totalCostChange || '--', trend: 0, colorClass: 'red'
        }
      ]
    } catch (e) {
      console.error('[M0] 加载KPI失败:', e)
      m0Error.value = true
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
        startDate: globalDateRange.value?.[0] || null,
        endDate: globalDateRange.value?.[1] || null,
        timeDimension: trendFilters.timeDimension,
        customerTypes: trendFilters.customerTypes.join(',') || undefined,
        projectTypes: trendFilters.projectTypes.join(',') || undefined,
        industries: trendFilters.industries.join(',') || undefined,
        regions: trendFilters.regions.join(',') || undefined,
        tenderEntities: trendFilters.tenderEntities.join(',') || undefined,
        projectLeaders: trendFilters.projectLeaders.join(',') || undefined,
        bidResults: trendFilters.bidResults.join(',') || undefined,
        competitors: trendFilters.competitors.join(',') || undefined
      }
      const res = await dashboardApi.getTrendsWithFilters(params)
      const data = Array.isArray(res?.data) ? res.data : []
      trendChartOption.value = buildTrendChartOption(data, trendFilters.timeDimension)
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
        startDate: globalDateRange.value?.[0] || null,
        endDate: globalDateRange.value?.[1] || null
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
        startDate: globalDateRange.value?.[0] || null,
        endDate: globalDateRange.value?.[1] || null
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
        loadM0Data(), loadM1Data(), loadM2Data(), loadM3Data(), loadM4Data()
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
    handleGlobalDateChange, handleM4DateChange, handleRefresh,
    handleTrendFilterChange, handleTrendDrill
  }
}