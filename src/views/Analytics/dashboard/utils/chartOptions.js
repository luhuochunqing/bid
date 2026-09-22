export function buildTrendOption(trendData) {
  const data = trendData || []
  const months = data.map((d) => d.month)
  const rates = data.map((d) => d.rate)
  const amounts = data.map((d) => d.amount / 100)

  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'cross' } },
    legend: { data: ['中标率', '中标金额'], bottom: 0 },
    grid: { left: '3%', right: '4%', bottom: '15%', containLabel: true },
    xAxis: { type: 'category', data: months, boundaryGap: false },
    yAxis: [
      { type: 'value', name: '中标率(%)', position: 'left', axisLabel: { formatter: '{value}%' } },
      { type: 'value', name: '金额(万)', position: 'right' }
    ],
    series: [
      {
        name: '中标率',
        type: 'line',
        smooth: true,
        data: rates,
        itemStyle: { color: '#67C23A' },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(103, 194, 58, 0.3)' },
              { offset: 1, color: 'rgba(103, 194, 58, 0.05)' }
            ]
          }
        }
      },
      {
        name: '中标金额',
        type: 'line',
        smooth: true,
        yAxisIndex: 1,
        data: amounts,
        itemStyle: { color: '#409EFF' }
      }
    ]
  }
}

export function buildCompetitorOption(competitors) {
  const data = competitors || []
  const highlightIndex = data.findIndex((c) => c.name === '我司')

  return {
    tooltip: { trigger: 'item', formatter: '{b}: {c}万元 ({d}%)' },
    legend: { orient: 'vertical', right: '10%', top: 'center' },
    series: [
      {
        type: 'pie',
        radius: ['40%', '70%'],
        center: ['35%', '50%'],
        avoidLabelOverlap: false,
        itemStyle: { borderRadius: 10, borderColor: '#fff', borderWidth: 2 },
        label: { show: false, position: 'center' },
        emphasis: { label: { show: true, fontSize: 20, fontWeight: 'bold' } },
        labelLine: { show: false },
        data: data.map((item, index) => ({
          value: item.amount,
          name: item.name,
          itemStyle: index === highlightIndex ? { color: '#409EFF' } : undefined
        }))
      }
    ]
  }
}

export function buildProductOption(productLines, metric) {
  const data = productLines || []
  const names = data.map((d) => d.name)

  let seriesData = []
  let yAxisName = ''
  let seriesName = ''

  switch (metric) {
    case 'revenue':
      seriesData = data.map((d) => d.revenue / 100)
      yAxisName = '收入(万)'
      seriesName = '收入'
      break
    case 'rate':
      seriesData = data.map((d) => d.rate)
      yAxisName = '中标率(%)'
      seriesName = '中标率'
      break
    case 'roi':
      seriesData = data.map((d) => ((d.revenue - d.cost) / d.cost * 100).toFixed(1))
      yAxisName = 'ROI(%)'
      seriesName = 'ROI'
      break
  }

  return {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (params) => {
        const item = data[params[0].dataIndex]
        let valueText
        if (metric === 'revenue') {
          valueText = `收入: ${item.revenue}万<br/>成本: ${item.cost}万<br/>中标率: ${item.rate}%`
        } else if (metric === 'rate') {
          valueText = `中标率: ${item.rate}%<br/>收入: ${item.revenue}万<br/>成本: ${item.cost}万`
        } else {
          const roi = ((item.revenue - item.cost) / item.cost * 100).toFixed(1)
          valueText = `ROI: ${roi}%<br/>收入: ${item.revenue}万<br/>成本: ${item.cost}万`
        }
        return `${params[0].name}<br/>${valueText}`
      }
    },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: names, axisLabel: { interval: 0, rotate: 0 } },
    yAxis: {
      type: 'value',
      name: yAxisName,
      axisLabel: metric === 'rate' || metric === 'roi' ? { formatter: '{value}%' } : undefined
    },
    series: [
      {
        name: seriesName,
        type: 'bar',
        data: seriesData,
        itemStyle: {
          color: {
            type: 'linear',
            x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [
              { offset: 0, color: '#9C27B0' },
              { offset: 1, color: '#E1BEE7' }
            ]
          },
          borderRadius: [8, 8, 0, 0]
        },
        emphasis: {
          itemStyle: {
            color: {
              type: 'linear',
              x: 0, y: 0, x2: 0, y2: 1,
              colorStops: [
                { offset: 0, color: '#7B1FA2' },
                { offset: 1, color: '#CE93D8' }
              ]
            }
          }
        }
      }
    ]
  }
}

export function buildRegionOption(regionData, view) {
  const data = regionData || []
  const names = data.map((d) => d.name)

  let seriesData = []
  let yAxisName = ''
  let color = ''

  switch (view) {
    case 'amount':
      seriesData = data.map((d) => d.amount / 100)
      yAxisName = '金额(万)'
      color = '#409EFF'
      break
    case 'bids':
      seriesData = data.map((d) => d.bids)
      yAxisName = '投标数'
      color = '#67C23A'
      break
    case 'rate':
      seriesData = data.map((d) => d.rate)
      yAxisName = '中标率(%)'
      color = '#E6A23C'
      break
  }

  return {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (params) => {
        const item = data[params[0].dataIndex]
        return `${params[0].name}<br/>${yAxisName}: ${params[0].value}<br/>投标数: ${item.bids}<br/>中标率: ${item.rate}%`
      }
    },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: names },
    yAxis: {
      type: 'value',
      name: yAxisName,
      axisLabel: view === 'rate' ? { formatter: '{value}%' } : undefined
    },
    series: [
      {
        name: yAxisName,
        type: 'bar',
        data: seriesData,
        itemStyle: { color, borderRadius: [6, 6, 0, 0] },
        barWidth: '50%'
      }
    ]
  }
}
