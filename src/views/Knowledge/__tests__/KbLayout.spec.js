// Input: 知识库 KbLayout — 简化布局容器（无顶部 tab，由侧边栏导航）
// Output: coverage for KbLayout.vue minimal layout
// Pos: src/views/Knowledge/ — layout tests

import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

describe('KbLayout', () => {
  it('renders kb-layout wrapper', async () => {
    const { default: KbLayout } = await import('../KbLayout.vue')
    const wrapper = mount(KbLayout, {
      global: {
        stubs: {
          'router-view': { template: '<div class="mock-view" />' },
        },
      },
    })

    expect(wrapper.find('.kb-layout').exists()).toBe(true)
    expect(wrapper.find('.kb-content').exists()).toBe(true)
    expect(wrapper.find('.mock-view').exists()).toBe(true)
  })
})