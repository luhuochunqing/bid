// Input: CAManagement.vue — dual-role CA certificate management page
// Output: coverage for stat cards, filters, role-based views, and store integration
// Pos: src/views/Resource/ — page tests

import { mount, flushPromises } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { createPinia } from 'pinia'

// Use vi.hoisted() so mock factories can reference the data at hoist time
const { mockCertificates, mockOverview } = vi.hoisted(() => ({
  mockCertificates: [
    {
      id: 1, platformIds: ['政采云'], caType: 'ENTITY_CA', caTypeLabel: '实体CA',
      sealType: 'OFFICIAL_SEAL', sealTypeLabel: '公章',
      electronicAccount: '', caPasswordMasked: '******',
      expiryDate: '2027-06-01', remainingDays: 370,
      caPlatformUrl: 'https://zcy.gov.cn',
      custodianName: '张三', custodianId: 'user001',
      borrowStatus: 'IN_STOCK', borrowStatusLabel: '在库',
      currentBorrowerName: '',
      status: 'ACTIVE', statusLabel: '有效',
      remark: ''
    },
    {
      id: 2, platformIds: ['深圳政采'], caType: 'ENTITY_CA', caTypeLabel: '实体CA',
      sealType: 'LEGAL_PERSON_SEAL', sealTypeLabel: '法人章',
      electronicAccount: '', caPasswordMasked: '******',
      expiryDate: '2026-06-15', remainingDays: 17,
      caPlatformUrl: 'https://sz.gov.cn',
      custodianName: '李四', custodianId: 'user002',
      borrowStatus: 'BORROWED', borrowStatusLabel: '已借出',
      currentBorrowerName: '王五',
      status: 'EXPIRING', statusLabel: '即将到期',
      remark: ''
    },
    {
      id: 3, platformIds: ['北京政采'], caType: 'ELECTRONIC_CA', caTypeLabel: '电子CA',
      sealType: 'OFFICIAL_SEAL', sealTypeLabel: '公章',
      electronicAccount: 'bj_account', caPasswordMasked: '******',
      expiryDate: '2025-03-01', remainingDays: -455,
      caPlatformUrl: 'https://bj.gov.cn',
      custodianName: '赵六', custodianId: 'user003',
      borrowStatus: 'IN_STOCK', borrowStatusLabel: '在库',
      currentBorrowerName: '',
      status: 'EXPIRED', statusLabel: '已过期',
      remark: ''
    },
    {
      id: 4, platformIds: ['上海政采'], caType: 'ENTITY_CA', caTypeLabel: '实体CA',
      sealType: 'LEGAL_SIGN', sealTypeLabel: '法人签字',
      electronicAccount: '', caPasswordMasked: '******',
      expiryDate: '2028-12-01', remainingDays: 920,
      caPlatformUrl: 'https://sh.gov.cn',
      custodianName: '孙七', custodianId: 'user004',
      borrowStatus: 'BORROWED', borrowStatusLabel: '已借出',
      currentBorrowerName: '周八',
      status: 'ACTIVE', statusLabel: '有效',
      remark: ''
    },
  ],
  mockOverview: { total: 4, expiring: 1, expired: 1, borrowed: 2 }
}))

// Mock stores
vi.mock('@/stores/user', () => ({
  useUserStore: vi.fn(() => ({
    userRole: 'admin',
    hasPermission: vi.fn((key) => key === 'resource-ca')
  }))
}))

vi.mock('@/stores/ca', () => ({
  useCaStore: vi.fn(() => ({
    certificates: [...mockCertificates],
    overview: { ...mockOverview },
    listLoading: false,
    loadCertificates: vi.fn().mockResolvedValue({ data: [...mockCertificates] }),
    loadOverview: vi.fn().mockResolvedValue({ data: { ...mockOverview } }),
    createCertificate: vi.fn().mockResolvedValue({ success: true }),
    updateCertificate: vi.fn().mockResolvedValue({ success: true }),
    deactivateCertificate: vi.fn().mockResolvedValue({ success: true }),
    borrowCertificate: vi.fn().mockResolvedValue({ success: true }),
    returnCertificate: vi.fn().mockResolvedValue({ success: true })
  }))
}))

vi.mock('@/api/modules/ca.js', () => ({
  caApi: {
    getList: vi.fn().mockResolvedValue({ data: [...mockCertificates] }),
    getOverview: vi.fn().mockResolvedValue({ data: { ...mockOverview } }),
    getBorrowApplications: vi.fn().mockResolvedValue({ data: [] }),
    getOperationEvents: vi.fn().mockResolvedValue({ data: [] }),
    borrow: vi.fn().mockResolvedValue({ success: true }),
    returnCa: vi.fn().mockResolvedValue({ success: true }),
    deactivate: vi.fn().mockResolvedValue({ success: true })
  }
}))

vi.mock('element-plus', () => ({
  ElMessage: { info: vi.fn(), success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  ElMessageBox: { confirm: vi.fn().mockResolvedValue('confirm'), prompt: vi.fn().mockResolvedValue({ value: 'test' }) }
}))

import CAManagement from '../CAManagement.vue'

const stubs = {
  'el-card': { template: '<div><slot name="header" /><slot /></div>' },
  'el-table': { template: '<div><slot /></div>' },
  'el-table-column': { template: '<div />' },
  'el-tag': { template: '<span><slot /></span>' },
  'el-button': { props: ['disabled', 'loading', 'type', 'link'], template: '<button :disabled="disabled"><slot /></button>' },
  'el-form': { template: '<form><slot /></form>' },
  'el-form-item': { template: '<div><slot /></div>' },
  'el-select': { template: '<div><slot /></div>' },
  'el-option': { template: '<div />' },
  'el-input': { template: '<input />' },
  'el-icon': { template: '<i><slot /></i>' },
  'el-empty': { template: '<div><slot /></div>' },
  'el-dialog': { template: '<div v-if="modelValue"><slot /></div>', props: ['modelValue'] },
  'el-drawer': { template: '<div v-if="modelValue"><slot /></div>', props: ['modelValue'] },
  'el-date-picker': { template: '<input />' },
  'el-descriptions': { template: '<div><slot /></div>' },
  'el-descriptions-item': { template: '<div><slot /></div>' },
  'el-tabs': { template: '<div><slot /></div>' },
  'el-tab-pane': { template: '<div><slot /></div>' },
  'el-timeline': { template: '<div><slot /></div>' },
  'el-timeline-item': { template: '<div><slot /></div>' },
  'el-alert': { template: '<div><slot /></div>' },
  CADetailDrawer: { template: '<div />' },
  CAFormDialog: { template: '<div />' },
  CABorrowDialog: { template: '<div />' },
  CAReturnDialog: { template: '<div />' },
}

function createWrapper() {
  return mount(CAManagement, {
    global: { stubs, plugins: [createPinia()] }
  })
}

describe('CAManagement', () => {
  it('computes stat cards correctly (admin view)', async () => {
    const wrapper = createWrapper()
    await flushPromises()

    const vm = wrapper.vm
    expect(vm.overview.total).toBe(4)
    expect(vm.overview.expiring).toBe(1)
    expect(vm.overview.expired).toBe(1)
    expect(vm.overview.borrowed).toBe(2)

    const cards = vm.statCards
    expect(cards.find(c => c.key === 'total')?.value).toBe(4)
    expect(cards.find(c => c.key === 'expiring')?.value).toBe(1)
    expect(cards.find(c => c.key === 'expired')?.value).toBe(1)
    expect(cards.find(c => c.key === 'borrowed')?.value).toBe(2)
  })

  it('detects admin/manager view correctly', async () => {
    const wrapper = createWrapper()
    await flushPromises()

    // With admin role mocked, isManagerView should be true
    expect(wrapper.vm.isManagerView).toBe(true)
  })

  it('filters CA list by caType', async () => {
    const wrapper = createWrapper()
    await flushPromises()

    // Set preloaded data
    wrapper.vm.filters.caType = 'ELECTRONIC_CA'
    wrapper.vm.applyFilters()
    await wrapper.vm.$nextTick()

    expect(wrapper.vm.filteredData.length).toBe(1)
    expect(wrapper.vm.filteredData[0].caType).toBe('ELECTRONIC_CA')
  })

  it('filters CA list by sealType', async () => {
    const wrapper = createWrapper()
    await flushPromises()

    wrapper.vm.filters.sealType = 'LEGAL_SIGN'
    wrapper.vm.applyFilters()
    await wrapper.vm.$nextTick()

    expect(wrapper.vm.filteredData.length).toBe(1)
    expect(wrapper.vm.filteredData[0].sealTypeLabel).toBe('法人签字')
  })

  it('filters CA list by borrow status', async () => {
    const wrapper = createWrapper()
    await flushPromises()

    wrapper.vm.filters.borrowStatus = 'BORROWED'
    wrapper.vm.applyFilters()
    await wrapper.vm.$nextTick()

    expect(wrapper.vm.filteredData.length).toBe(2)
    expect(wrapper.vm.filteredData.every(c => c.borrowStatus === 'BORROWED')).toBe(true)
  })

  it('filters CA list by keyword (platform)', async () => {
    const wrapper = createWrapper()
    await flushPromises()

    wrapper.vm.filters.keyword = '深圳'
    wrapper.vm.applyFilters()
    await wrapper.vm.$nextTick()

    expect(wrapper.vm.filteredData.length).toBe(1)
    expect(wrapper.vm.filteredData[0].platformIds).toContain('深圳政采')
  })

  it('resets all filters', async () => {
    const wrapper = createWrapper()
    await flushPromises()

    wrapper.vm.filters.caType = 'ELECTRONIC_CA'
    wrapper.vm.filters.borrowStatus = 'BORROWED'
    wrapper.vm.filters.keyword = 'test'
    wrapper.vm.applyFilters()
    wrapper.vm.resetFilters()
    await wrapper.vm.$nextTick()

    expect(wrapper.vm.filters.caType).toBe('')
    expect(wrapper.vm.filters.borrowStatus).toBe('')
    expect(wrapper.vm.filters.keyword).toBe('')
    expect(wrapper.vm.appliedFilters.caType).toBe('')
    expect(wrapper.vm.appliedFilters.borrowStatus).toBe('')
    expect(wrapper.vm.appliedFilters.keyword).toBe('')
  })
})
