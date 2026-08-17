import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import WinScoreChart from './WinScoreChart.vue'

const instances = []

vi.mock('@/utils/echarts', () => ({
  default: {
    init: vi.fn((el) => {
      const inst = { el, setOption: vi.fn(), on: vi.fn(), resize: vi.fn(), dispose: vi.fn() }
      instances.push(inst)
      return inst
    })
  }
}))

describe('WinScoreChart', () => {
  it('挂载后 setOption 生成 radar series', () => {
    const wrapper = mount(WinScoreChart, {
      props: {
        scores: [80],
        dimensionScores: [
          { name: '客户关系', score: 90 },
          { name: '需求匹配', score: 70 }
        ]
      }
    })
    const opt = instances.at(-1).setOption.mock.calls[0][0]
    expect(opt.series[0].type).toBe('radar')
    expect(opt.radar.indicator.map((i) => i.name)).toEqual(['客户关系', '需求匹配'])
    expect(opt.series[0].data[0].value).toEqual([90, 70])
    wrapper.unmount()
  })

  it('无 dimensionScores 时使用默认五维', () => {
    const wrapper = mount(WinScoreChart, { props: { scores: [], dimensionScores: [] } })
    const opt = instances.at(-1).setOption.mock.calls[0][0]
    expect(opt.radar.indicator).toHaveLength(5)
    expect(opt.series[0].data[0].value).toEqual([70, 70, 70, 70, 70])
    wrapper.unmount()
  })

  it('dimensionScores 变化触发 setOption(option, true)', async () => {
    const wrapper = mount(WinScoreChart, {
      props: { scores: [], dimensionScores: [{ name: 'a', score: 60 }] }
    })
    await wrapper.setProps({ dimensionScores: [{ name: 'a', score: 95 }] })
    expect(instances.at(-1).setOption).toHaveBeenLastCalledWith(expect.anything(), true)
    wrapper.unmount()
  })

  it('卸载时 dispose 实例', () => {
    const wrapper = mount(WinScoreChart, { props: { scores: [], dimensionScores: [] } })
    const inst = instances.at(-1)
    wrapper.unmount()
    expect(inst.dispose).toHaveBeenCalled()
  })
})
