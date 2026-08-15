import { ref, computed, nextTick, markRaw } from 'vue'
import * as echarts from 'echarts'
import { dashboardApi } from '@/api/modules/dashboard.js'
import { renderCompetitorChart } from '../chartRenderer.js'

// PRD §9.4 竞品公司枚举（与 M1 多维度趋势分析保持一致，共 24 项）
const COMPETITOR_ENUM = [
  '震坤行', '鑫方盛', '浙江物产', '欧菲斯', '领先未来',
  '浙江宏伟', '咸亨国际', '企事通', '一线达通', '京东',
  '苏宁', '科力普', '得力', '史泰博', '齐心',
  '广博', '一出科技', '怡亚通', '申合信', '大江科技',
  '诚和致远', '阳采', '德致商成', '全程速达'
].map((name) => ({ label: name, value: name }))

// 默认选中全部 24 项
const DEFAULT_COMPETITORS = COMPETITOR_ENUM.map((c) => c.value)
const DEFAULT_DATE_RANGE = () => {
  const now = new Date()
  const y = now.getFullYear()
  const m = String(now.getMonth() + 1).padStart(2, '0')
  const d = String(now.getDate()).padStart(2, '0')
  return [`${y}-01-01`, `${y}-${m}-${d}`]
}

// 将后端响应规范化为 chartRenderer 所需结构（兼容 PRD 格式与旧格式）
function normalizeChartData(response, mode, selectedCompetitors, selectedEntities) {
  const data = response?.data || response || {}
  if (data.mode) return data

  if (mode === 'grouped') {
    const detail = data.detail || {}
    const categories = selectedEntities.filter((e) => detail[e])
    const groups = selectedCompetitors.map((comp) => {
      const minData = categories.map((e) => Number(detail[e]?.[comp]?.min ?? 0))
      const avgData = categories.map((e) => Number(detail[e]?.[comp]?.avg ?? 0))
      const maxData = categories.map((e) => Number(detail[e]?.[comp]?.max ?? 0))
      return { competitor: comp, minData, avgData, maxData }
    })
    const overallAvgLine = categories.map((e) => {
      const avgs = selectedCompetitors.map((c) => Number(detail[e]?.[c]?.avg)).filter((v) => v > 0)
      return avgs.length ? Number((avgs.reduce((s, v) => s + v, 0) / avgs.length).toFixed(1)) : 0
    })
    return { mode: 'grouped', categories, groups, overallAvgLine }
  }

  // 默认模式：兼容 { competitors, discounts: { comp: { min, avg, max } } }
  const discounts = data.discounts || {}
  const categories = selectedCompetitors
  const series = [
    { name: '最低折扣', data: selectedCompetitors.map((c) => Number(discounts[c]?.min ?? 0)) },
    { name: '平均折扣', data: selectedCompetitors.map((c) => Number(discounts[c]?.avg ?? 0)) },
    { name: '最高折扣', data: selectedCompetitors.map((c) => Number(discounts[c]?.max ?? 0)) }
  ]
  return { mode: 'default', categories, series }
}

export function useCompetitorData(chartRef) {
  const loading = ref(false)
  const error = ref(false)
  const noData = ref(false)
  let chartInstance = null

  const dateRange = ref(DEFAULT_DATE_RANGE())
  const selectedCompetitors = ref([...DEFAULT_COMPETITORS])
  const selectedEntities = ref([])
  const selectedProjectName = ref(null)
  const entityActive = ref(false)
  const projectNameActive = ref(false)
  const generateTableChecked = ref(false)

  const entityOptions = ref([])
  const projectNameOptions = ref([])

  const chartData = ref(null)
  const tableData = ref(null)
  const tableVisible = ref(false)

  const competitorOptions = computed(() => COMPETITOR_ENUM)

  const generateTableDisabled = computed(
    () => !projectNameActive.value || !selectedProjectName.value
  )

  // 解析折扣值为纯数字（PRD §9.15 — 折扣列只显示数字，不带百分号）
  const parseDiscountValue = (raw) => {
    if (raw == null) return ''
    const cleaned = String(raw).replace(/[^0-9.]/g, '')
    return cleaned === '' ? '' : cleaned
  }

  // 解析账期天数为纯数字（PRD §9.15 — 账期列显示天数）
  const parsePaymentDays = (raw) => {
    if (raw == null) return ''
    const cleaned = String(raw).replace(/[^0-9]/g, '')
    return cleaned === '' ? '' : cleaned
  }

  const chartMode = computed(() => {
    if (projectNameActive.value && selectedProjectName.value) return 'project'
    if (selectedEntities.value.length > 0) return 'grouped'
    return 'default'
  })

  const searchProjectNames = async (query) => {
    if (!query) { projectNameOptions.value = []; return }
    try {
      const res = await dashboardApi.getProjectNames({ query })
      const list = Array.isArray(res?.data) ? res.data : []
      projectNameOptions.value = list.map((p) => (typeof p === 'string' ? p : p?.name || p?.label || '')).filter(Boolean)
    } catch (err) {
      console.warn('M4 project-names fetch error (non-fatal):', err)
      projectNameOptions.value = []
    }
  }

  // PRD §9.12 步骤 7 + §9.14：竞品公司至少保留 1 个，选值变化自动刷新
  const onCompetitorChange = (val) => {
    if (!val || val.length === 0) {
      // 清空时保留第一项（震坤行）
      selectedCompetitors.value = [COMPETITOR_ENUM[0].value]
      return
    }
    fetchData()
  }

  // PRD §9.12 步骤 10：取消招标主体勾选时清空选值并切回默认模式
  const onEntityToggle = (checked) => {
    entityActive.value = checked
    if (checked) {
      projectNameActive.value = false
      selectedProjectName.value = null
      generateTableChecked.value = false
    } else {
      selectedEntities.value = []
      fetchData()
    }
  }

  const onEntityChange = (val) => {
    if (val && val.length > 0) {
      entityActive.value = true
      projectNameActive.value = false
      selectedProjectName.value = null
      generateTableChecked.value = false
    }
    fetchData()
  }

  // PRD §9.12 步骤 13/10：勾选项目名称时互斥招标主体；取消时切回默认模式
  const onProjectNameToggle = (checked) => {
    projectNameActive.value = checked
    if (checked) {
      entityActive.value = false
      selectedEntities.value = []
    } else {
      selectedProjectName.value = null
      generateTableChecked.value = false
      tableData.value = null
      tableVisible.value = false
      fetchData()
    }
  }

  const onProjectNameChange = (val) => {
    if (val) {
      projectNameActive.value = true
      entityActive.value = false
      selectedEntities.value = []
    }
  }

  const onGenerateTableChange = () => {
    // 仅状态切换，实际表格生成在确认时触发
  }

  const renderChart = () => {
    if (!chartData.value) return
    // 先设 loading=false 让图表容器 div 渲染（v-else 分支），chartRef 才能可用
    loading.value = false
    nextTick(() => {
      if (!chartRef.value) return
      if (!chartInstance) chartInstance = markRaw(echarts.init(chartRef.value))
      const ok = renderCompetitorChart(chartInstance, chartData.value)
      if (!ok) noData.value = true
    })
  }

  async function fetchData() {
    if (!selectedCompetitors.value || selectedCompetitors.value.length === 0) return
    loading.value = true
    error.value = false
    noData.value = false
    tableVisible.value = false
    try {
      const params = {
        competitorNames: selectedCompetitors.value,
        startDate: dateRange.value?.[0] || null,
        endDate: dateRange.value?.[1] || null
      }
      const mode = chartMode.value
      if (mode === 'grouped') params.tenderEntities = selectedEntities.value
      if (mode === 'project') params.projectName = selectedProjectName.value

      const response = await dashboardApi.getCompetitorAnalysis(params)
      chartData.value = normalizeChartData(response, mode, selectedCompetitors.value, selectedEntities.value)

      // 项目模式 + 勾选生成表格 → 构建表格数据
      if (mode === 'project' && generateTableChecked.value) {
        const raw = response?.data || response || {}
        tableData.value = {
          projectLabel: raw.projectLabel || selectedProjectName.value,
          rows: Array.isArray(raw.tableRows) ? raw.tableRows : []
        }
        tableVisible.value = true
      } else {
        tableData.value = null
      }

      renderChart()
    } catch (err) {
      console.error('M4 CompetitorAnalysis fetch error:', err)
      error.value = true
      loading.value = false
    }
  }

  const resetDateRange = () => {
    dateRange.value = DEFAULT_DATE_RANGE()
    fetchData()
  }

  const resetFilters = () => {
    selectedCompetitors.value = [...DEFAULT_COMPETITORS]
    selectedEntities.value = []
    selectedProjectName.value = null
    entityActive.value = false
    projectNameActive.value = false
    generateTableChecked.value = false
    tableData.value = null
    tableVisible.value = false
    fetchData()
  }

  const initOptions = async () => {
    try {
      const res = await dashboardApi.getTenderEntities()
      const list = Array.isArray(res?.data) ? res.data : []
      entityOptions.value = list.map((e) => {
        const name = typeof e === 'string' ? e : (e?.name || e?.entityName || '')
        return { label: name, value: name }
      }).filter((o) => o.value)
    } catch (err) {
      console.warn('M4 tender-entities fetch error (non-fatal):', err)
      entityOptions.value = []
    }
  }

  const resizeChart = () => { if (chartInstance) chartInstance.resize() }
  const disposeChart = () => {
    if (chartInstance) { chartInstance.dispose(); chartInstance = null }
  }

  return {
    loading, error, noData,
    dateRange, selectedCompetitors, selectedEntities, selectedProjectName,
    competitorOptions, entityOptions, projectNameOptions,
    entityActive, projectNameActive, generateTableChecked, generateTableDisabled,
    chartData, tableData, tableVisible, chartMode,
    parseDiscountValue, parsePaymentDays,
    searchProjectNames, onCompetitorChange,
    onEntityToggle, onEntityChange, onProjectNameToggle, onProjectNameChange, onGenerateTableChange,
    fetchData, resetDateRange, resetFilters, initOptions, resizeChart, disposeChart
  }
}
