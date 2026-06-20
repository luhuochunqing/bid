import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import CustomerInfoMatrixTable from './CustomerInfoMatrixTable.vue'
import {
  CONTACT_METHOD_OPTIONS,
  CUSTOMER_INFO_COLUMNS,
  IMPACT_OPTIONS,
  POSITION_OPTIONS,
  TENDENCY_OPTIONS,
} from './customerInfoMatrixConfig.js'

const globalStubs = {
  ElTable: {
    name: 'ElTable',
    props: ['data', 'maxHeight', 'showHeader'],
    provide() {
      return { tableRows: this.data }
    },
    template: '<div class="el-table-stub"><slot /></div>',
  },
  ElTableColumn: {
    name: 'ElTableColumn',
    props: ['prop', 'label', 'width', 'minWidth', 'fixed'],
    inject: ['tableRows'],
    template:
      '<div class="el-table-column-stub" :data-label="label" :data-width="width"><slot :row="sampleRow" /></div>',
    computed: {
      sampleRow() {
        return this.tableRows?.[0] || {}
      },
    },
  },
  ElInput: {
    name: 'ElInput',
    props: ['modelValue', 'disabled', 'placeholder', 'size', 'clearable'],
    emits: ['update:modelValue', 'change'],
    template: '<input class="el-input-stub" :placeholder="placeholder" />',
  },
  ElSelect: {
    name: 'ElSelect',
    props: ['modelValue', 'disabled', 'placeholder', 'size', 'clearable'],
    emits: ['update:modelValue', 'change'],
    template: '<select class="el-select-stub"><slot /></select>',
  },
  ElOption: {
    name: 'ElOption',
    props: ['label', 'value'],
    template: '<option :value="value">{{ label }}</option>',
  },
  ElSwitch: {
    name: 'ElSwitch',
    props: ['modelValue', 'disabled'],
    emits: ['update:modelValue', 'change'],
    template: '<input class="el-switch-stub" type="checkbox" />',
  },
}

describe('CustomerInfoMatrixTable', () => {
  it('renders matrix columns without relying on parent-only constants', () => {
    const wrapper = mount(CustomerInfoMatrixTable, {
      props: {
        localData: [{ roleKey: 'PROJECT_HIGHEST_DECISION_MAKER', roleLabel: '项目最高决策人' }],
        editableColumns: CUSTOMER_INFO_COLUMNS.slice(1),
        disabled: false,
      },
      global: { stubs: globalStubs },
    })

    // Verify columns render: role column + all editable column stubs should be present
    const columnStubs = wrapper.findAll('.el-table-column-stub')
    expect(columnStubs.length).toBe(CUSTOMER_INFO_COLUMNS.length)
    expect(columnStubs[0].attributes('data-label')).toBe('角色')
    // First editable column (CUSTOMER_INFO_COLUMNS[1]) has label "联系方式"
    expect(columnStubs[1].attributes('data-label')).toBe('联系方式')
    expect(columnStubs[1].attributes('data-width')).toBe('160')
  })

  it('uses numeric index values with Chinese labels for integration option fields', () => {
    expect(POSITION_OPTIONS[0]).toEqual({ label: '项目最高决策人', value: '1' })
    expect(POSITION_OPTIONS[13]).toEqual({ label: '专家3', value: '14' })
    expect(CONTACT_METHOD_OPTIONS[2]).toEqual({ label: '供应商渠道推荐', value: '3' })
    expect(TENDENCY_OPTIONS).toEqual([
      { label: '支持', value: '1' },
      { label: '中立', value: '2' },
      { label: '反对', value: '3' },
    ])
    expect(IMPACT_OPTIONS[3]).toEqual({ label: '50%', value: '4' })
  })

  it('shows role label column so tender 285 external customer row is identifiable', () => {
    const wrapper = mount(CustomerInfoMatrixTable, {
      props: {
        localData: [
          {
            roleKey: 'EXTERNAL_ROLE_1',
            roleLabel: '外部对接人1',
            NAME: '张三',
            CONTACT_INFO: '18888888888',
            XIYU_CONTACT: '张頔',
            INFO_TENDENCY_BASIS: '3333',
          },
        ],
        editableColumns: CUSTOMER_INFO_COLUMNS,
        disabled: false,
      },
      global: { stubs: globalStubs },
    })

    const columnStubs = wrapper.findAll('.el-table-column-stub')
    expect(columnStubs[0].attributes('data-label')).toBe('角色')
    expect(wrapper.text()).toContain('外部对接人1')
    expect(wrapper.text()).not.toContain('EXTERNAL_ROLE_1')
  })
})
