import { describe, it, expect } from 'vitest'
import { renderDefaultMode, renderGroupedMode } from './chartRenderer.js'

// 捕获 setOption 入参的轻量桩（渲染器为纯函数，仅依赖 setOption）
const capture = () => {
  let option = null
  return {
    inst: { setOption: (o) => { option = o } },
    get: () => option
  }
}

describe('renderDefaultMode（默认模式）', () => {
  const seed = () => {
    const c = capture()
    renderDefaultMode(c.inst, {
      categories: ['震坤行'],
      series: [
        { name: '最低折扣', data: [90] },
        { name: '平均折扣', data: [92] },
        { name: '最高折扣', data: [94] }
      ]
    })
    return c.get()
  }

  it('三段柱并排（无 stack）且柱顶显示折扣标签', () => {
    const opt = seed()
    expect(opt.series).toHaveLength(3)
    opt.series.forEach((s) => {
      expect(s.stack).toBeUndefined()
      expect(s.label.show).toBe(true)
      expect(s.label.position).toBe('top')
    })
  })

  it('y 轴固定 0-100', () => {
    const opt = seed()
    expect(opt.yAxis[0].min).toBe(0)
    expect(opt.yAxis[0].max).toBe(100)
  })

  it('标签格式化：折扣值显示数字，0 占位值不显示', () => {
    const fmt = seed().series[0].label.formatter
    expect(fmt({ value: 92.3 })).toBe('92.3')
    expect(fmt({ value: 0 })).toBe('')
    expect(fmt({ value: null })).toBe('')
  })
})

describe('renderGroupedMode（招标主体分组模式）', () => {
  const seed = () => {
    const c = capture()
    renderGroupedMode(c.inst, {
      categories: ['主体A', '主体B'],
      groups: [{ competitor: '震坤行', minData: [90, 0], avgData: [92, 0], maxData: [94, 0] }],
      overallAvgLine: [91.7, 0]
    })
    return c.get()
  }

  it('每个竞品公司的最低/平均/最高柱并排（无 stack）', () => {
    const bars = seed().series.filter((s) => s.type === 'bar')
    expect(bars).toHaveLength(3)
    bars.forEach((s) => expect(s.stack).toBeUndefined())
  })

  it('每根柱子显示折扣数字标签，0 占位值不显示', () => {
    const bars = seed().series.filter((s) => s.type === 'bar')
    bars.forEach((s) => {
      expect(s.label.show).toBe(true)
      expect(s.label.formatter({ value: 88.5 })).toBe('88.5')
      expect(s.label.formatter({ value: 0 })).toBe('')
    })
  })

  it('y 轴固定 0-100 且保留整体平均折扣折线', () => {
    const opt = seed()
    expect(opt.yAxis[0].min).toBe(0)
    expect(opt.yAxis[0].max).toBe(100)
    expect(opt.series.some((s) => s.name === '整体平均折扣' && s.type === 'line')).toBe(true)
  })

  it('空数据返回 false 且不渲染', () => {
    const c = capture()
    expect(renderGroupedMode(c.inst, { categories: [], groups: [] })).toBe(false)
    expect(c.get()).toBeNull()
  })
})
