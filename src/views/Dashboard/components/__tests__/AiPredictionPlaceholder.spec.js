import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import AiPredictionPlaceholder from '../AiPredictionPlaceholder.vue'

describe('AiPredictionPlaceholder', () => {
  it('shows planning status', () => {
    const wrapper = mount(AiPredictionPlaceholder, {
      global: { stubs: { 'el-icon': true, 'el-tag': { template: '<span><slot /></span>' } } }
    })
    expect(wrapper.text()).toContain('AI 商机预测')
    expect(wrapper.text()).toContain('规划中')
  })
})
