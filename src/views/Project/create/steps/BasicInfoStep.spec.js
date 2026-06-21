import { describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import BasicInfoStep from './BasicInfoStep.vue'

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn() },
}))

const elStubs = {
  'el-form': { template: '<form><slot /></form>' },
  'el-form-item': { template: '<div><slot /></div>' },
  'el-input': { template: '<input />' },
  'el-input-number': {
    props: ['modelValue'],
    emits: ['update:modelValue', 'focus'],
    template: '<input type="number" :value="modelValue" @focus="$emit(\'focus\')" @input="$emit(\'update:modelValue\', Number($event.target.value))" />',
  },
  'el-select': { template: '<div><slot /></div>' },
  'el-option': { template: '<div><slot /></div>' },
  'el-date-picker': { template: '<input />' },
  'el-alert': { template: '<div><slot /></div>' },
  'el-button': { template: '<button><slot /></button>' },
  'el-divider': { template: '<div><slot /></div>' },
  'el-table': { template: '<table><slot /></table>' },
  'el-table-column': { template: '<col />' },
  'el-icon': { template: '<span><slot /></span>' },
  AdaptiveFormPage: {
    template: '<div><slot name="fallback-form" /></div>',
  },
}

function mountStep(budget) {
  return mount(BasicInfoStep, {
    props: {
      basicForm: {
        name: '',
        customer: '',
        budget,
        industry: '',
        region: '',
        platform: '',
        deadline: '',
        manager: '',
        competitors: [],
      },
      competitorAnalysis: [],
      platformOptions: [],
      userList: [],
      competitorOptions: [],
    },
    global: { stubs: elStubs },
  })
}

describe('BasicInfoStep — 预算字段 focus 清空默认值 0.00', () => {
  it('当 budget 为 0 时，focus 后清空为 null', async () => {
    const wrapper = mountStep(0)
    const input = wrapper.find('input[type="number"]')
    await input.trigger('focus')
    await flushPromises()

    expect(wrapper.props('basicForm').budget).toBeNull()
  })

  it('当 budget 为非 0 值时，focus 后保持不变', async () => {
    const wrapper = mountStep(500)
    const input = wrapper.find('input[type="number"]')
    await input.trigger('focus')
    await flushPromises()

    expect(wrapper.props('basicForm').budget).toBe(500)
  })

  it('当 budget 为 null 时，focus 后保持 null', async () => {
    const wrapper = mountStep(null)
    const input = wrapper.find('input[type="number"]')
    await input.trigger('focus')
    await flushPromises()

    expect(wrapper.props('basicForm').budget).toBeNull()
  })
})
