// Input: CAManagement.vue — dual-role CA certificate management page
// Output: coverage for stat cards, filters, role-based views, and store integration
// Pos: src/views/Resource/ — page tests

import { mount, flushPromises } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useUserStore } from '@/stores/user'

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

// ── CO-393：bid-projectLeader 视角应进入简化视图，不应因 resource-ca 权限进入管理员视图 ──

describe('CAManagement — bid-projectLeader 视角', () => {
  const adminStoreMock = {
    userRole: 'admin',
    hasPermission: vi.fn((key) => key === 'resource-ca')
  }
  const projectLeaderStoreMock = {
    userRole: 'bid-projectLeader',
    hasPermission: vi.fn(() => true)
  }

  beforeEach(() => {
    setActivePinia(createPinia())
    vi.mocked(useUserStore).mockImplementation(() => projectLeaderStoreMock)
  })

  afterEach(() => {
    vi.clearAllMocks()
    vi.mocked(useUserStore).mockImplementation(() => adminStoreMock)
  })

  it('isManagerView 为 false（不应因有 resource-ca 权限而进入管理员视图）', async () => {
    const wrapper = createWrapper()
    await flushPromises()

    expect(wrapper.vm.isManagerView).toBe(false)
  })

  it('项目负责人视角不渲染统计卡片', async () => {
    const wrapper = createWrapper()
    await flushPromises()

    const html = wrapper.html()
    expect(html).not.toContain('stat-row')
  })

  it('项目负责人视角不渲染高级筛选项（CA类型/印章类型/借用状态/关键词）', async () => {
    const wrapper = createWrapper()
    await flushPromises()

    const searchCardHtml = wrapper.find('.search-card').html()
    expect(searchCardHtml).not.toContain('CA类型')
    expect(searchCardHtml).not.toContain('印章类型')
    expect(searchCardHtml).not.toContain('借用状态')
    expect(searchCardHtml).not.toContain('关键词')
  })

  it('项目负责人视角保留关联平台搜索框', async () => {
    const wrapper = createWrapper()
    await flushPromises()

    const searchCardHtml = wrapper.find('.search-card').html()
    expect(searchCardHtml).toContain('关联平台')
  })

  it('项目负责人视角不渲染「新增」「批量导入」按钮', async () => {
    const wrapper = createWrapper()
    await flushPromises()

    const headerActionsHtml = wrapper.find('.header-actions').html()
    expect(headerActionsHtml).not.toContain('新增')
    expect(headerActionsHtml).not.toContain('批量导入')
  })
})

// ── CO-409：投标专员(bid-Team)视角 —— 完整管理员视图 + 按保管员差异化操作项 ──

describe('CAManagement — bid-Team 视角', () => {
  const adminStoreMock = {
    userRole: 'admin',
    currentUser: { id: 'admin001' },
    hasPermission: vi.fn((key) => key === 'resource-ca')
  }
  // 投标专员「李四」本人是 user002，对应 mockCertificates[1] 的 custodianId='user002'
  const bidTeamStoreMock = {
    userRole: 'bid-Team',
    currentUser: { id: 'user002' },
    hasPermission: vi.fn((key) => key === 'resource-ca')
  }

  beforeEach(() => {
    setActivePinia(createPinia())
    vi.mocked(useUserStore).mockImplementation(() => bidTeamStoreMock)
  })

  afterEach(() => {
    vi.clearAllMocks()
    vi.mocked(useUserStore).mockImplementation(() => adminStoreMock)
  })

  it('canCreate 为 true（投标专员可新增/批量导入，与管理员一致）', async () => {
    const wrapper = createWrapper()
    await flushPromises()

    expect(wrapper.vm.canCreate).toBe(true)
  })

  it('isManagerView 为 true（CO-409: 投标专员进入完整管理员视图）', async () => {
    const wrapper = createWrapper()
    await flushPromises()

    expect(wrapper.vm.isManagerView).toBe(true)
  })

  it('投标专员视角渲染「新增」「批量导入」按钮', async () => {
    const wrapper = createWrapper()
    await flushPromises()

    const headerActionsHtml = wrapper.find('.header-actions').html()
    expect(headerActionsHtml).toContain('新增')
    expect(headerActionsHtml).toContain('批量导入')
  })

  it('投标专员视角渲染统计卡片（完整管理员视图）', async () => {
    const wrapper = createWrapper()
    await flushPromises()

    const html = wrapper.html()
    expect(html).toContain('stat-row')
  })

  it('canManageRow: 投标专员仅对自己保管的 CA 返回 true', async () => {
    const wrapper = createWrapper()
    await flushPromises()

    // mockCertificates[1].custodianId='user002' === 当前用户 user002 → 可管理
    expect(wrapper.vm.canManageRow(mockCertificates[1])).toBe(true)
    // mockCertificates[0].custodianId='user001' !== user002 → 不可管理
    expect(wrapper.vm.canManageRow(mockCertificates[0])).toBe(false)
  })

  it('canBorrowRow: 投标专员不可借用自己保管的 CA，可借用他人的', async () => {
    const wrapper = createWrapper()
    await flushPromises()

    // 自己保管的 CA（custodianId='user002'）→ 不可借用
    expect(wrapper.vm.canBorrowRow(mockCertificates[1])).toBe(false)
    // 他人保管的 CA（custodianId='user001'）→ 可借用
    expect(wrapper.vm.canBorrowRow(mockCertificates[0])).toBe(true)
  })
})
