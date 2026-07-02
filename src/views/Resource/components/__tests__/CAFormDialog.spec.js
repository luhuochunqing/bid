// Input: src/views/Resource/components/CAFormDialog.vue — CA 证书新增/编辑表单
// Output: CO-405 关联平台字段改为非必填的回归测试
// Output: CO-436 编辑时密码不应回填脱敏值的回归测试
// Pos: src/views/Resource/components/__tests__/ — 表单校验与提交行为
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

// Mock usePlatformAccountSearch —— CAFormDialog 顶层依赖
const searchPlatformsMock = vi.fn(() => Promise.resolve())
vi.mock('@/composables/usePlatformAccountSearch.js', () => ({
  usePlatformAccountSearch: () => ({
    platformOptions: { value: [] },
    platformOptionsLoading: { value: false },
    searchPlatforms: searchPlatformsMock
  })
}))

// Mock caApi.getPassword
const getPasswordMock = vi.fn()
vi.mock('@/api/modules/ca.js', () => ({
  caApi: { getPassword: getPasswordMock }
}))

// el-form 的 validate 由测试控制返回值，便于覆盖校验通过/失败两条路径
const validateMock = vi.fn(() => Promise.resolve(true))
const elFormStub = {
  template: '<form class="el-form-stub"><slot /></form>',
  methods: { validate: validateMock }
}

const stubs = {
  'el-dialog': { template: '<div class="el-dialog-stub"><slot /><slot name="footer" /></div>' },
  'el-form': elFormStub,
  'el-form-item': {
    props: ['required', 'prop', 'label'],
    template: '<div class="el-form-item-stub"><slot /></div>'
  },
  'el-select': { template: '<div class="el-select-stub"><slot /></div>' },
  'el-option': { template: '<div />' },
  'el-input': { template: '<input />' },
  'el-date-picker': { template: '<input />' },
  'el-button': {
    template: '<button class="el-button-stub" @click="$emit(\'click\')"><slot /></button>'
  },
  UserPicker: { template: '<div class="user-picker-stub" />' }
}

async function importDialog() {
  const mod = await import('../CAFormDialog.vue')
  return mod.default
}

function mountDialog(props = {}, injectedPinia = null) {
  const pinia = injectedPinia || createPinia()
  if (injectedPinia) {
    setActivePinia(pinia)
  }
  return importDialog().then((component) =>
    mount(component, {
      props: { modelValue: true, ...props },
      global: { plugins: [pinia], stubs }
    })
  )
}

// Mock useUserStore —— 返回可配置的角色状态
const userRoleState = { role: 'bid-Team', id: null }
const mockUserStore = {
  get userRole() { return userRoleState.role },
  get currentUser() { return { role: userRoleState.role, id: userRoleState.id } }
}
vi.mock('@/stores/user', () => ({
  useUserStore: () => mockUserStore
}))

// 设置模拟 store 的角色（同步更新模块级状态）
function setUserRole(role, userId) {
  userRoleState.role = role || 'bid-Team'
  userRoleState.id = userId ?? null
}

describe('CAFormDialog.vue — CO-405 关联平台字段改为非必填', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    validateMock.mockReset()
    validateMock.mockResolvedValue(true)
    searchPlatformsMock.mockReset()
    searchPlatformsMock.mockResolvedValue()
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  // 核心红线：rules 不应再对 platformIds 强制 required
  it('rules.platformIds 不再声明 required: true', async () => {
    const wrapper = await mountDialog()
    const platformIdsRules = wrapper.vm.rules?.platformIds
    if (platformIdsRules) {
      const arr = Array.isArray(platformIdsRules) ? platformIdsRules : [platformIdsRules]
      const hasRequired = arr.some((r) => r?.required === true)
      expect(hasRequired).toBe(false)
    }
    // 若 rules.platformIds 为 undefined，视为已移除，同样通过
  })

  // 行为：platformIds 为空时，校验通过后应正常 emit submit
  it('handleSubmit 在 platformIds 为空时仍 emit submit，payload.platformIds 为 []', async () => {
    const wrapper = await mountDialog()
    await flushPromises()

    // 其他必填字段填入合法值，仅 platformIds 留空
    wrapper.vm.form.caType = 'ENTITY_CA'
    wrapper.vm.form.sealType = 'OFFICIAL_SEAL'
    wrapper.vm.form.caPassword = 'pass123'
    wrapper.vm.form.expiryDate = '2027-01-01'
    wrapper.vm.form.custodianId = 1
    wrapper.vm.form.custodianName = '张三'
    wrapper.vm.form.platformIds = []

    await wrapper.vm.handleSubmit()
    await flushPromises()

    const submitEvents = wrapper.emitted('submit')
    expect(submitEvents).toBeTruthy()
    expect(submitEvents[0][0].platformIds).toEqual([])
  })

  // 回归保护：校验失败时不得 emit submit
  it('handleSubmit 在校验失败时不 emit submit', async () => {
    validateMock.mockResolvedValue(false)
    const wrapper = await mountDialog()
    await flushPromises()

    await wrapper.vm.handleSubmit()
    await flushPromises()

    expect(wrapper.emitted('submit')).toBeFalsy()
  })

  // payload 转换：platformIds 应被转为 Number 数组
  it('handleSubmit 将 platformIds 转为 Number 数组', async () => {
    const wrapper = await mountDialog()
    await flushPromises()

    wrapper.vm.form.platformIds = ['1', '2']
    await wrapper.vm.handleSubmit()
    await flushPromises()

    const submitEvents = wrapper.emitted('submit')
    expect(submitEvents).toBeTruthy()
    expect(submitEvents[0][0].platformIds).toEqual([1, 2])
  })
})

describe('CAFormDialog.vue — CO-436 编辑时密码不应回填脱敏值', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    validateMock.mockReset()
    validateMock.mockResolvedValue(true)
    searchPlatformsMock.mockReset()
    searchPlatformsMock.mockResolvedValue()
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  it('编辑时 ca.caPassword 为脱敏值，form.caPassword 应为空字符串', async () => {
    const wrapper = await mountDialog({
      ca: {
        id: 1,
        caType: 'ENTITY_CA',
        sealType: 'OFFICIAL_SEAL',
        caPassword: '******',
        expiryDate: '2027-01-01',
        custodianId: 1,
        custodianName: '张三',
        platformIds: []
      }
    })
    await flushPromises()

    expect(wrapper.vm.form.caPassword).toBe('')
  })

  it('编辑时未修改密码，提交数据中 caPassword 应为空字符串', async () => {
    const wrapper = await mountDialog({
      ca: {
        id: 1,
        caType: 'ENTITY_CA',
        sealType: 'OFFICIAL_SEAL',
        caPassword: '******',
        expiryDate: '2027-01-01',
        custodianId: 1,
        custodianName: '张三',
        platformIds: []
      }
    })
    await flushPromises()

    await wrapper.vm.handleSubmit()
    await flushPromises()

    const submitEvents = wrapper.emitted('submit')
    expect(submitEvents).toBeTruthy()
    expect(submitEvents[0][0].caPassword).toBe('')
  })

  // CO-435 回归：编辑模式空密码不应被前端 rules 拦截
  it('编辑模式且 caType=ENTITY_CA 时，rules.caPassword 不应声明 required: true', async () => {
    const wrapper = await mountDialog({
      ca: {
        id: 1,
        caType: 'ENTITY_CA',
        sealType: 'OFFICIAL_SEAL',
        caPassword: '******',
        expiryDate: '2027-01-01',
        custodianId: 1,
        custodianName: '张三',
        platformIds: []
      }
    })
    await flushPromises()

    const caPasswordRules = wrapper.vm.rules?.caPassword
    const arr = Array.isArray(caPasswordRules) ? caPasswordRules : [caPasswordRules]
    const hasRequired = arr.some((r) => r?.required === true)
    expect(hasRequired).toBe(false)
  })

  // CO-435 回归：编辑模式 el-form-item :required 属性不应为 true
  // 根因：:required="form.caType === 'ENTITY_CA'" 在编辑模式仍为 true，
  // Element Plus 自动注入 required 规则（默认消息 "caPassword is required"）
  it('编辑模式且 caType=ENTITY_CA 时，CA密码 form-item 的 required prop 应为 false', async () => {
    const wrapper = await mountDialog({
      ca: {
        id: 1,
        caType: 'ENTITY_CA',
        sealType: 'OFFICIAL_SEAL',
        caPassword: '******',
        expiryDate: '2027-01-01',
        custodianId: 1,
        custodianName: '张三',
        platformIds: []
      }
    })
    await flushPromises()

    const formItems = wrapper.findAllComponents('.el-form-item-stub')
    const caPasswordItem = formItems.find((fi) => fi.props('prop') === 'caPassword')
    expect(caPasswordItem).toBeTruthy()
    expect(caPasswordItem.props('required')).toBe(false)
  })

  it('编辑时修改了密码，提交数据中 caPassword 应为新值', async () => {
    const wrapper = await mountDialog({
      ca: {
        id: 1,
        caType: 'ENTITY_CA',
        sealType: 'OFFICIAL_SEAL',
        caPassword: '******',
        expiryDate: '2027-01-01',
        custodianId: 1,
        custodianName: '张三',
        platformIds: []
      }
    })
    await flushPromises()

    wrapper.vm.form.caPassword = 'newPassword123'
    await wrapper.vm.handleSubmit()
    await flushPromises()

    const submitEvents = wrapper.emitted('submit')
    expect(submitEvents).toBeTruthy()
    expect(submitEvents[0][0].caPassword).toBe('newPassword123')
  })
})

describe('CAFormDialog.vue — CA 密码 reveal 权限控制', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    validateMock.mockReset()
    validateMock.mockResolvedValue(true)
    searchPlatformsMock.mockReset()
    searchPlatformsMock.mockResolvedValue()
    getPasswordMock.mockReset()
    getPasswordMock.mockResolvedValue({ success: true, data: { caPassword: '真实密码123' } })
    // 重置模拟用户角色
    setUserRole('bid-Team', null)
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  // 新增时：不得显示眼睛按钮
  it('canViewPassword 在新增模式下返回 false', async () => {
    const wrapper = await mountDialog({ ca: null })
    await flushPromises()
    expect(wrapper.vm.canViewPassword).toBe(false)
  })

  // 投标管理员：可查看
  it('canViewPassword 对投标管理员返回 true', async () => {
    setUserRole('admin', 999)
    const wrapper = await mountDialog({
      ca: {
        id: 1, caType: 'ENTITY_CA', sealType: 'OFFICIAL_SEAL',
        caPassword: '******', expiryDate: '2027-01-01',
        custodianId: 1, custodianName: '张三', platformIds: []
      }
    })
    await flushPromises()
    expect(wrapper.vm.canViewPassword).toBe(true)
  })

  // 投标组长：可查看
  it('canViewPassword 对投标组长返回 true', async () => {
    setUserRole('bid-TeamLeader', 999)
    const wrapper = await mountDialog({
      ca: {
        id: 1, caType: 'ENTITY_CA', sealType: 'OFFICIAL_SEAL',
        caPassword: '******', expiryDate: '2027-01-01',
        custodianId: 1, custodianName: '张三', platformIds: []
      }
    })
    await flushPromises()
    expect(wrapper.vm.canViewPassword).toBe(true)
  })

  // CA 保管员本人：可查看
  it('canViewPassword 对 CA 保管员本人返回 true', async () => {
    setUserRole('bid-Team', 42)
    const wrapper = await mountDialog({
      ca: {
        id: 1, caType: 'ENTITY_CA', sealType: 'OFFICIAL_SEAL',
        caPassword: '******', expiryDate: '2027-01-01',
        custodianId: 42, custodianName: '保管员A', platformIds: []
      }
    })
    await flushPromises()
    expect(wrapper.vm.canViewPassword).toBe(true)
  })

  // 非管理员非保管员：不可查看
  it('canViewPassword 对非管理员非保管员返回 false', async () => {
    setUserRole('bid-Team', 99)
    const wrapper = await mountDialog({
      ca: {
        id: 1, caType: 'ENTITY_CA', sealType: 'OFFICIAL_SEAL',
        caPassword: '******', expiryDate: '2027-01-01',
        custodianId: 42, custodianName: '保管员A', platformIds: []
      }
    })
    await flushPromises()
    expect(wrapper.vm.canViewPassword).toBe(false)
  })

  // 点击眼睛按钮：调用 getPassword 并显示密码
  it('handleRevealPassword 调用 getPassword 并设置 passwordRevealed 为 true', async () => {
    setUserRole('admin', 999)
    const wrapper = await mountDialog({
      ca: {
        id: 1, caType: 'ENTITY_CA', sealType: 'OFFICIAL_SEAL',
        caPassword: '******', expiryDate: '2027-01-01',
        custodianId: 1, custodianName: '张三', platformIds: []
      }
    })
    await flushPromises()

    await wrapper.vm.handleRevealPassword()
    await flushPromises()

    expect(getPasswordMock).toHaveBeenCalledWith(1)
    expect(wrapper.vm.passwordRevealed).toBe(true)
    expect(wrapper.vm.form.caPassword).toBe('真实密码123')
  })

  // 再次点击：隐藏密码
  it('handleRevealPassword 在已显示时再次点击隐藏密码', async () => {
    setUserRole('admin', 999)
    const wrapper = await mountDialog({
      ca: {
        id: 1, caType: 'ENTITY_CA', sealType: 'OFFICIAL_SEAL',
        caPassword: '******', expiryDate: '2027-01-01',
        custodianId: 1, custodianName: '张三', platformIds: []
      }
    })
    await flushPromises()

    // 先显示
    await wrapper.vm.handleRevealPassword()
    await flushPromises()
    expect(wrapper.vm.passwordRevealed).toBe(true)

    // 再隐藏
    await wrapper.vm.handleRevealPassword()
    await flushPromises()
    expect(wrapper.vm.passwordRevealed).toBe(false)
    expect(wrapper.vm.form.caPassword).toBe('')
    // getPassword 不应再被调用
    expect(getPasswordMock).toHaveBeenCalledTimes(1)
  })
})

describe('CAFormDialog.vue — CO-451 编辑时保管员字段显示为"姓名（工号）"', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    validateMock.mockReset()
    validateMock.mockResolvedValue(true)
    searchPlatformsMock.mockReset()
    searchPlatformsMock.mockResolvedValue()
    getPasswordMock.mockReset()
    getPasswordMock.mockResolvedValue({ success: true, data: { caPassword: '真实密码123' } })
    setUserRole('bid-Team', null)
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  // 编辑模式下，custodianId 和 custodianName 应正确填充到 form
  it('编辑时 form.custodianId 和 form.custodianName 应从 ca 对象填充', async () => {
    const wrapper = await mountDialog({
      ca: {
        id: 1, caType: 'ENTITY_CA', sealType: 'OFFICIAL_SEAL',
        caPassword: '******', expiryDate: '2027-01-01',
        custodianId: 42, custodianName: '保管员A', custodianEmployeeNumber: '20260101',
        platformIds: []
      }
    })
    await flushPromises()

    expect(wrapper.vm.form.custodianId).toBe(42)
    expect(wrapper.vm.form.custodianName).toBe('保管员A')
    expect(wrapper.vm.form.custodianEmployeeNumber).toBe('20260101')
  })

  // 编辑模式下，custodianEmployeeNumber 应从 ca 对象填充（用于构造 initialOptions）
  it('编辑时 form.custodianEmployeeNumber 应从 ca 对象填充', async () => {
    const wrapper = await mountDialog({
      ca: {
        id: 1, caType: 'ENTITY_CA', sealType: 'OFFICIAL_SEAL',
        caPassword: '******', expiryDate: '2027-01-01',
        custodianId: 42, custodianName: '保管员A', custodianEmployeeNumber: '20260101',
        platformIds: []
      }
    })
    await flushPromises()

    expect(wrapper.vm.form.custodianEmployeeNumber).toBe('20260101')
  })

  // 新增模式下，custodianEmployeeNumber 应为空
  it('新增时 form.custodianEmployeeNumber 应为空', async () => {
    const wrapper = await mountDialog({ ca: null })
    await flushPromises()

    expect(wrapper.vm.form.custodianEmployeeNumber).toBe('')
  })

  // 编辑模式下，initialOptions 应包含当前保管员（用于 UserPicker 显示）
  it('编辑时 custodianInitialOptions 应包含当前保管员信息', async () => {
    const wrapper = await mountDialog({
      ca: {
        id: 1, caType: 'ENTITY_CA', sealType: 'OFFICIAL_SEAL',
        caPassword: '******', expiryDate: '2027-01-01',
        custodianId: 42, custodianName: '保管员A', custodianEmployeeNumber: '20260101',
        platformIds: []
      }
    })
    await flushPromises()

    const options = wrapper.vm.custodianInitialOptions
    expect(options).toHaveLength(1)
    expect(options[0].id).toBe(42)
    expect(options[0].name).toBe('保管员A')
    expect(options[0].employeeNumber).toBe('20260101')
  })

  // 新增模式下，custodianInitialOptions 应为空数组
  it('新增时 custodianInitialOptions 应为空数组', async () => {
    const wrapper = await mountDialog({ ca: null })
    await flushPromises()

    expect(wrapper.vm.custodianInitialOptions).toEqual([])
  })
})
