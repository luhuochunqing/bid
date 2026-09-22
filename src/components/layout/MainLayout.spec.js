// Input: MainLayout breadcrumb mapping after audit-logs mapping removal
// Output: assertion that live breadcrumbs render and removed route title is gone
// Pos: src/components/layout/ - main layout breadcrumb spec
import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'

const routeState = ref({
  path: '/resource/margin',
  meta: {},
  matched: [{ meta: { title: '保证金管理' } }],
})

vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    useRoute: () => routeState.value,
    useRouter: () => ({ push: vi.fn() }),
  }
})

const stubs = {
  ElContainer: { template: '<div><slot /></div>' },
  ElAside: { template: '<aside><slot /></aside>' },
  ElHeader: { template: '<header><slot /></header>' },
  ElMain: { template: '<main><slot /></main>' },
  Sidebar: true,
  Header: true,
  RouterLink: { template: '<a><slot /></a>', props: ['to'] },
  RouterView: { template: '<div class="router-view" />' },
  Transition: { template: '<div><slot /></div>' },
}

describe('MainLayout', () => {
  it('renders breadcrumb for live routes', async () => {
    routeState.value = {
      path: '/resource/margin',
      meta: {},
      matched: [{ meta: { title: '保证金管理' } }],
    }
    const { default: MainLayout } = await import('./MainLayout.vue')
    const wrapper = mount(MainLayout, { global: { stubs } })

    expect(wrapper.text()).toContain('资源管理')
    expect(wrapper.text()).toContain('保证金管理')
  })

  it('no longer maps the removed audit-logs route title', async () => {
    routeState.value = {
      path: '/audit-logs/legacy',
      meta: {},
      matched: [{ meta: { title: '历史页' } }],
    }
    const { default: MainLayout } = await import('./MainLayout.vue')
    const wrapper = mount(MainLayout, { global: { stubs } })

    // audit-logs 一级路径映射已删除，不再渲染"审计日志"面包屑
    expect(wrapper.text()).not.toContain('审计日志')
  })
})
