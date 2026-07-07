import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import WarehouseDrawer from './WarehouseDrawer.vue'

const mockGet = vi.fn()
vi.mock('@/api/client', () => ({
  default: {
    get: (...args) => mockGet(...args),
    post: vi.fn().mockResolvedValue({ data: { id: 1 } }),
    delete: vi.fn().mockResolvedValue({})
  }
}))

vi.mock('@/stores/user.js', () => ({
  useUserStore: () => ({ userRole: 'admin', currentUser: { role: 'admin' } })
}))

const stubs = {
  'el-drawer': { template: '<div class="el-drawer-stub"><slot name="header" /><slot /></div>' },
  'el-tabs': { template: '<div><slot /></div>' },
  'el-tab-pane': { template: '<div><slot /></div>' },
  'el-descriptions': { template: '<div><slot /></div>' },
  'el-descriptions-item': { template: '<span class="desc-item"><slot /></span>', props: ['label', 'span'] },
  'el-tag': { template: '<span class="tag-stub"><slot /></span>', props: ['size', 'type'] },
  'el-divider': { template: '<div></div>' },
  'el-button': { template: '<button><slot /></button>' },
  'el-select': { template: '<select><slot /></select>', props: ['modelValue', 'size'] },
  'el-option': { template: '<option>{{ label }}</option>', props: ['label', 'value'] },
  'el-table': { template: '<div class="table-stub"></div>' },
  'el-table-column': { template: '<div class="col-stub"></div>' },
  'el-pagination': { template: '<div class="pager-stub"></div>' },
  'el-icon': true,
  'Edit': true,
  'Upload': true,
  'Lock': true,
  'RefreshRight': true
}

const detailWithLease = {
  id: 1, name: '测试仓库', type: 'SELF_OPERATED', region: '华东', province: '北京市',
  address: '朝阳区', area: 100, contactPerson: '张三',
  hasPropertyCert: true, hasInvoice: false, hasPhotos: true, hasLeaseContract: true,
  status: 'IN_USE', certRemarks: '', attachments: []
}

describe('WarehouseDrawer', () => {
  beforeEach(() => {
    mockGet.mockReset()
    // detail 接口 + logs 接口
    mockGet.mockResolvedValue({ data: detailWithLease })
  })

  it('detail.hasLeaseContract=true 时显示租赁合同"有"', async () => {
    const wrapper = mount(WarehouseDrawer, {
      props: { modelValue: false, warehouseId: 1 },
      global: { stubs }
    })
    await wrapper.setProps({ modelValue: true })
    await flushPromises()
    const html = wrapper.html()
    expect(html).toContain('租赁合同')
    expect(html).toContain('有')
  })

  it('detail.hasLeaseContract=false 时显示租赁合同"无"', async () => {
    mockGet.mockResolvedValue({ data: { ...detailWithLease, hasLeaseContract: false } })
    const wrapper = mount(WarehouseDrawer, {
      props: { modelValue: false, warehouseId: 2 },
      global: { stubs }
    })
    await wrapper.setProps({ modelValue: true })
    await flushPromises()
    const html = wrapper.html()
    expect(html).toContain('租赁合同')
    expect(html).toContain('无')
  })

  it('附件类型下拉选项包含"租赁合同"（ATTACH_TYPE_MAP）', async () => {
    const wrapper = mount(WarehouseDrawer, {
      props: { modelValue: false, warehouseId: 3 },
      global: { stubs }
    })
    await wrapper.setProps({ modelValue: true })
    await flushPromises()
    // ATTACH_TYPE_MAP 包含 LEASE_CONTRACT: '租赁合同'，通过渲染下拉选项验证
    const options = wrapper.findAll('option')
    const labels = options.map(o => o.text())
    expect(labels).toContain('租赁合同')
  })

  it('附件管理 tab 渲染"类型筛选"下拉，默认包含"全部"选项', async () => {
    const wrapper = mount(WarehouseDrawer, {
      props: { modelValue: false, warehouseId: 4 },
      global: { stubs }
    })
    await wrapper.setProps({ modelValue: true })
    await flushPromises()
    const html = wrapper.html()
    expect(html).toContain('类型筛选')
    const labels = wrapper.findAll('option').map(o => o.text())
    expect(labels).toContain('全部')
    expect(labels).toContain('产权证')
    expect(labels).toContain('发票')
    expect(labels).toContain('内外照片')
    expect(labels).toContain('租赁合同')
  })

  it('抽屉关闭再打开时 filterType 重置为 ALL', async () => {
    const wrapper = mount(WarehouseDrawer, {
      props: { modelValue: false, warehouseId: 5 },
      global: { stubs }
    })
    await wrapper.setProps({ modelValue: true })
    await flushPromises()
    // 模拟用户切换筛选类型后关闭抽屉
    wrapper.vm.filterType = 'INVOICE'
    await wrapper.setProps({ modelValue: false })
    await flushPromises()
    await wrapper.setProps({ modelValue: true })
    await flushPromises()
    // 重新打开后 filterType 应已重置为 ALL（通过下次渲染的筛选状态推断）
    expect(wrapper.vm.filterType).toBe('ALL')
  })
})
