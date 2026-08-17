import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import PieChart from './PieChart.vue'

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

const option = () => ({ series: [{ type: 'pie', data: [{ value: 1 }] }] })

describe('PieChart', () => {
  it('挂载后 init 并 setOption 初始 option', () => {
    const opt = option()
    const wrapper = mount(PieChart, { props: { option: opt } })
    expect(instances.at(-1).setOption).toHaveBeenCalledWith(opt)
    wrapper.unmount()
  })

  it('option 变化触发 setOption(option, true)', async () => {
    const wrapper = mount(PieChart, { props: { option: option() } })
    await wrapper.setProps({ option: { series: [{ type: 'pie', data: [{ value: 9 }] }] } })
    expect(instances.at(-1).setOption).toHaveBeenLastCalledWith(expect.anything(), true)
    wrapper.unmount()
  })

  it('转发图表 click 事件为 chart-click', () => {
    const wrapper = mount(PieChart, { props: { option: option() } })
    const clickCb = instances.at(-1).on.mock.calls.find(([evt]) => evt === 'click')?.[1]
    clickCb({ name: 'x' })
    expect(wrapper.emitted('chart-click')?.[0]).toEqual([{ name: 'x' }])
    wrapper.unmount()
  })

  it('卸载时 dispose 实例', () => {
    const wrapper = mount(PieChart, { props: { option: option() } })
    const inst = instances.at(-1)
    wrapper.unmount()
    expect(inst.dispose).toHaveBeenCalled()
  })
})
