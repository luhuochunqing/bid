// Input: DesignerFieldList component with locked/fixed-group field constraints
// Output: regression coverage for key/type disabled, draggable false, enabled checkbox binding
// Pos: src/views/System/workflow-form-designer/components/ - DesignerFieldList unit tests

import { mount } from '@vue/test-utils'
import { describe, it, expect } from 'vitest'

import DesignerFieldList from './DesignerFieldList.vue'

const elementStubs = {
  'el-button': { template: '<button><slot /></button>' },
  'el-input': {
    name: 'ElInput',
    props: ['modelValue', 'disabled', 'placeholder', 'type', 'rows'],
    template: '<input :disabled="disabled" />'
  },
  'el-select': {
    name: 'ElSelect',
    props: ['modelValue', 'disabled'],
    template: '<select :disabled="disabled"><slot /></select>'
  },
  'el-option': { template: '<option />' },
  'el-checkbox': {
    name: 'ElCheckbox',
    props: ['modelValue', 'disabled'],
    template: '<label class="el-checkbox"><input type="checkbox" :checked="modelValue" :disabled="disabled" /><slot /></label>'
  },
  'el-tooltip': { template: '<span><slot /></span>' },
  'el-input-number': { template: '<input />' }
}

function mountWithFields(fields) {
  return mount(DesignerFieldList, {
    props: {
      fields,
      fieldTypes: [
        { label: '文本', value: 'text' },
        { label: '多行文本', value: 'textarea' },
        { label: '附件', value: 'attachment' }
      ]
    },
    global: { stubs: elementStubs }
  })
}

describe('DesignerFieldList', () => {
  it('特殊渲染字段（如 region）的 key 和 type 都是 disabled', () => {
    const wrapper = mountWithFields([
      { key: 'region', label: '总部所在地', type: 'cascader', required: true, enabled: true }
    ])

    const keyInput = wrapper.find('.field-key-input')
    const typeSelect = wrapper.find('.field-type-select')

    expect(keyInput.attributes('disabled')).toBeDefined()
    expect(typeSelect.attributes('disabled')).toBeDefined()
  })

  it('固定分组字段（如 contact）的 key disabled，type 可编辑', () => {
    const wrapper = mountWithFields([
      { key: 'contact', label: '联系人1', type: 'text', required: false, enabled: true }
    ])

    const keyInput = wrapper.find('.field-key-input')
    const typeSelect = wrapper.find('.field-type-select')

    expect(keyInput.attributes('disabled')).toBeDefined()
    expect(typeSelect.attributes('disabled')).toBeUndefined()
  })

  it('普通字段（如 title）的 key 和 type 都可编辑', () => {
    const wrapper = mountWithFields([
      { key: 'title', label: '项目名称', type: 'text', required: true, enabled: true }
    ])

    const keyInput = wrapper.find('.field-key-input')
    const typeSelect = wrapper.find('.field-type-select')

    expect(keyInput.attributes('disabled')).toBeUndefined()
    expect(typeSelect.attributes('disabled')).toBeUndefined()
  })

  it('自定义字段的 key 和 type 都可编辑', () => {
    const wrapper = mountWithFields([
      { key: 'customField', label: '自定义', type: 'text', required: false, enabled: true }
    ])

    const keyInput = wrapper.find('.field-key-input')
    const typeSelect = wrapper.find('.field-type-select')

    expect(keyInput.attributes('disabled')).toBeUndefined()
    expect(typeSelect.attributes('disabled')).toBeUndefined()
  })

  it('固定分组字段的 draggable 是 false', () => {
    const wrapper = mountWithFields([
      { key: 'contact', label: '联系人1', type: 'text', required: false, enabled: true }
    ])

    const fieldRow = wrapper.find('.field-row')
    expect(fieldRow.attributes('draggable')).toBe('false')
  })

  it('非固定分组字段的 draggable 是 true', () => {
    const wrapper = mountWithFields([
      { key: 'customField', label: '自定义', type: 'text', required: false, enabled: true }
    ])

    const fieldRow = wrapper.find('.field-row')
    expect(fieldRow.attributes('draggable')).toBe('true')
  })

  it('启用 checkbox 绑定 field.enabled', () => {
    const wrapper = mountWithFields([
      { key: 'customField', label: '自定义', type: 'text', required: false, enabled: true }
    ])

    const checkboxes = wrapper.findAllComponents({ name: 'ElCheckbox' })
    const enableCheckbox = checkboxes.find((c) => c.text().includes('启用'))

    expect(enableCheckbox).toBeTruthy()
    expect(enableCheckbox.props('modelValue')).toBe(true)
  })

  it('启用 checkbox 反映 field.enabled=false 状态', () => {
    const wrapper = mountWithFields([
      { key: 'customField', label: '自定义', type: 'text', required: false, enabled: false }
    ])

    const checkboxes = wrapper.findAllComponents({ name: 'ElCheckbox' })
    const enableCheckbox = checkboxes.find((c) => c.text().includes('启用'))

    expect(enableCheckbox).toBeTruthy()
    expect(enableCheckbox.props('modelValue')).toBe(false)
  })
})
