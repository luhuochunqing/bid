import * as echarts from 'echarts'

export const COMPETITOR_COLORS = [
  '#2563EB', '#10B981', '#F59E0B', '#EF4444',
  '#8B5CF6', '#EC4899', '#06B6D4', '#F97316',
  '#6366F1', '#14B8A6', '#D946EF', '#84CC16'
]

function getBarGradient() {
  return new echarts.graphic.LinearGradient(0, 0, 0, 1, [
    { offset: 0, color: '#A7F3D0' },
    { offset: 1, color: '#10B981' }
  ])
}

const tooltipBase = {
  backgroundColor: 'rgba(255,255,255,0.96)',
  borderColor: '#E2E8F0',
  borderWidth: 1,
  borderRadius: 8,
  padding: [10, 14]
}

const axisLabelStyle = { color: '#475569', fontSize: 12 }

export function renderDefaultMode(chartInstance, data, selectedCompetitors) {
  const competitors = Array.isArray(data.competitors) ? data.competitors : Object.keys(data.discounts || {})
  const discounts = data.discounts || {}
  const activeCompetitors = competitors.filter((c) => selectedCompetitors.includes(c))

  if (activeCompetitors.length === 0) return false

  const option = {
    tooltip: {
      ...tooltipBase,
      trigger: 'axis',
      formatter: function (params) {
        const competitor = params[0]?.axisValue || ''
        let html = `<strong>${competitor}</strong><br/>`
        const discount = discounts[competitor] || {}
        html += `最低折扣: ${discount.min != null ? (discount.min * 100).toFixed(1) + '%' : '--'}<br/>`
        html += `平均折扣: ${discount.avg != null ? (discount.avg * 100).toFixed(1) + '%' : '--'}<br/>`
        html += `最高折扣: ${discount.max != null ? (discount.max * 100).toFixed(1) + '%' : '--'}`
        return html
      }
    },
    legend: {
      data: [
        { name: '最低折扣', icon: 'rect' },
        { name: '平均折扣', icon: 'rect' },
        { name: '最高折扣', icon: 'rect' }
      ],
      orient: 'horizontal', bottom: 0, left: 'center',
      itemWidth: 14, itemHeight: 10, itemGap: 24,
      textStyle: { color: '#475569', fontSize: 12 }
    },
    grid: { left: 50, right: 30, top: 20, bottom: 50, containLabel: true },
    xAxis: {
      type: 'category', data: activeCompetitors,
      axisLabel: {
        ...axisLabelStyle,
        interval: 0, rotate: activeCompetitors.length > 5 ? 30 : 0
      },
      axisLine: { lineStyle: { color: '#E2E8F0' } },
      axisTick: { alignWithLabel: true }
    },
    yAxis: {
      type: 'value', name: '折扣率',
      nameTextStyle: { color: '#94A3B8', fontSize: 11 },
      axisLabel: { color: '#94A3B8', fontSize: 11, formatter: '{value}%' },
      splitLine: { lineStyle: { color: '#F1F5F9', type: 'dashed' } },
      axisLine: { show: false }, axisTick: { show: false }
    },
    series: ['min', 'avg', 'max'].map((key, _idx) => ({
      name: key === 'min' ? '最低折扣' : key === 'avg' ? '平均折扣' : '最高折扣',
      type: 'bar',
      barWidth: '22%',
      barGap: '15%',
      data: activeCompetitors.map((c) => {
        const d = discounts[c]
        return d?.[key] != null ? Number((d[key] * 100).toFixed(1)) : 0
      }),
      itemStyle: { color: getBarGradient(), borderRadius: [2, 2, 0, 0] }
    }))
  }

  chartInstance.setOption(option, true)
  return true
}

export function renderGroupedMode(chartInstance, data, selectedCompetitors, selectedEntities) {
  const entities = Array.isArray(data.entities) ? data.entities : Object.keys(data.detail || {})
  const detail = data.detail || {}
  const activeEntities = entities.filter((e) => selectedEntities.includes(e))
  const activeCompetitors = selectedCompetitors

  if (activeEntities.length === 0 || activeCompetitors.length === 0) return false

  const overallAvgMap = {}
  activeEntities.forEach((entity) => {
    const entityData = detail[entity] || {}
    const avgs = activeCompetitors
      .map((c) => entityData[c]?.avg)
      .filter((v) => v != null)
    overallAvgMap[entity] = avgs.length > 0
      ? avgs.reduce((s, v) => s + v, 0) / avgs.length
      : null
  })

  const overallAvgLine = activeEntities.map((entity) => {
    if (overallAvgMap[entity] == null) return null
    return Number((overallAvgMap[entity] * 100).toFixed(1))
  })

  const seriesList = activeCompetitors.map((competitor, idx) => {
    const values = activeEntities.map((entity) => {
      const entityData = detail[entity] || {}
      const compData = entityData[competitor]
      return compData?.avg != null ? Number((compData.avg * 100).toFixed(1)) : 0
    })
    return {
      name: competitor, type: 'bar',
      barWidth: Math.max(8, Math.min(24, 80 / activeCompetitors.length)),
      barGap: '10%', data: values,
      itemStyle: { color: COMPETITOR_COLORS[idx % COMPETITOR_COLORS.length], borderRadius: [2, 2, 0, 0] },
      label: { show: true, position: 'top', formatter: competitor, color: '#475569', fontSize: 10, rotate: 0 }
    }
  })

  seriesList.push({
    name: '整体平均折扣', type: 'line', data: overallAvgLine,
    smooth: false, symbol: 'circle', symbolSize: 8,
    lineStyle: { color: '#F97316', width: 2, type: 'solid' },
    itemStyle: { color: '#F97316' },
    label: { show: true, position: 'top',
      formatter: (params) => params.value != null ? params.value + '%' : '',
      color: '#F97316', fontSize: 11, fontWeight: 600
    },
    z: 10
  })

  const legendData = activeCompetitors.map((c) => ({ name: c, icon: 'rect' }))
  legendData.push({ name: '整体平均折扣', icon: 'line', itemStyle: { color: '#F97316' } })

  const option = {
    tooltip: {
      ...tooltipBase, trigger: 'axis',
      formatter: function (params) {
        const entity = params[0]?.axisValue || ''
        let html = `<strong>${entity}</strong><br/>`
        params.filter((p) => p.seriesName !== '整体平均折扣').forEach((p) => {
          html += `${p.marker} ${p.seriesName}: ${p.value != null ? p.value + '%' : '--'}<br/>`
        })
        const overall = params.find((p) => p.seriesName === '整体平均折扣')
        if (overall && overall.value != null) {
          html += `<div style="border-top:1px solid #E2E8F0;margin:4px 0;padding-top:4px;">`
          html += `${overall.marker} 整体平均折扣: <strong>${overall.value}%</strong></div>`
        }
        return html
      }
    },
    legend: {
      data: legendData, orient: 'horizontal', bottom: 0, left: 'center',
      type: 'scroll', itemWidth: 14, itemHeight: 10, itemGap: 20,
      textStyle: { color: '#475569', fontSize: 12 }
    },
    grid: { left: 50, right: 30, top: 30, bottom: 50, containLabel: true },
    xAxis: {
      type: 'category', data: activeEntities,
      axisLabel: {
        ...axisLabelStyle,
        interval: 0, rotate: activeEntities.length > 5 ? 30 : 0
      },
      axisLine: { lineStyle: { color: '#E2E8F0' } }, axisTick: { alignWithLabel: true }
    },
    yAxis: {
      type: 'value', name: '折扣率',
      nameTextStyle: { color: '#94A3B8', fontSize: 11 },
      axisLabel: { color: '#94A3B8', fontSize: 11, formatter: '{value}%' },
      splitLine: { lineStyle: { color: '#F1F5F9', type: 'dashed' } },
      axisLine: { show: false }, axisTick: { show: false }
    },
    series: seriesList
  }

  chartInstance.setOption(option, true)
  return true
}