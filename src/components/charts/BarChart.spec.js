import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import BarChart from './BarChart.vue'

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

const option = () => ({ series: [{ type: 'bar', data: [1, 2, 3] }] })

describe('BarChart', () => {
  it('挂载后 init 并 setOption 初始 option', () => {
    const opt = option()
    const wrapper = mount(BarChart, { props: { option: opt } })
    const inst = instances.at(-1)
    expect(inst.setOption).toHaveBeenCalledWith(opt)
    wrapper.unmount()
  })

  it('option 变化触发 setOption(option, true)', async () => {
    const wrapper = mount(BarChart, { props: { option: option() } })
    await wrapper.setProps({ option: { series: [{ type: 'bar', data: [9] }] } })
    expect(instances.at(-1).setOption).toHaveBeenLastCalledWith(expect.anything(), true)
    wrapper.unmount()
  })

  it('转发图表 click 事件为 chart-click', () => {
    const wrapper = mount(BarChart, { props: { option: option() } })
    const clickCb = instances.at(-1).on.mock.calls.find(([evt]) => evt === 'click')?.[1]
    clickCb({ dataIndex: 0 })
    expect(wrapper.emitted('chart-click')?.[0]).toEqual([{ dataIndex: 0 }])
    wrapper.unmount()
  })

  it('卸载时 dispose 实例', () => {
    const wrapper = mount(BarChart, { props: { option: option() } })
    const inst = instances.at(-1)
    wrapper.unmount()
    expect(inst.dispose).toHaveBeenCalled()
  })
})
