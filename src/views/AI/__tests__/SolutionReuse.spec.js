// Input: 历史方案提取与复用页面 — search, results, loading/empty/error states
// Output: coverage for SolutionReuse.vue states and interactions
// Pos: src/views/AI/ — page tests

import { mount, shallowMount } from '@vue/test-utils'
import { describe, expect, it, vi, beforeEach } from 'vitest'

const mockResults = [
  { id: 1, title: '测试方案一', customerName: '客户A', industry: 'INDUSTRY', matchScore: 85, projectDate: '2026-01-15', description: '工业电商技术方案', content: '详细内容...', sourceProject: '项目A' },
  { id: 2, title: '测试方案二', customerName: '客户B', industry: 'GOVERNMENT', matchScore: 72, projectDate: '2026-02-20', description: '政府采购方案', content: '详细内容...', sourceProject: '项目B' },
]

function mockFetch(data, ok = true) {
  return vi.fn().mockResolvedValue({
    ok,
    json: () => Promise.resolve(data),
  })
}

const baseStubs = {
  'el-button': { template: '<button @click="$emit(\'click\')"><slot /></button>' },
  'el-card': { template: '<div><slot /></div>' },
  'el-input': { template: '<input />' },
  'el-select': { template: '<div><slot /></div>' },
  'el-option': { template: '<div />' },
  'el-form': { template: '<form><slot /></form>' },
  'el-form-item': { template: '<div><slot /></div>' },
  'el-icon': { template: '<i><slot /></i>' },
  'el-tag': { template: '<span><slot /></span>' },
  SolutionReuseDrawer: { template: '<div />' },
}

const { default: SolutionReuse } = await import('../SolutionReuse.vue')

beforeEach(() => {
  vi.stubGlobal('fetch', mockFetch({ success: true, data: { content: mockResults } }))
})

describe('SolutionReuse', () => {
  it('renders initial state with search prompt', () => {
    const wrapper = shallowMount(SolutionReuse, {
      global: {
        stubs: baseStubs,
      },
    })
    expect(wrapper.text()).toContain('输入关键词开始搜索')
  })

  it('searches and displays results', async () => {
    const wrapper = mount(SolutionReuse, {
      global: {
        stubs: baseStubs,
        mocks: { $router: { push: vi.fn() } },
      },
    })

    // Trigger search
    await wrapper.vm.handleSearch()
    await wrapper.vm.$nextTick()

    expect(wrapper.text()).toContain('测试方案一')
    expect(wrapper.text()).toContain('测试方案二')
    expect(wrapper.text()).toContain('客户A')
    expect(wrapper.text()).toContain('客户B')
  })

  it('handles empty search results', async () => {
    vi.stubGlobal('fetch', mockFetch({ success: true, data: { content: [] } }))
    const wrapper = mount(SolutionReuse, {
      global: {
        stubs: baseStubs,
      },
    })

    await wrapper.vm.handleSearch()
    await wrapper.vm.$nextTick()

    expect(wrapper.text()).toContain('未找到匹配方案')
  })

  it('handles fetch error gracefully', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('Network error')))
    const wrapper = mount(SolutionReuse, {
      global: {
        stubs: baseStubs,
      },
    })

    await wrapper.vm.handleSearch()
    await wrapper.vm.$nextTick()

    expect(wrapper.text()).toContain('搜索失败')
  })

  it('reset clears search and results', async () => {
    const wrapper = mount(SolutionReuse, {
      global: {
        stubs: baseStubs,
      },
    })

    await wrapper.vm.handleSearch()
    await wrapper.vm.$nextTick()
    wrapper.vm.handleReset()
    await wrapper.vm.$nextTick()

    expect(wrapper.text()).toContain('输入关键词开始搜索')
  })
})
