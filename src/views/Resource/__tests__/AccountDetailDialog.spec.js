// Input: src/views/Resource/AccountDetailDialog.vue — detail dialog with password reveal
// Output: unit tests covering field name fix, alias fix, v2 field removal, password reveal wiring
// Pos: src/views/Resource/__tests__/ — CO-389 regression for detail dialog
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { mount, flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

const mockAccountDetail = {
  id: 1,
  accountName: 'gov-procurement-main',
  platform: '政采云',
  username: 'admin001',
  password: '',
  url: 'https://gov.example.com',
  contactPerson: '张三（001）',
  contactPhone: '13800000001',
  contactEmail: 'zhangsan@example.com',
  hasCa: true,
  custodian: '901',
  custodianName: '李保管',
  caCustodian: '902',
  caCustodianName: '王CA',
  platformType: 'GOV_PROCUREMENT',
  remarks: '主账号',
  status: 'available',
  lastUsed: '2026-06-20 10:00',
  borrower: '',
  dueAt: ''
}

const mockPasswordResponse = {
  success: true,
  data: { password: '<TEST-MOCK-PASSWORD-DO-NOT-USE>', auditId: 'audit-001' }
}

const resourcesApiMock = {
  accounts: {
    getPassword: vi.fn()
  }
}

vi.mock('@/api/modules/resources', () => ({
  default: resourcesApiMock
}))

vi.mock('@/api/client', () => ({
  default: {
    get: vi.fn(() => Promise.resolve({ data: { data: [] } }))
  }
}))

vi.mock('element-plus', () => ({
  ElMessage: {
    error: vi.fn(),
    success: vi.fn(),
    info: vi.fn(),
    warning: vi.fn()
  }
}))

const AccountDetailDialogModule = await import('../AccountDetailDialog.vue')

const mountDialog = (data = mockAccountDetail) =>
  mount(AccountDetailDialogModule.default, {
    props: {
      modelValue: true,
      data
    },
    global: {
      plugins: [createPinia()],
      stubs: {
        'el-dialog': { template: '<div class="el-dialog-stub"><slot /><slot name="footer" /></div>' },
        'el-tabs': { template: '<div class="el-tabs-stub"><slot /></div>' },
        'el-tab-pane': { template: '<div class="el-tab-pane-stub"><slot /></div>' },
        'el-descriptions': { template: '<div class="el-descriptions-stub"><slot /></div>' },
        'el-descriptions-item': {
          props: ['label', 'span'],
          template: '<div class="el-descriptions-item-stub" :data-label="label" :data-span="span"><slot /></div>'
        },
        'el-tag': { template: '<span class="el-tag-stub"><slot /></span>' },
        'el-button': {
          template: '<button class="el-button-stub" @click="$emit(\'click\')"><slot /></button>'
        },
        'el-table': { template: '<div class="el-table-stub"><slot /></div>' },
        'el-table-column': { template: '<div />' },
        'el-empty': { template: '<div class="el-empty-stub" />' },
        'el-timeline': { template: '<div class="el-timeline-stub"><slot /></div>' },
        'el-timeline-item': { template: '<div class="el-timeline-item-stub"><slot /></div>' }
      }
    }
  })

describe('AccountDetailDialog.vue — CO-389 详情字段漂移修复 + 密码字段补全', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    resourcesApiMock.accounts.getPassword.mockReset()
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  // 问题 3：alias 修复 — 详情应读 data.accountName
  it('render_showsPlatformNameFromAccountName — 优先展示 accountName', () => {
    const wrapper = mountDialog()
    const platformNameItem = wrapper.find('.el-descriptions-item-stub[data-label="平台名称"]')
    expect(platformNameItem.exists()).toBe(true)
    expect(platformNameItem.text()).toContain('gov-procurement-main')
  })

  // 问题 2：字段名漂移修复 — "用户名" → "平台账号"
  it('render_showsUsernameLabelAs平台账号 — label 文案对齐新增表单', () => {
    const wrapper = mountDialog()
    const usernameItem = wrapper.find('.el-descriptions-item-stub[data-label="平台账号"]')
    expect(usernameItem.exists()).toBe(true)
    expect(usernameItem.text()).toContain('admin001')
  })

  // 问题 2 验证：原 "用户名" label 不再存在
  it('render_doesNotShowUsernameLabel — 原 label 已改名', () => {
    const wrapper = mountDialog()
    const oldLabelItem = wrapper.find('.el-descriptions-item-stub[data-label="用户名"]')
    expect(oldLabelItem.exists()).toBe(false)
  })

  // 问题 4 v2：详情不展示"账号保管员"
  it('render_doesNotShowCustodianName — v2 字段移除', () => {
    const wrapper = mountDialog()
    const custodianItem = wrapper.find('.el-descriptions-item-stub[data-label="账号保管员"]')
    expect(custodianItem.exists()).toBe(false)
  })

  // 问题 4 v2：详情不展示"CA 保管人"
  it('render_doesNotShowCaCustodianName — v2 字段移除', () => {
    const wrapper = mountDialog()
    const caCustodianItem = wrapper.find('.el-descriptions-item-stub[data-label="CA 保管人"]')
    expect(caCustodianItem.exists()).toBe(false)
  })

  // 问题 1：详情新增"平台密码"行（默认占位）
  it('render_showsPasswordPlaceholderWhenHidden — 默认显示掩码', () => {
    const wrapper = mountDialog()
    const passwordItem = wrapper.find('.el-descriptions-item-stub[data-label="平台密码"]')
    expect(passwordItem.exists()).toBe(true)
    // 默认未点击显示，应展示掩码 "••••••"
    expect(passwordItem.text()).toContain('••••••')
  })

  // 问题 1：点击"显示"按钮触发 getPassword 接口
  it('clickShowPassword_callsGetPasswordEndpoint — 触发密码揭示', async () => {
    resourcesApiMock.accounts.getPassword.mockResolvedValue(mockPasswordResponse)
    const wrapper = mountDialog()
    await flushPromises()

    const passwordItem = wrapper.find('.el-descriptions-item-stub[data-label="平台密码"]')
    expect(passwordItem.exists()).toBe(true)
    const showBtn = passwordItem.find('.el-button-stub')
    expect(showBtn.exists()).toBe(true)

    await showBtn.trigger('click')
    await flushPromises()

    expect(resourcesApiMock.accounts.getPassword).toHaveBeenCalledWith(1)
  })

  // P1 安全：dialog 关闭时清理密码揭示状态，防止明文驻留 + 避免下次打开误显示
  it('close_dialog_clearsPasswordState — 关闭后密码状态被清理', async () => {
    resourcesApiMock.accounts.getPassword.mockResolvedValue(mockPasswordResponse)
    const wrapper = mountDialog()
    await flushPromises()

    // 触发密码显示
    const passwordItem = wrapper.find('.el-descriptions-item-stub[data-label="平台密码"]')
    await passwordItem.find('.el-button-stub').trigger('click')
    await flushPromises()

    // 确认密码已揭示
    expect(wrapper.vm.password.visible.value[1]).toBe(true)
    expect(wrapper.vm.password.revealed.value[1]).toBeDefined()

    // 关闭 dialog
    await wrapper.setProps({ modelValue: false })
    await flushPromises()

    // 密码状态应被清理
    expect(wrapper.vm.password.visible.value[1]).toBeUndefined()
    expect(wrapper.vm.password.revealed.value[1]).toBeUndefined()
    expect(wrapper.vm.password.loading.value[1]).toBeUndefined()
  })
})
