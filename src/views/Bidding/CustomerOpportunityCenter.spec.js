import { defineComponent, h } from 'vue'
import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { routerPush, sourceState } = vi.hoisted(() => ({
  routerPush: vi.fn(),
  sourceState: {
    loading: false,
    isScanning: false,
    isConverting: false,
    customerOpportunityDemoEnabled: true,
    historyDrawer: false,
    filters: { status: '', keyword: '', sales: '', region: '', industry: '' },
    salesUsers: [],
    regions: [],
    industries: [],
    filteredCustomers: [],
    customerHistory: [],
    drawerStats: { totalCount: 0, totalBudget: 0, topCategory: '未知' },
    categoryStats: [],
    boardSummaries: [],
    selectedCustomer: null,
  }
}))

vi.mock('vue-router', async () => {
  const actual = await vi.importActual('vue-router')
  return {
    ...actual,
    useRouter: () => ({
      push: routerPush
    })
  }
})

vi.mock('./customer-opportunity/useCustomerOpportunityCenter.js', () => ({
  useCustomerOpportunityCenter: () => ({
    loading: sourceState.loading,
    isScanning: sourceState.isScanning,
    isConverting: sourceState.isConverting,
    customerOpportunityDemoEnabled: sourceState.customerOpportunityDemoEnabled,
    historyDrawer: sourceState.historyDrawer,
    filters: sourceState.filters,
    salesUsers: sourceState.salesUsers,
    regions: sourceState.regions,
    industries: sourceState.industries,
    filteredCustomers: sourceState.filteredCustomers,
    selectedCustomer: sourceState.selectedCustomer,
    customerHistory: sourceState.customerHistory,
    drawerStats: sourceState.drawerStats,
    categoryStats: sourceState.categoryStats,
    boardSummaries: sourceState.boardSummaries,
    rowClass: vi.fn(() => ''),
    selectCustomer: vi.fn(),
    selectFirstHighValue: vi.fn(),
    refreshInsights: vi.fn(),
    convertSelectedCustomerToProject: vi.fn(() => {
      if (sourceState.selectedCustomer?.prediction?.convertedProjectId) {
        routerPush(`/project/${sourceState.selectedCustomer.prediction.convertedProjectId}`)
      }
    }),
    goBidding: vi.fn(() => routerPush('/bidding')),
    setRecommendFilter: vi.fn()
  })
}))

vi.mock('./customer-opportunity/CustomerOpportunityBoard.vue', () => ({
  default: defineComponent({
    name: 'CustomerOpportunityBoard',
    setup() {
      return () => h('div', { 'data-test': 'board' })
    }
  })
}))

vi.mock('./customer-opportunity/CustomerOpportunityPool.vue', () => ({
  default: defineComponent({
    name: 'CustomerOpportunityPool',
    emits: ['select'],
    setup() {
      return () => h('div', { 'data-test': 'pool' })
    }
  })
}))

vi.mock('./customer-opportunity/CustomerOpportunityDetail.vue', () => ({
  default: defineComponent({
    name: 'CustomerOpportunityDetail',
    emits: ['open-history', 'convert', 'select-first', 'filter-recommend', 'go-bidding'],
    setup(_, { emit }) {
      return () =>
        h('button', {
          class: 'detail-convert',
          onClick: () => emit('convert')
        }, 'convert')
    }
  })
}))

vi.mock('./customer-opportunity/CustomerOpportunityHistoryDrawer.vue', () => ({
  default: defineComponent({
    name: 'CustomerOpportunityHistoryDrawer',
    props: ['visible'],
    emits: ['update:visible'],
    setup() {
      return () => h('div', { 'data-test': 'history-drawer' })
    }
  })
}))

import CustomerOpportunityCenter from './CustomerOpportunityCenter.vue'

function mountPage() {
  return mount(CustomerOpportunityCenter, {
    global: {
      stubs: {
        transition: false,
        'el-button': defineComponent({
          name: 'ElButton',
          props: ['loading', 'type'],
          emits: ['click'],
          inheritAttrs: false,
          setup(props, { emit, slots, attrs }) {
            return () => h('button', {
              ...attrs,
              'data-loading': props.loading,
              onClick: () => emit('click')
            }, slots.default ? slots.default() : [])
          }
        }),
        'el-icon': defineComponent({
          name: 'ElIcon',
          setup(_, { slots }) {
            return () => h('span', slots.default ? slots.default() : [])
          }
        }),
        'el-skeleton': defineComponent({
          name: 'ElSkeleton',
          setup(_, { slots }) {
            return () => h('div', slots.default ? slots.default() : [])
          }
        }),
        'el-skeleton-item': defineComponent({
          name: 'ElSkeletonItem',
          setup() {
            return () => h('div')
          }
        })
      }
    }
  })
}

describe('CustomerOpportunityCenter', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    sourceState.loading = false
    sourceState.isScanning = false
    sourceState.isConverting = false
    sourceState.customerOpportunityDemoEnabled = true
    sourceState.selectedCustomer = {
      customerId: 'c-1',
      customerName: '华北医疗集团',
      prediction: {
        convertedProjectId: ''
      }
    }
  })

  it('renders project action when a customer is selected', () => {
    const wrapper = mountPage()

    expect(wrapper.find('.btn-primary').exists()).toBe(true)
    expect(wrapper.find('.btn-primary').text()).toContain('转为正式项目')
  })

  it('delegates convert action and routes to project detail for converted customer', async () => {
    sourceState.selectedCustomer.prediction.convertedProjectId = 88
    const wrapper = mountPage()

    await wrapper.find('.detail-convert').trigger('click')

    expect(routerPush).toHaveBeenCalledWith('/project/88')
  })

  it('keeps refresh action visible while scanning state is active', () => {
    sourceState.isScanning = true
    const wrapper = mountPage()

    expect(wrapper.find('.btn-refresh').exists()).toBe(true)
    expect(wrapper.find('.scanning-overlay').exists()).toBe(true)
  })
})
