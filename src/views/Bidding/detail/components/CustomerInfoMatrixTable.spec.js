import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import CustomerInfoMatrixTable from './CustomerInfoMatrixTable.vue'
import { CUSTOMER_INFO_COLUMNS } from './customerInfoMatrixConfig.js'

const globalStubs = {
  ElTable: {
    name: 'ElTable',
    props: ['data', 'maxHeight', 'showHeader'],
    template: '<div class="el-table-stub"><slot /></div>',
  },
  ElTableColumn: {
    name: 'ElTableColumn',
    props: ['prop', 'label', 'width', 'minWidth', 'fixed'],
    template:
      '<div class="el-table-column-stub" :data-label="label" :data-width="width"><slot :row="sampleRow" /></div>',
    computed: {
      sampleRow() {
        return {
          roleLabel: '项目最高决策人',
          NAME: '',
          POSITION: '',
          CONTACT_METHOD: '',
          INFO_WIN_RATE_IMPACT: null,
        }
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

    const fixedColumn = wrapper.find('[data-label="客户信息（角色名）"]')
    expect(fixedColumn.exists()).toBe(true)
    expect(fixedColumn.attributes('data-width')).toBe('200')
    expect(wrapper.text()).toContain('董事长')
    expect(wrapper.text()).toContain('电话')
    expect(wrapper.text()).toContain('极高')
  })
})
