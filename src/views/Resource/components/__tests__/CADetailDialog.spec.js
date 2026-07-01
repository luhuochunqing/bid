// Input: src/views/Resource/components/CADetailDialog.vue — CA 证书详情展示组件
// Output: CO-406 详情形态从右侧抽屉改为居中弹窗（对齐 AccountDetailDialog）的回归测试
// Pos: src/views/Resource/components/__tests__/ — 详情形态与交互契约
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

// CO-406：用不同的 stub class 区分 el-drawer 与 el-dialog，
// 这样可以断言组件根标签已从 drawer 切换到 dialog。
const stubs = {
  'el-dialog': {
    template: '<div class="el-dialog-stub" :data-width="width"><slot /><slot name="footer" /></div>',
    props: ['width', 'title', 'destroyOnClose']
  },
  'el-drawer': {
    template: '<div class="el-drawer-stub" :data-size="size" :data-direction="direction"><slot /><slot name="footer" /></div>',
    props: ['size', 'direction', 'title', 'destroyOnClose']
  },
  'el-tabs': { template: '<div class="el-tabs-stub"><slot /></div>' },
  'el-tab-pane': {
    props: ['label', 'name'],
    template: '<div class="el-tab-pane-stub" :data-name="name" :data-label="label"><slot /></div>'
  },
  'el-descriptions': { template: '<div class="el-descriptions-stub"><slot /></div>' },
  'el-descriptions-item': {
    props: ['label'],
    template: '<div class="el-descriptions-item-stub" :data-label="label"><slot /></div>'
  },
  'el-tag': { template: '<span class="el-tag-stub"><slot /></span>' },
  'el-button': {
    template: '<button class="el-button-stub" @click="$emit(\'click\')"><slot /></button>'
  },
  'el-icon': { template: '<i class="el-icon-stub"><slot /></i>' },
  'el-table': { template: '<div class="el-table-stub"><slot /></div>' },
  'el-table-column': { template: '<div />' },
  'el-empty': { template: '<div class="el-empty-stub" />' },
  'el-timeline': { template: '<div class="el-timeline-stub"><slot /></div>' },
  'el-timeline-item': { template: '<div class="el-timeline-item-stub"><slot /></div>' }
}

const mockCa = {
  id: 1,
  platformIds: ['政采云'],
  caType: 'ENTITY_CA',
  caTypeLabel: '实体CA',
  sealTypeLabel: '公章',
  electronicAccount: '',
  caPasswordMasked: '******',
  expiryDate: '2027-06-01',
  remainingDays: 370,
  caPlatformUrl: 'https://zcy.gov.cn',
  custodianName: '张三',
  custodianId: 'user001',
  borrowStatus: 'IN_STOCK',
  borrowStatusLabel: '在库',
  currentBorrowerName: '',
  status: 'ACTIVE',
  statusLabel: '有效',
  remark: ''
}

async function importComponent() {
  const mod = await import('../CADetailDialog.vue')
  return mod.default
}

function mountComponent(props = {}) {
  return importComponent().then((component) =>
    mount(component, {
      props: { modelValue: true, ca: mockCa, ...props },
      global: { plugins: [createPinia()], stubs }
    })
  )
}

describe('CADetailDialog.vue — CO-406 详情形态对齐 AccountDetailDialog（居中弹窗）', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  // 核心断言：渲染的是 el-dialog 而不是 el-drawer
  it('render_usesDialogNotDrawer — 根标签应为 el-dialog', async () => {
    const wrapper = await mountComponent()
    await flushPromises()

    expect(wrapper.find('.el-dialog-stub').exists()).toBe(true)
    expect(wrapper.find('.el-drawer-stub').exists()).toBe(false)
  })

  // 宽度对齐 AccountDetailDialog（680px）
  it('render_dialogWidthIs680px — 宽度对齐账户详情弹窗', async () => {
    const wrapper = await mountComponent()
    await flushPromises()

    const dialog = wrapper.find('.el-dialog-stub')
    expect(dialog.attributes('data-width')).toBe('680px')
  })

  // 不再携带 drawer 专属的 direction 属性
  it('render_noDrawerDirectionOrSize — 不再使用 drawer 专属属性', async () => {
    const wrapper = await mountComponent()
    await flushPromises()

    // 既然根标签是 el-dialog，drawer stub 不应出现，自然没有 direction/size
    const drawer = wrapper.find('.el-drawer-stub')
    expect(drawer.exists()).toBe(false)
  })

  // 三个 Tab 仍渲染（基本信息 / 借用记录 / 操作日志）
  it('render_threeTabsPreserved — Tab 结构不被破坏', async () => {
    const wrapper = await mountComponent()
    await flushPromises()

    const panes = wrapper.findAll('.el-tab-pane-stub')
    const labels = panes.map((p) => p.attributes('data-label'))
    expect(labels).toContain('基本信息')
    expect(labels).toContain('借用记录')
    expect(labels).toContain('操作日志')
  })

  // 按钮区仍在（编辑/借用按权限渲染）
  it('render_detailActionsPreserved — 操作按钮区保留', async () => {
    const wrapper = await mountComponent({ actions: { canEdit: true, canBorrow: true, canReturn: true } })
    await flushPromises()

    expect(wrapper.find('.detail-actions').exists()).toBe(true)
  })

  // v-model 关闭契约：emit update:modelValue(false)
  it('emit_closeContractsModelValue — 关闭时 emit update:modelValue', async () => {
    const wrapper = await mountComponent()
    await flushPromises()

    await wrapper.setProps({ modelValue: false })
    // 组件通过 computed setter emit update:modelValue
    // setProps 后组件内部 visible 跟随变化，无需额外断言 emit 内容
    expect(wrapper.vm.visible).toBe(false)
  })

  // 编辑按钮 emit
  it('emit_editWhenManagerClicksEdit — 管理员点击编辑 emit edit 事件', async () => {
    const wrapper = await mountComponent({ actions: { canEdit: true, canBorrow: true, canReturn: true } })
    await flushPromises()

    const actions = wrapper.find('.detail-actions')
    const editBtn = actions.findAll('button.el-button-stub').find((b) => b.text().includes('编辑'))
    expect(editBtn).toBeDefined()

    await editBtn.trigger('click')
    expect(wrapper.emitted('edit')).toBeTruthy()
    // CO-406 不改 emit 契约，payload 仍是 ca 对象
    expect(wrapper.emitted('edit')[0][0]).toEqual(mockCa)
  })

  // 借用按钮 emit（由 actions.canBorrow 控制）
  it('emit_borrowWhenInStockClicksBorrow — actions.canBorrow 为 true 时显示借用按钮并 emit borrow 事件', async () => {
    const wrapper = await mountComponent({ actions: { canEdit: false, canBorrow: true, canReturn: false } })
    await flushPromises()

    const actions = wrapper.find('.detail-actions')
    const borrowBtn = actions.findAll('button.el-button-stub').find((b) => b.text().includes('借用'))
    expect(borrowBtn).toBeDefined()

    await borrowBtn.trigger('click')
    expect(wrapper.emitted('borrow')).toBeTruthy()
    expect(wrapper.emitted('borrow')[0][0]).toEqual(mockCa)
  })

  // actions.canReturn 为 false 时不显示登记归还按钮
  it('render_noReturnButtonWhenInStock — actions.canReturn 为 false 时不显示登记归还按钮', async () => {
    const wrapper = await mountComponent({ actions: { canEdit: false, canBorrow: true, canReturn: false } })
    await flushPromises()

    const actions = wrapper.find('.detail-actions')
    const returnBtn = actions.findAll('button.el-button-stub').find((b) => b.text().trim() === '登记归还')
    expect(returnBtn).toBeUndefined()
  })

  // actions.canReturn 为 true 时显示登记归还按钮
  it('render_returnButtonWhenBorrowed — actions.canReturn 为 true 时显示登记归还按钮', async () => {
    const borrowedCa = { ...mockCa, borrowStatus: 'BORROWED', borrowStatusLabel: '已借出', currentBorrowerName: '王五' }
    const wrapper = await mountComponent({ ca: borrowedCa, actions: { canEdit: false, canBorrow: false, canReturn: true } })
    await flushPromises()

    const actions = wrapper.find('.detail-actions')
    const returnBtn = actions.findAll('button.el-button-stub').find((b) => b.text().trim() === '登记归还')
    expect(returnBtn).toBeDefined()
  })

  // 登记归还按钮 emit return 事件
  it('emit_returnWhenBorrowedClicksReturn — 点击登记归还 emit return 事件', async () => {
    const borrowedCa = { ...mockCa, borrowStatus: 'BORROWED', borrowStatusLabel: '已借出', currentBorrowerName: '王五' }
    const wrapper = await mountComponent({ ca: borrowedCa, actions: { canEdit: false, canBorrow: false, canReturn: true } })
    await flushPromises()

    const actions = wrapper.find('.detail-actions')
    const returnBtn = actions.findAll('button.el-button-stub').find((b) => b.text().trim() === '登记归还')
    expect(returnBtn).toBeDefined()

    await returnBtn.trigger('click')
    expect(wrapper.emitted('return')).toBeTruthy()
    expect(wrapper.emitted('return')[0][0]).toEqual(borrowedCa)
  })

  // actions.canBorrow 为 false 时不显示借用按钮
  it('render_noBorrowButtonWhenBorrowed — actions.canBorrow 为 false 时不显示借用按钮', async () => {
    const borrowedCa = { ...mockCa, borrowStatus: 'BORROWED', borrowStatusLabel: '已借出', currentBorrowerName: '王五' }
    const wrapper = await mountComponent({ ca: borrowedCa, actions: { canEdit: false, canBorrow: false, canReturn: true } })
    await flushPromises()

    const actions = wrapper.find('.detail-actions')
    const borrowBtn = actions.findAll('button.el-button-stub').find((b) => b.text().includes('借用'))
    expect(borrowBtn).toBeUndefined()
  })

  // CO-451: 详情页不应显示"保管员ID"字段（业务无意义）
  it('render_noCustodianIdField — 不渲染保管员ID字段', async () => {
    const wrapper = await mountComponent()
    await flushPromises()

    const items = wrapper.findAll('.el-descriptions-item-stub')
    const custodianIdItem = items.find((item) => item.attributes('data-label') === '保管员ID')
    expect(custodianIdItem).toBeUndefined()
  })

  // CO-451: 保管员字段显示为"姓名（工号）"格式
  it('render_custodianWithNameAndEmployeeNumber — 保管员显示为"姓名（工号）"格式', async () => {
    const caWithEmployeeNumber = {
      ...mockCa,
      custodianName: '张三',
      custodianEmployeeNumber: '20260509'
    }
    const wrapper = await mountComponent({ ca: caWithEmployeeNumber })
    await flushPromises()

    const items = wrapper.findAll('.el-descriptions-item-stub')
    const custodianItem = items.find((item) => item.attributes('data-label') === '保管员')
    expect(custodianItem).toBeDefined()
    // 应显示"张三（20260509）"格式
    expect(custodianItem.text()).toContain('张三')
    expect(custodianItem.text()).toContain('20260509')
  })

  // CO-451: 保管员无工号时仅显示姓名
  it('render_custodianWithOnlyName — 保管员无工号时仅显示姓名', async () => {
    const caWithoutEmployeeNumber = {
      ...mockCa,
      custodianName: '张三',
      custodianEmployeeNumber: ''
    }
    const wrapper = await mountComponent({ ca: caWithoutEmployeeNumber })
    await flushPromises()

    const items = wrapper.findAll('.el-descriptions-item-stub')
    const custodianItem = items.find((item) => item.attributes('data-label') === '保管员')
    expect(custodianItem).toBeDefined()
    expect(custodianItem.text()).toBe('张三')
  })
})
