// 项目名称模式柱子配色（原型 2240 行）
const PROJECT_BAR_COLORS = [
  '#2563EB', '#8B5CF6', '#10B981', '#F59E0B',
  '#EF4444', '#06B6D4', '#EC4899', '#F97316'
]

// 分组模式每组配色（浅=最低/中=平均/深=最高，原型 2281-2290 行）
const GROUP_COLOR_SHADES = [
  ['#BBF7D0', '#4ADE80', '#16A34A'],
  ['#BFDBFE', '#60A5FA', '#2563EB'],
  ['#FDE68A', '#FBBF24', '#D97706'],
  ['#FECACA', '#F87171', '#DC2626'],
  ['#DDD6FE', '#A78BFA', '#7C3AED'],
  ['#FBCFE8', '#F472B6', '#DB2777'],
  ['#99F6E4', '#2DD4BF', '#0D9488'],
  ['#FED7AA', '#FB923C', '#EA580C']
]

const OVERALL_AVG_COLOR = '#F97316'

const tooltipStyle = {
  trigger: 'axis',
  axisPointer: { type: 'cross' },
  backgroundColor: 'rgba(255,255,255,0.96)',
  borderColor: '#E2E8F0',
  borderWidth: 1,
  padding: [10, 14],
  textStyle: { color: '#1E293B', fontSize: 12 },
  extraCssText: 'box-shadow: 0 4px 16px rgba(0,0,0,0.12); border-radius: 8px;'
}

const axisLineColor = '#E2E8F0'
const splitLineColor = '#F1F5F9'
const axisLabelColor = '#475569'
const axisNameColor = '#94A3B8'

// 默认模式：竞品公司为 X 轴，最低/平均/最高三段堆叠（绿色系）
export function renderDefaultMode(chartInstance, data) {
  const categories = data.categories || []
  if (categories.length === 0) return false
  const series = data.series || []
  const minData = series.find((s) => s.name === '最低折扣')?.data || []
  const avgData = series.find((s) => s.name === '平均折扣')?.data || []
  const maxData = series.find((s) => s.name === '最高折扣')?.data || []

  chartInstance.setOption({
    tooltip: {
      ...tooltipStyle,
      formatter: (params) => {
        let tip = `<div style="font-size:13px;font-weight:700;color:#1E293B;margin-bottom:8px;padding-bottom:6px;border-bottom:1px solid #F1F5F9;">${params[0].axisValue}</div>`
        params.forEach((p) => {
          tip += `<div style="display:flex;align-items:center;gap:6px;margin:4px 0;"><span style="display:inline-block;width:8px;height:8px;border-radius:2px;background:${p.color};"></span><span style="font-weight:600;color:#334155;">${p.seriesName}</span><b style="margin-left:auto;color:#475569;">${p.value}</b></div>`
        })
        return tip
      }
    },
    legend: { data: ['最高折扣', '平均折扣', '最低折扣'], top: 0, textStyle: { fontSize: 11 } },
    grid: { left: 60, right: 40, top: 60, bottom: 30 },
    xAxis: {
      type: 'category', data: categories,
      axisLine: { lineStyle: { color: axisLineColor } },
      axisLabel: { fontSize: 12, color: axisLabelColor, interval: 0, rotate: categories.length > 5 ? 20 : 0 }
    },
    yAxis: [{
      type: 'value', name: '折扣(%)',
      axisLine: { show: false },
      splitLine: { lineStyle: { color: splitLineColor } },
      axisLabel: { color: axisNameColor, formatter: '{value}' }
    }],
    series: [
      { name: '最低折扣', type: 'bar', stack: 'discount', data: minData, itemStyle: { color: '#A7F3D0' }, barWidth: '30%', barGap: '20%', label: { show: true, position: 'insideTop', fontSize: 11, color: '#065F46', fontWeight: 600 } },
      { name: '平均折扣', type: 'bar', stack: 'discount', data: avgData, itemStyle: { color: '#6EE7B7' }, barWidth: '30%', label: { show: true, position: 'insideTop', fontSize: 11, color: '#065F46', fontWeight: 600 } },
      { name: '最高折扣', type: 'bar', stack: 'discount', data: maxData, itemStyle: { color: '#10B981', borderRadius: [4, 4, 0, 0] }, barWidth: '30%', label: { show: true, position: 'insideTop', fontSize: 11, color: '#fff', fontWeight: 600 } }
    ]
  }, true)
  return true
}

// 分组模式：招标主体为 X 轴，每个竞品公司一组堆叠（min/avg/max）+ 整体平均折扣折线
export function renderGroupedMode(chartInstance, data) {
  const categories = data.categories || []
  const groups = data.groups || []
  if (categories.length === 0 || groups.length === 0) return false

  const series = []
  groups.forEach((group, ci) => {
    const shades = GROUP_COLOR_SHADES[ci % GROUP_COLOR_SHADES.length]
    const stackName = 'comp_' + ci
    series.push({ name: group.competitor, type: 'bar', stack: stackName, data: group.minData, itemStyle: { color: shades[0] }, label: { show: false } })
    series.push({ name: group.competitor, type: 'bar', stack: stackName, data: group.avgData, itemStyle: { color: shades[1] }, label: { show: false } })
    series.push({ name: group.competitor, type: 'bar', stack: stackName, data: group.maxData, itemStyle: { color: shades[2], borderRadius: [4, 4, 0, 0] }, label: { show: true, position: 'top', fontSize: 10, color: '#475569', fontWeight: 600, formatter: group.competitor } })
  })

  series.push({
    name: '整体平均折扣', type: 'line', data: data.overallAvgLine || [],
    yAxisIndex: 0, symbol: 'circle', symbolSize: 10,
    lineStyle: { width: 3.5, color: OVERALL_AVG_COLOR, shadowColor: 'rgba(249,115,22,0.35)', shadowBlur: 8 },
    itemStyle: { color: OVERALL_AVG_COLOR, borderColor: '#fff', borderWidth: 2 },
    label: { show: true, position: 'top', fontSize: 11, color: OVERALL_AVG_COLOR, fontWeight: 700, formatter: '{c}', backgroundColor: 'rgba(255,255,255,0.92)', padding: [2, 5], borderRadius: 3, borderColor: OVERALL_AVG_COLOR, borderWidth: 1 },
    z: 100
  })

  chartInstance.setOption({
    tooltip: {
      ...tooltipStyle,
      formatter: (params) => {
        let tip = `<div style="font-size:13px;font-weight:700;color:#1E293B;margin-bottom:8px;padding-bottom:6px;border-bottom:1px solid #F1F5F9;">${params[0].axisValue}</div>`
        const grouped = {}
        let avgLineVal = null
        let avgLineColor = null
        params.forEach((p) => {
          if (p.seriesName === '整体平均折扣') { avgLineVal = p.value; avgLineColor = p.color; return }
          if (!grouped[p.seriesName]) grouped[p.seriesName] = { vals: [], color: p.color }
          grouped[p.seriesName].vals.push(p.value)
        })
        Object.keys(grouped).forEach((name) => {
          const g = grouped[name]
          const vals = g.vals.sort((a, b) => a - b)
          tip += `<div style="display:flex;align-items:center;gap:6px;margin:4px 0;"><span style="display:inline-block;width:8px;height:8px;border-radius:2px;background:${g.color};"></span><span style="font-weight:600;color:#334155;">${name}</span></div>`
          tip += `<div style="margin:2px 0 6px 16px;font-size:11px;color:#64748B;"><span style="color:#94A3B8;">最低</span> <b style="color:#475569;">${vals[0]}</b> &nbsp;|&nbsp; <span style="color:#94A3B8;">平均</span> <b style="color:#475569;">${vals[1]}</b> &nbsp;|&nbsp; <span style="color:#94A3B8;">最高</span> <b style="color:#475569;">${vals[2]}</b></div>`
        })
        if (avgLineVal !== null) {
          tip += `<div style="margin-top:6px;padding-top:6px;border-top:1px dashed #F1F5F9;display:flex;align-items:center;gap:6px;"><span style="display:inline-block;width:8px;height:8px;border-radius:50%;background:${avgLineColor || OVERALL_AVG_COLOR};"></span><span style="font-weight:600;color:#334155;">整体平均折扣</span><b style="margin-left:auto;color:${OVERALL_AVG_COLOR};font-size:13px;">${avgLineVal}</b></div>`
        }
        return tip
      }
    },
    legend: { data: [...groups.map((g) => g.competitor), '整体平均折扣'], top: 0, textStyle: { fontSize: 11 } },
    grid: { left: 60, right: 40, top: 60, bottom: 50 },
    xAxis: {
      type: 'category', data: categories,
      axisLine: { lineStyle: { color: axisLineColor } },
      axisLabel: { fontSize: 12, color: axisLabelColor, interval: 0, rotate: categories.length > 4 ? 20 : 0 }
    },
    yAxis: [{
      type: 'value', name: '折扣(%)',
      axisLine: { show: false },
      splitLine: { lineStyle: { color: splitLineColor } },
      axisLabel: { color: axisNameColor, formatter: '{value}' }
    }],
    series
  }, true)
  return true
}

// 项目名称模式：竞品公司为 X 轴，单条柱展示折扣，每柱不同颜色，标题为项目名称
export function renderProjectMode(chartInstance, data) {
  const categories = data.categories || []
  if (categories.length === 0) return false
  const discounts = data.discounts || []

  chartInstance.setOption({
    title: { text: data.projectLabel || '', left: 'center', top: 0, textStyle: { fontSize: 14, fontWeight: 700, color: '#1E293B' } },
    tooltip: {
      ...tooltipStyle,
      formatter: (params) => {
        let tip = `<div style="font-size:13px;font-weight:700;color:#1E293B;margin-bottom:6px;">${params[0].axisValue}</div>`
        params.forEach((p) => {
          tip += `<div style="display:flex;align-items:center;gap:6px;"><span style="display:inline-block;width:8px;height:8px;border-radius:2px;background:${p.color};"></span><span style="font-weight:600;color:#334155;">折扣</span><b style="margin-left:auto;color:#475569;">${p.value}%</b></div>`
        })
        return tip
      }
    },
    grid: { left: 60, right: 40, top: 50, bottom: 60 },
    xAxis: {
      type: 'category', data: categories,
      axisLine: { lineStyle: { color: axisLineColor } },
      axisLabel: { fontSize: 12, color: axisLabelColor, interval: 0, rotate: categories.length > 5 ? 20 : 0 }
    },
    yAxis: [{
      type: 'value', name: '折扣(%)',
      axisLine: { show: false },
      splitLine: { lineStyle: { color: splitLineColor } },
      axisLabel: { color: axisNameColor, formatter: '{value}' }
    }],
    series: [{
      name: '折扣', type: 'bar',
      data: discounts.map((v, i) => ({ value: v, itemStyle: { color: PROJECT_BAR_COLORS[i % PROJECT_BAR_COLORS.length] } })),
      barWidth: '45%',
      itemStyle: { borderRadius: [4, 4, 0, 0] },
      label: { show: true, position: 'top', fontSize: 12, fontWeight: 600, color: '#475569', formatter: '{c}' }
    }]
  }, true)
  return true
}

// 分发：根据 chartData.mode 调用对应渲染器
export function renderCompetitorChart(chartInstance, chartData) {
  if (!chartData || !chartInstance) return false
  switch (chartData.mode) {
    case 'project': return renderProjectMode(chartInstance, chartData)
    case 'grouped': return renderGroupedMode(chartInstance, chartData)
    default: return renderDefaultMode(chartInstance, chartData)
  }
}

// 导出竞品明细表格为 Excel(.xls)
// PRD §9.15 — 折扣列只显示数字，账期列显示天数
export function exportCompetitorTable(tableData, parseDiscountValue, parsePaymentDays) {
  if (!tableData) return
  const rows = Array.isArray(tableData.rows) ? tableData.rows : []
  const sorted = [...rows].sort((a, b) => {
    if (a.isWon && !b.isWon) return -1
    if (!a.isWon && b.isWon) return 1
    const da = Number(parseDiscountValue(a.discount)) || 0
    const db = Number(parseDiscountValue(b.discount)) || 0
    return db - da
  })
  let html = '<table border="1"><thead><tr><th>竞品公司</th><th>折扣/百分比</th><th>账期/天</th><th>是否中标</th></tr></thead><tbody>'
  sorted.forEach((r) => {
    html += `<tr><td>${r.competitor}</td><td>${parseDiscountValue(r.discount)}</td><td>${parsePaymentDays(r.paymentDays)}</td><td>${r.isWon ? '已中标' : '未中标'}</td></tr>`
  })
  html += '</tbody></table>'
  const fullHtml = `<html xmlns:o="urn:schemas-microsoft-com:office:office" xmlns:x="urn:schemas-microsoft-com:office:excel" xmlns="http://www.w3.org/TR/REC-html40"><head><meta charset="UTF-8"></head><body>${html}</body></html>`
  const blob = new Blob(['\ufeff' + fullHtml], { type: 'application/vnd.ms-excel' })
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = `${tableData.projectLabel || '竞品明细'}.xls`
  link.click()
  URL.revokeObjectURL(link.href)
}
