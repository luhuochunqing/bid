import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import DeadlineMetricCards from '../DeadlineMetricCards.vue'

describe('DeadlineMetricCards', () => {
  const sampleMetrics = [
    { key: 'reg_today', label: '今日报名截止', value: '3', variant: 'red', icon: 'Document',
      iconBg: '#FEE2E2', iconColor: '#DC2626' },
    { key: 'opening_week', label: '本周开标', value: '5', variant: 'amber', icon: 'Flag',
      iconBg: '#FEF3C7', iconColor: '#D97706' },
  ]

  it('renders metric labels and values', () => {
    const wrapper = mount(DeadlineMetricCards, { props: { metrics: sampleMetrics },
      global: { stubs: { 'el-icon': true } } })
    expect(wrapper.text()).toContain('今日报名截止')
    expect(wrapper.text()).toContain('3')
    expect(wrapper.text()).toContain('本周开标')
    expect(wrapper.text()).toContain('5')
  })

  it('emits metric-click on card click', async () => {
    const wrapper = mount(DeadlineMetricCards, { props: { metrics: sampleMetrics },
      global: { stubs: { 'el-icon': true } } })
    await wrapper.find('.metric-card').trigger('click')
    expect(wrapper.emitted('metric-click')).toBeTruthy()
    expect(wrapper.emitted('metric-click')[0][0]).toEqual(sampleMetrics[0])
  })

  it('shows empty state when no metrics and not loading', () => {
    const wrapper = mount(DeadlineMetricCards, { props: { metrics: [], loading: false },
      global: { stubs: { 'el-icon': true } } })
    expect(wrapper.text()).toContain('暂无截止节点数据')
  })

  it('shows error state with retry', () => {
    const wrapper = mount(DeadlineMetricCards, { props: { metrics: [], error: '请求超时' },
      global: { stubs: { 'el-icon': true } } })
    expect(wrapper.text()).toContain('请求超时')
    expect(wrapper.text()).toContain('重试')
  })

  it('emits retry on error button click', async () => {
    const wrapper = mount(DeadlineMetricCards, { props: { metrics: [], error: '失败' },
      global: { stubs: { 'el-icon': true } } })
    await wrapper.findComponent({ name: 'EmptyState' }).vm.$emit('action')
    expect(wrapper.emitted('retry')).toBeTruthy()
  })
})
