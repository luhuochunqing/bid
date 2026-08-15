import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { dashboardApi } from '@/api'

export function useAnalyticsData() {
  const defaultDateRange = () => [new Date('2026-01-01'), new Date()]
  const globalDateRange = ref(defaultDateRange())
  const m4DateRange = ref(defaultDateRange())

  const initialLoading = ref(true)
  const refreshing = ref(false)

  const m0Loading = ref(false)
  const m0Error = ref(false)
  const m2Loading = ref(false)
  const m2Error = ref(false)
  const m3Loading = ref(false)
  const m3Error = ref(false)
  const m4Loading = ref(false)
  const m4Error = ref(false)

  const kpiCards = ref([])
  const customerTypeData = ref([])
  const projectTypeData = ref([])
  const competitorData = ref([])

  function formatAmount(val) {
    if (val == null) return '--'
    const num = Number(val)
    if (isNaN(num)) return '--'
    if (num >= 10000) {
      return (num / 10000).toFixed(1) + '万'
    }
    return num.toLocaleString()
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

  // PRD §3.1 同比格式化（原型：↑ 较去年同期 +18.2% / ↓ 较去年同期 -5.3% / —）
  function formatYoy(yoy) {
    if (yoy == null) return { text: '较去年同期 —', direction: 'flat' }
    const direction = yoy > 0 ? 'up' : yoy < 0 ? 'down' : 'flat'
    const arrow = yoy > 0 ? '↑' : yoy < 0 ? '↓' : ''
    const sign = yoy > 0 ? '+' : ''
    return { text: `${arrow} 较去年同期 ${sign}${yoy.toFixed(1)}%`, direction }
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
      const todayNew = Number(d.todayNewCount ?? 0)

      // PRD §3.1 + 原型：投标总数显示"今日新增 +X"，其他三个显示同比
      const totalTrend = {
        text: `今日新增 +${todayNew}`,
        direction: todayNew > 0 ? 'up' : 'flat'
      }
      const biddingTrend = formatYoy(d.biddingCountYoy)
      const wonTrend = formatYoy(d.wonCountYoy)
      const winRateTrend = formatYoy(d.winRateYoy)

      kpiCards.value = [
        { key: 'totalCount', label: '投标总数', value: String(totalCount), unit: '个', foot: '投标项目总数', trendText: totalTrend.text, trendDirection: totalTrend.direction, colorClass: 'kpi-blue' },
        { key: 'biddingCount', label: '投标中', value: String(biddingCount), unit: '个', foot: '项目状态为投标中', trendText: biddingTrend.text, trendDirection: biddingTrend.direction, colorClass: 'kpi-green' },
        { key: 'wonCount', label: '中标数', value: String(wonCount), unit: '个', foot: '项目状态为已中标', trendText: wonTrend.text, trendDirection: wonTrend.direction, colorClass: 'kpi-orange' },
        { key: 'winRate', label: '中标率', value: winRate.toFixed(1), unit: '%', foot: '中标数 / 投标数', trendText: winRateTrend.text, trendDirection: winRateTrend.direction, colorClass: 'kpi-purple' }
      ]
      // 跨年度检测：如果日期筛选区间跨年度，投标中/中标数/中标率的同比显示"—"
      const start = globalDateRange.value?.[0]
      const end = globalDateRange.value?.[1]
      if (start && end && start.getFullYear() !== end.getFullYear()) {
        kpiCards.value[1].trendText = ''
        kpiCards.value[1].trendDirection = ''
        kpiCards.value[2].trendText = ''
        kpiCards.value[2].trendDirection = ''
        kpiCards.value[3].trendText = ''
        kpiCards.value[3].trendDirection = ''
      }
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
      // M1 自治：自己监听 globalDateRange 变化加载数据，不需要父组件 loadM1Data
      await Promise.all([
        loadM0Data(), loadM2Data(), loadM3Data()
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

  return {
    globalDateRange, m4DateRange,
    initialLoading, refreshing,
    m0Loading, m0Error,
    m2Loading, m2Error, m3Loading, m3Error, m4Loading, m4Error,
    kpiCards, customerTypeData, projectTypeData, competitorData,
    loadM0Data, loadM2Data, loadM3Data, loadM4Data, loadAllData,
    handleGlobalDateChange, handleGlobalDateReset, handleM4DateChange, handleRefresh
  }
}