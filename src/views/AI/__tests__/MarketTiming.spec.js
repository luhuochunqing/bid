// Input: 商机时间预测页面 — prediction cards, stats, search filter, error handling
// Output: coverage for MarketTiming.vue states and computed properties
// Pos: src/views/AI/ — page tests

import { mount } from '@vue/test-utils'
import { describe, expect, it, vi, beforeEach } from 'vitest'

const mockPredictions = [
  { purchaserHash: 'hash1', purchaserName: '业主A', hasData: true, confidence: 0.85, nextTenderDate: '2026-06-15', historicalCount: 5, note: '预计六月有招标' },
  { purchaserHash: 'hash2', purchaserName: '业主B', hasData: true, confidence: 0.65, nextTenderDate: '2026-07-01', historicalCount: 3, note: '预计七月有招标' },
  { purchaserHash: 'hash3', purchaserName: '业主C', hasData: false, confidence: 0, nextTenderDate: null, historicalCount: 0, note: '' },
]

function mockFetch(data, ok = true) {
  return vi.fn().mockResolvedValue({
    ok,
    json: () => Promise.resolve(data),
  })
}

beforeEach(() => {
  // Default: batch prediction returns mock predictions
  vi.stubGlobal('fetch', mockFetch({ success: true, data: mockPredictions }))
})

describe('MarketTiming', () => {
  it('renders summary stats correctly', async () => {
    const { default: MarketTiming } = await import('../MarketTiming.vue')
    const wrapper = mount(MarketTiming, {
      global: {
        stubs: {
          'el-button': { template: '<button><slot /></button>' },
          'el-card': { template: '<div><slot /></div>' },
          'el-tag': { template: '<span><slot /></span>' },
          'el-input': { template: '<input />' },
          'el-icon': { template: '<i><slot /></i>' },
          'el-empty': { template: '<div><slot /></div>' },
          'el-progress': { template: '<div />' },
        },
      },
    })

    await wrapper.vm.fetchPredictions()
    await wrapper.vm.$nextTick()

    expect(wrapper.vm.predictions.length).toBe(3)
    expect(wrapper.vm.highConfidenceCount).toBe(1) // Only owner A (0.85 >= 0.7)
  })

  it('renders error message on API failure', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: false,
      json: () => Promise.resolve({ message: '预测接口错误' }),
    }))

    const { default: MarketTiming } = await import('../MarketTiming.vue')
    const wrapper = mount(MarketTiming, {
      global: {
        stubs: {
          'el-button': { template: '<button><slot /></button>' },
          'el-card': { template: '<div><slot /></div>' },
          'el-tag': { template: '<span><slot /></span>' },
          'el-input': { template: '<input />' },
          'el-icon': { template: '<i><slot /></i>' },
          'el-empty': { template: '<div><slot /></div>' },
          'el-progress': { template: '<div />' },
        },
      },
    })

    await wrapper.vm.fetchPredictions()
    await wrapper.vm.$nextTick()

    expect(wrapper.vm.errorMsg).toBeTruthy()
  })

  it('filters predictions by keyword', async () => {
    const { default: MarketTiming } = await import('../MarketTiming.vue')
    const wrapper = mount(MarketTiming, {
      global: {
        stubs: {
          'el-button': { template: '<button><slot /></button>' },
          'el-card': { template: '<div><slot /></div>' },
          'el-tag': { template: '<span><slot /></span>' },
          'el-input': { template: '<input />' },
          'el-icon': { template: '<i><slot /></i>' },
          'el-empty': { template: '<div><slot /></div>' },
          'el-progress': { template: '<div />' },
        },
      },
    })

    await wrapper.vm.fetchPredictions()
    await wrapper.vm.$nextTick()

    wrapper.vm.searchKeyword = '业主A'
    await wrapper.vm.$nextTick()

    expect(wrapper.vm.filteredPredictions.length).toBe(1)
    expect(wrapper.vm.filteredPredictions[0].purchaserName).toBe('业主A')
  })

  it('shows empty state when no predictions data', async () => {
    // Mock tenders fetch returning empty
    vi.stubGlobal('fetch', mockFetch({ success: true, data: { content: [] } }))

    const { default: MarketTiming } = await import('../MarketTiming.vue')
    const wrapper = mount(MarketTiming, {
      global: {
        stubs: {
          'el-button': { template: '<button><slot /></button>' },
          'el-card': { template: '<div><slot /></div>' },
          'el-tag': { template: '<span><slot /></span>' },
          'el-input': { template: '<input />' },
          'el-icon': { template: '<i><slot /></i>' },
          'el-empty': { template: '<div><slot /></div>' },
          'el-progress': { template: '<div />' },
        },
      },
    })

    await wrapper.vm.fetchPredictions()
    await wrapper.vm.$nextTick()

    expect(wrapper.vm.predictions.length).toBe(0)
  })
})
