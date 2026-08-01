// Input: DynamicFormRenderer with enum-type fields holding legacy/stale values (CO-601 US3)
// Output: 失配存量值不报错、不丢失、不阻断提交的兜底行为契约
// Pos: src/components/common/__tests__/ - DynamicFormRenderer fallback unit tests

import { mount } from '@vue/test-utils'
import { describe, it, expect } from 'vitest'

import DynamicFormRenderer from '../DynamicFormRenderer.vue'

const elementStubs = {
  'el-form': { template: '<form><slot /></form>' },
  'el-form-item': {
    name: 'ElFormItem',
    props: ['label', 'required'],
    template: '<label class="el-form-item"><span>{{ label }}</span><slot /></label>'
  },
  'el-input': {
    props: ['modelValue', 'type', 'rows', 'placeholder', 'disabled', 'readonly'],
    template: '<input :value="modelValue" />'
  },
  'el-input-number': { template: '<input />' },
  'el-date-picker': { template: '<input />' },
  'el-select': {
    name: 'ElSelect',
    props: ['modelValue', 'disabled', 'placeholder'],
    template: '<select :data-value="modelValue"><slot /></select>'
  },
  'el-option': { template: '<option />' },
  'el-upload': { template: '<div />' },
  'el-button': { template: '<button><slot /></button>' },
  'el-alert': { template: '<div />' },
  'el-divider': { template: '<hr />' },
  'el-cascader': { template: '<div />' },
  'el-slider': { template: '<div />' },
  'el-tag': { template: '<span />' }
}

describe('DynamicFormRenderer 失配存量值兜底（CO-601 US3）', () => {
  it('select 字段持有不在 options 中的历史值：挂载不报错', () => {
    const fields = [
      {
        key: 'customerType',
        label: '客户类型',
        type: 'select',
        options: [{ label: '国企', value: 'soe' }, { label: '民营', value: 'private' }]
      }
    ]
    // 历史值 "legacy_text" 是文本改下拉前留下的存量值
    const wrapper = mount(DynamicFormRenderer, {
      props: { fields, modelValue: { customerType: 'legacy_text' } },
      global: { stubs: elementStubs }
    })
    expect(wrapper.exists()).toBe(true)
  })

  it('select 失配历史值保留在数据中：submit 不丢值', () => {
    const fields = [
      {
        key: 'customerType',
        label: '客户类型',
        type: 'select',
        options: [{ label: '国企', value: 'soe' }]
      }
    ]
    const wrapper = mount(DynamicFormRenderer, {
      props: { fields, modelValue: { customerType: 'legacy_text' } },
      global: { stubs: elementStubs }
    })
    const result = wrapper.vm.submit()
    expect(result.valid).toBe(true)
    expect(result.data.customerType).toBe('legacy_text')
  })

  it('select 失配历史值不触发校验误杀（非必填 validate 通过）', () => {
    const fields = [
      {
        key: 'customerType',
        label: '客户类型',
        type: 'select',
        options: [{ label: '国企', value: 'soe' }]
      }
    ]
    const wrapper = mount(DynamicFormRenderer, {
      props: { fields, modelValue: { customerType: 'legacy_text' } },
      global: { stubs: elementStubs }
    })
    expect(wrapper.vm.validate()).toBe('')
  })

  it('select 值绑定到失配历史值（Element Plus 原生文本兜底渲染的契约锚点）', () => {
    const fields = [
      {
        key: 'customerType',
        label: '客户类型',
        type: 'select',
        options: [{ label: '国企', value: 'soe' }]
      }
    ]
    const wrapper = mount(DynamicFormRenderer, {
      props: { fields, modelValue: { customerType: 'legacy_text' } },
      global: { stubs: elementStubs }
    })
    // el-select v-model 值无匹配 option 时，Element Plus 原生将原始 value 按文本回显；
    // 此处锚定"值原样透传到 el-select"，视觉兜底由 Element Plus 保证
    const select = wrapper.findComponent({ name: 'ElSelect' })
    expect(select.props('modelValue')).toBe('legacy_text')
  })

  it('枚举类型（tender_source）失配历史值同样不报错不丢值', () => {
    const fields = [
      { key: 'source', label: '来源', type: 'tender_source' }
    ]
    const wrapper = mount(DynamicFormRenderer, {
      props: { fields, modelValue: { source: 'deleted_option_value' } },
      global: { stubs: elementStubs }
    })
    const result = wrapper.vm.submit()
    expect(result.valid).toBe(true)
    expect(result.data.source).toBe('deleted_option_value')
  })

  it('未知字段类型降级为文本输入渲染（原有 v-else 分支），历史值可见可编辑', () => {
    const fields = [
      { key: 'legacy', label: '旧字段', type: 'removed_widget_type' }
    ]
    const wrapper = mount(DynamicFormRenderer, {
      props: { fields, modelValue: { legacy: 'old_value' } },
      global: { stubs: elementStubs }
    })
    const input = wrapper.find('input')
    expect(input.exists()).toBe(true)
    expect(input.element.value).toBe('old_value')
  })
})
