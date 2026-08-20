import { describe, expect, it } from 'vitest'
import { buildChartOption } from './chartOptions.js'

describe('m1-trend-analysis/chartOptions 项目状态维度 X 轴中文映射', () => {
  it('待立项/已立项等全部状态枚举在 X 轴显示中文标签', () => {
    const data = [
      { label: 'PENDING_INITIATION', bidCount: 2 },
      { label: 'INITIATED', bidCount: 3 },
      { label: 'BIDDING', bidCount: 4 },
      { label: 'WON', bidCount: 5 }
    ]
    const option = buildChartOption(data, 'projectStatus')
    expect(option.xAxis.data).toEqual(['待立项', '已立项', '投标中', '已中标'])
  })

  it('非状态维度 X 轴不做枚举映射（原样透传）', () => {
    const data = [{ label: '央企', bidCount: 2, winCount: 1, winRate: 50 }]
    const option = buildChartOption(data, 'customerType')
    expect(option.xAxis.data).toEqual(['央企'])
  })
})
