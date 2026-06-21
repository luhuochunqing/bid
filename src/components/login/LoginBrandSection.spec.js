import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import LoginBrandSection from './LoginBrandSection.vue'

describe('LoginBrandSection', () => {
  it('renders brand section with logo, hero, features and footer', () => {
    const wrapper = mount(LoginBrandSection)

    expect(wrapper.find('.brand-section').exists()).toBe(true)
    expect(wrapper.find('.logo-text').text()).toBe('西域数智化投标管理平台')
    expect(wrapper.find('.hero-title').text()).toContain('专业高效的')
    expect(wrapper.find('.hero-desc').text()).toContain('AI 驱动')

    const features = wrapper.findAll('.feature-text')
    expect(features).toHaveLength(4)
    expect(features[0].text()).toBe('智能标书分析')
    expect(features[1].text()).toBe('中标率预测')
    expect(features[2].text()).toBe('团队协作')
    expect(features[3].text()).toBe('数据报表')

    expect(wrapper.find('.brand-footer').text()).toContain('© 西域数智化投标管理平台')
  })

  it('exposes feature color variants', () => {
    const wrapper = mount(LoginBrandSection)
    expect(wrapper.find('.feature-blue').exists()).toBe(true)
    expect(wrapper.find('.feature-green').exists()).toBe(true)
    expect(wrapper.find('.feature-orange').exists()).toBe(true)
    expect(wrapper.find('.feature-purple').exists()).toBe(true)
  })
})
