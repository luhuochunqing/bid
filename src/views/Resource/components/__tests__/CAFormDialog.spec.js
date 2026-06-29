// Input: src/views/Resource/components/CAFormDialog.vue — CA 证书新增/编辑表单
// Output: CO-405 关联平台字段改为非必填的回归测试
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

// el-form 的 validate 由测试控制返回值，便于覆盖校验通过/失败两条路径
const validateMock = vi.fn(() => Promise.resolve(true))
const elFormStub = {
  template: '<form class="el-form-stub"><slot /></form>',
  methods: { validate: validateMock }
}

const stubs = {
  'el-dialog': { template: '<div class="el-dialog-stub"><slot /><slot name="footer" /></div>' },
  'el-form': elFormStub,
  'el-form-item': { template: '<div class="el-form-item-stub"><slot /></div>' },
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

function mountDialog(props = {}) {
  return importDialog().then((component) =>
    mount(component, {
      props: { modelValue: true, ...props },
      global: { plugins: [createPinia()], stubs }
    })
  )
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
