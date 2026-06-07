// Input: 知识库 KbLayout — tab switching, route sync
// Output: coverage for KbLayout.vue tab computed and tab click handler
// Pos: src/views/Knowledge/ — layout tests

import { mount, shallowMount } from '@vue/test-utils'
import { describe, expect, it, vi, beforeEach } from 'vitest'

// Mock vue-router
const mockRoute = { path: '/knowledge/archive' }
const mockRouter = { push: vi.fn() }

vi.mock('vue-router', () => ({
  useRoute: () => mockRoute,
  useRouter: () => mockRouter,
}))

describe('KbLayout', () => {
  beforeEach(() => {
    mockRoute.path = '/knowledge/archive'
    mockRouter.push.mockClear()
  })

  it('renders all 7 tab labels', async () => {
    const { default: KbLayout } = await import('../KbLayout.vue')
    const wrapper = mount(KbLayout, {
      global: {
        stubs: {
          'el-tabs': { template: '<div><slot /></div>' },
          'el-tab-pane': {
            props: ['label', 'name'],
            template: '<section class="tab-pane" :data-name="name"><span>{{ label }}</span><slot /></section>',
          },
          'router-view': { template: '<div />' },
        },
      },
    })

    expect(wrapper.text()).toContain('档案台账')
    expect(wrapper.text()).toContain('资质库')
    expect(wrapper.text()).toContain('人员库')
    expect(wrapper.text()).toContain('业绩库')
    expect(wrapper.text()).toContain('品牌授权')
    expect(wrapper.text()).toContain('案例库')
    expect(wrapper.text()).toContain('模板库')
  })

  it('computes activeTab based on route path', async () => {
    const { default: KbLayout } = await import('../KbLayout.vue')

    mockRoute.path = '/knowledge/qualification'
    const wrapper = mount(KbLayout, {
      global: {
        stubs: {
          'el-tabs': { template: '<div><slot /></div>' },
          'el-tab-pane': { template: '<div><slot /></div>' },
          'router-view': { template: '<div />' },
        },
      },
    })

    expect(wrapper.vm.activeTab).toBe('qualification')
  })

  it('defaults to archive tab for unknown paths', async () => {
    const { default: KbLayout } = await import('../KbLayout.vue')

    mockRoute.path = '/knowledge/unknown'
    const wrapper = mount(KbLayout, {
      global: {
        stubs: {
          'el-tabs': { template: '<div><slot /></div>' },
          'el-tab-pane': { template: '<div><slot /></div>' },
          'router-view': { template: '<div />' },
        },
      },
    })

    expect(wrapper.vm.activeTab).toBe('archive')
  })

  it('clears active tab on case detail route', async () => {
    const { default: KbLayout } = await import('../KbLayout.vue')

    mockRoute.path = '/knowledge/case/detail/123'
    const wrapper = mount(KbLayout, {
      global: {
        stubs: {
          'el-tabs': { template: '<div><slot /></div>' },
          'el-tab-pane': { template: '<div><slot /></div>' },
          'router-view': { template: '<div />' },
        },
      },
    })

    expect(wrapper.vm.activeTab).toBe('')
  })

  it('navigates on tab click', async () => {
    const { default: KbLayout } = await import('../KbLayout.vue')

    const wrapper = mount(KbLayout, {
      global: {
        stubs: {
          'el-tabs': { template: '<div><slot /></div>' },
          'el-tab-pane': { template: '<div><slot /></div>' },
          'router-view': { template: '<div />' },
        },
      },
    })

    wrapper.vm.handleTabClick({ props: { name: 'template' } })
    expect(mockRouter.push).toHaveBeenCalledWith('/knowledge/template')
  })
})
