import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import QuickEntrySection from '../QuickEntrySection.vue'

describe('QuickEntrySection', () => {
  it('renders three entry cards', () => {
    const wrapper = mount(QuickEntrySection, { 
      props: { canCreateProject: true, canViewTenders: true },
      global: { stubs: { 'el-icon': true } }
    })
    expect(wrapper.text()).toContain('发起项目')
    expect(wrapper.text()).toContain('查看标讯')
    expect(wrapper.text()).toContain('处理待办')
  })

  it('hides project card when no permission', () => {
    const wrapper = mount(QuickEntrySection, { 
      props: { canCreateProject: false, canViewTenders: true },
      global: { stubs: { 'el-icon': true } }
    })
    expect(wrapper.text()).not.toContain('发起项目')
    expect(wrapper.text()).toContain('查看标讯')
  })

  it('emits handle-todos on click', async () => {
    const wrapper = mount(QuickEntrySection, { 
      props: { canCreateProject: true, canViewTenders: true },
      global: { stubs: { 'el-icon': true } }
    })
    // Find the last card (处理待办)
    const cards = wrapper.findAll('.quick-entry-card')
    await cards[cards.length - 1].trigger('click')
    expect(wrapper.emitted('handle-todos')).toBeTruthy()
  })
})
