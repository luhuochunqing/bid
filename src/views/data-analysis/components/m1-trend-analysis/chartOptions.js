import * as echarts from 'echarts'

export function buildChartOption(data, xAxisType) {
  if (!data || data.length === 0) return {}

  const categories = data.map((item) => item.label || item.name || '')
  const bidCounts = data.map((item) => item.bidCount ?? 0)
  const winCounts = data.map((item) => item.winCount ?? 0)
  const winRates = data.map((item) => {
    const rate = item.winRate ?? 0
    return Number(rate.toFixed(1))
  })

  const isCategoryChart = xAxisType === 'projectStatus'
  const showDataZoom = categories.length > 100

  const baseOption = {
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(255, 255, 255, 0.95)',
      borderColor: '#E2E8F0',
      borderWidth: 1,
      textStyle: { color: '#1E293B', fontSize: 12 },
      formatter: (params) => {
        let tip = `<div style="font-weight:600;margin-bottom:4px;font-size:13px;">${params[0].axisValue}</div>`
        params.forEach((p) => {
          tip += `<div style="display:flex;justify-content:space-between;gap:16px;">
            <span>${p.marker} ${p.seriesName}</span>
            <span style="font-weight:600;">${p.value}${p.seriesName === '中标率' ? '%' : ''}</span>
          </div>`
        })
        return tip
      }
    },
    legend: {
      data: isCategoryChart ? ['投标数', '中标数'] : ['投标数', '中标数', '中标率'],
      bottom: 0, left: 'center', icon: 'circle',
      textStyle: { color: '#475569', fontSize: 12 },
      itemWidth: 8, itemHeight: 8
    },
    grid: {
      left: 50, right: 30, top: 20, bottom: showDataZoom ? 60 : 40
    },
    xAxis: {
      type: 'category',
      data: categories,
      axisLine: { lineStyle: { color: '#E2E8F0' } },
      axisLabel: {
        color: '#475569', fontSize: 11,
        interval: isCategoryChart ? 0 : 'auto',
        rotate: categories.length > 10 ? 45 : 0
      },
      axisTick: { alignWithLabel: true }
    }
  }

  const commonBarStyle = {
    color: '#2563EB', borderRadius: [2, 2, 0, 0]
  }

  const commonWinBarStyle = {
    color: '#10B981', borderRadius: [2, 2, 0, 0]
  }

  if (isCategoryChart) {
    return {
      ...baseOption,
      yAxis: {
        type: 'value', name: '数量',
        nameTextStyle: { color: '#475569', fontSize: 11 },
        splitLine: { lineStyle: { color: '#F1F5F9', type: 'dashed' } },
        axisLabel: { color: '#475569', fontSize: 11 }
      },
      series: [
        { name: '投标数', type: 'bar', data: bidCounts, itemStyle: commonBarStyle, barMaxWidth: 32 },
        { name: '中标数', type: 'bar', data: winCounts, itemStyle: commonWinBarStyle, barMaxWidth: 32 }
      ]
    }
  }

  return {
    ...baseOption,
    yAxis: [
      {
        type: 'value', name: '数量',
        nameTextStyle: { color: '#475569', fontSize: 11 },
        splitLine: { lineStyle: { color: '#F1F5F9', type: 'dashed' } },
        axisLabel: { color: '#475569', fontSize: 11 }
      },
      {
        type: 'value', name: '中标率(%)', min: 0, max: 100,
        nameTextStyle: { color: '#F59E0B', fontSize: 11 },
        splitLine: { show: false },
        axisLabel: { color: '#F59E0B', fontSize: 11, formatter: '{value}%' }
      }
    ],
    series: [
      { name: '投标数', type: 'bar', data: bidCounts, yAxisIndex: 0, itemStyle: commonBarStyle, barMaxWidth: 32 },
      { name: '中标数', type: 'bar', data: winCounts, yAxisIndex: 0, itemStyle: commonWinBarStyle, barMaxWidth: 32 },
      {
        name: '中标率', type: 'line', data: winRates, yAxisIndex: 1,
        smooth: true, symbol: 'circle', symbolSize: 6,
        lineStyle: { color: '#F59E0B', width: 2 },
        itemStyle: { color: '#F59E0B' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(245, 158, 11, 0.15)' },
            { offset: 1, color: 'rgba(245, 158, 11, 0.02)' }
          ])
        }
      }
    ],
    ...(showDataZoom ? {
      dataZoom: [
        {
          type: 'slider',
          start: Math.max(0, 100 - (30 / categories.length) * 100),
          end: 100, height: 20, bottom: 10,
          borderColor: '#E2E8F0',
          fillerColor: 'rgba(37, 99, 235, 0.1)',
          handleStyle: { color: '#2563EB' },
          textStyle: { color: '#475569', fontSize: 11 },
          labelFormatter: (_value, str) => str
        },
        {
          type: 'inside',
          start: Math.max(0, 100 - (30 / categories.length) * 100),
          end: 100
        }
      ]
    } : {})
  }
}