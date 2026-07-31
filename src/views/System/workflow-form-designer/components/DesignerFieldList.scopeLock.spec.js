// Input: DesignerFieldList component with scope-aware preset field lock constraints (CO-601 US2)
// Output: project.* scope 预置字段 key/type disabled + 删除隐藏；自定义字段全可用；tender.entry 原行为回归
// Pos: src/views/System/workflow-form-designer/components/ - DesignerFieldList scope lock unit tests

import { mount } from '@vue/test-utils'
import { describe, it, expect } from 'vitest'

import DesignerFieldList from './DesignerFieldList.vue'

const elementStubs = {
  'el-button': { template: '<button><slot /></button>' },
  'el-input': {
    name: 'ElInput',
    props: ['modelValue', 'disabled', 'placeholder', 'type', 'rows'],
    template: '<input :value="modelValue" :disabled="disabled" />'
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

function mountWithScope(fields, scope) {
  return mount(DesignerFieldList, {
    props: {
      fields,
      fieldTypes: [
        { label: '文本', value: 'text' },
        { label: '多行文本', value: 'textarea' },
        { label: '数字', value: 'number' }
      ],
      readonly: false,
      scope
    },
    global: { stubs: elementStubs }
  })
}

function rowOf(wrapper, key) {
  return wrapper.findAll('.field-row').find((row) => {
    const input = row.find('.field-key-input')
    return input.exists() && input.element.value === key
  })
}

describe('DesignerFieldList scope 维度锁定（CO-601 US2）', () => {
  describe('project.basic scope', () => {
    const fields = [
      { key: 'name', label: '项目名称', type: 'text', required: true, enabled: true },
      { key: 'budget', label: '预算', type: 'number', required: false, enabled: true },
      { key: 'budgetLevel', label: '客户预算等级', type: 'text', required: false, enabled: true }
    ]

    it('预置字段 name 的 key/type disabled，删除按钮隐藏', () => {
      const wrapper = mountWithScope(fields, 'project.basic')
      const row = rowOf(wrapper, 'name')
      expect(row.find('.field-key-input').attributes('disabled')).toBeDefined()
      expect(row.find('.field-type-select').attributes('disabled')).toBeDefined()
      expect(row.findAll('button').find((b) => b.text() === '删')).toBeUndefined()
    })

    it('预置字段 budget 的 key/type disabled', () => {
      const wrapper = mountWithScope(fields, 'project.basic')
      const row = rowOf(wrapper, 'budget')
      expect(row.find('.field-key-input').attributes('disabled')).toBeDefined()
      expect(row.find('.field-type-select').attributes('disabled')).toBeDefined()
    })

    it('自定义字段 budgetLevel 的 key/type 可编辑，删除按钮可见，可拖拽', () => {
      const wrapper = mountWithScope(fields, 'project.basic')
      const row = rowOf(wrapper, 'budgetLevel')
      expect(row.find('.field-key-input').attributes('disabled')).toBeUndefined()
      expect(row.find('.field-type-select').attributes('disabled')).toBeUndefined()
      expect(row.findAll('button').find((b) => b.text() === '删')).toBeDefined()
      expect(row.attributes('draggable')).toBe('true')
    })
  })

  describe('project.initiation scope', () => {
    it('预置字段 projectName / contactName 均锁定', () => {
      const wrapper = mountWithScope([
        { key: 'projectName', label: '项目名称', type: 'text', required: true, enabled: true },
        { key: 'contactName', label: '联系人', type: 'text', required: false, enabled: true }
      ], 'project.initiation')
      for (const key of ['projectName', 'contactName']) {
        const row = rowOf(wrapper, key)
        expect(row.find('.field-key-input').attributes('disabled')).toBeDefined()
        expect(row.find('.field-type-select').attributes('disabled')).toBeDefined()
      }
    })

    it('tender.entry 专属锁定 key（pastedText）在 project.initiation 下不锁定（scope 隔离）', () => {
      const wrapper = mountWithScope([
        { key: 'pastedText', label: '粘贴文本', type: 'textarea', required: false, enabled: true }
      ], 'project.initiation')
      const row = rowOf(wrapper, 'pastedText')
      expect(row.find('.field-key-input').attributes('disabled')).toBeUndefined()
      expect(row.find('.field-type-select').attributes('disabled')).toBeUndefined()
    })
  })

  describe('project.detail scope', () => {
    it('预置字段 description 锁定，自定义字段可编辑', () => {
      const wrapper = mountWithScope([
        { key: 'description', label: '项目描述', type: 'textarea', required: false, enabled: true },
        { key: 'customNote', label: '自定义备注', type: 'text', required: false, enabled: true }
      ], 'project.detail')
      expect(rowOf(wrapper, 'description').find('.field-key-input').attributes('disabled')).toBeDefined()
      expect(rowOf(wrapper, 'customNote').find('.field-key-input').attributes('disabled')).toBeUndefined()
    })
  })

  describe('tender.entry 回归（原 LOCKED_FIELD_KEYS / FIXED_GROUP_KEYS 不变）', () => {
    it('region 仍 key/type 双锁', () => {
      const wrapper = mountWithScope([
        { key: 'region', label: '地区', type: 'text', required: true, enabled: true }
      ], 'tender.entry')
      const row = rowOf(wrapper, 'region')
      expect(row.find('.field-key-input').attributes('disabled')).toBeDefined()
      expect(row.find('.field-type-select').attributes('disabled')).toBeDefined()
    })

    it('contact 仍 key 锁 + type 可改 + 不可拖拽（固定分组）', () => {
      const wrapper = mountWithScope([
        { key: 'contact', label: '联系人1', type: 'text', required: false, enabled: true }
      ], 'tender.entry')
      const row = rowOf(wrapper, 'contact')
      expect(row.find('.field-key-input').attributes('disabled')).toBeDefined()
      expect(row.find('.field-type-select').attributes('disabled')).toBeUndefined()
      expect(row.attributes('draggable')).toBe('false')
    })
  })

  describe('不传 scope（向后兼容：走 tender.entry 原清单）', () => {
    it('region 锁定行为与现状一致', () => {
      const wrapper = mountWithScope([
        { key: 'region', label: '地区', type: 'text', required: true, enabled: true }
      ], undefined)
      const row = rowOf(wrapper, 'region')
      expect(row.find('.field-key-input').attributes('disabled')).toBeDefined()
      expect(row.find('.field-type-select').attributes('disabled')).toBeDefined()
    })
  })
})
