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

describe('m1-trend-analysis/chartOptions 图例 tooltip 口径说明', () => {
  const data = [{ label: '央企', bidCount: 2, winCount: 1, winRate: 50 }]

  it('投标数/中标数/中标率图例 tooltip 文案与口径一致', () => {
    const { formatter } = buildChartOption(data, 'customerType').legend.tooltip
    expect(formatter({ name: '投标数' })).toBe(
      '项目状态为：待立项/已立项/投标中/评标中/已中标/未中标/已流标/弃标 的项目总数'
    )
    expect(formatter({ name: '中标数' })).toBe('项目状态为：已中标的项目数')
    expect(formatter({ name: '中标率' })).toBe('中标数/（已中标项目数+未中标项目数）')
  })

  it('图例 tooltip 仅在非项目状态维度开启', () => {
    expect(buildChartOption(data, 'customerType').legend.tooltip.show).toBe(true)
    expect(buildChartOption(data, 'time').legend.tooltip.show).toBe(true)
    expect(buildChartOption(data, 'projectStatus').legend.tooltip.show).toBe(false)
  })

  it('竞品维度图例 tooltip 使用竞品级口径，不出现项目状态话术', () => {
    const { formatter } = buildChartOption(data, 'competitor').legend.tooltip
    expect(formatter({ name: '投标数' })).toBe('该竞品参与的项目数')
    expect(formatter({ name: '中标数' })).toBe('该竞品参与项目中「是否中标」标记为已中标的项目数')
    expect(formatter({ name: '中标率' })).toBe('中标数/投标数（该竞品对我司的中标率）')
    expect(formatter({ name: '投标数' })).not.toContain('项目状态为')
    expect(formatter({ name: '中标数' })).not.toContain('项目状态为')
  })
})
