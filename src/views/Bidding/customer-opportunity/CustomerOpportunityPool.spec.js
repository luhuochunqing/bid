import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { ref } from 'vue'
import CustomerOpportunityPool from './CustomerOpportunityPool.vue'

function mountPool(props = {}) {
  const filters = props.filters ?? { keyword: '', sales: '', region: '', industry: '', status: '' }
  return mount(CustomerOpportunityPool, {
    props: {
      filters,
      'onUpdate:filters': (val) => Object.assign(filters, val),
      customers: [],
      salesUsers: [],
      regions: [],
      industries: [],
      statusOptions: [],
      loading: false,
      demoEnabled: true,
      ...props,
    },
    global: {
      stubs: {
        'el-icon': { template: '<span><slot /></span>' },
        'el-input': {
          name: 'ElInput',
          props: ['modelValue'],
          template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
        },
        'el-select': {
          name: 'ElSelect',
          props: ['modelValue'],
          template: '<select><slot /></select>',
        },
        'el-option': { template: '<option><slot /></option>' },
        'el-skeleton': { template: '<div><slot /></div>' },
        'el-table': { template: '<div class="table-stub"><slot /></div>' },
        'el-table-column': { template: '<div />' },
        'el-empty': { template: '<div>empty</div>' },
        'el-progress': { template: '<div />' },
        UserPicker: {
          name: 'UserPicker',
          props: ['modelValue', 'mode', 'valueField', 'placeholder', 'initialOptions', 'disabled'],
          emits: ['update:modelValue', 'select'],
          template: '<div class="user-picker-stub" />',
        },
      },
    },
  })
}

describe('CustomerOpportunityPool', () => {
  it('renders UserPicker for sales filter', () => {
    const wrapper = mountPool()

    const picker = wrapper.findComponent({ name: 'UserPicker' })
    expect(picker.exists()).toBe(true)
    expect(picker.props('mode')).toBe('search')
    expect(picker.props('valueField')).toBe('name')
    expect(picker.props('placeholder')).toBe('销售负责人')
  })

  it('passes salesUsers as initial options', () => {
    const salesUsers = [
      { id: 1, name: '张三', employeeNumber: '20260509' },
      { id: 2, name: '李四', employeeNumber: '20260510' },
    ]
    const wrapper = mountPool({ salesUsers })

    const picker = wrapper.findComponent({ name: 'UserPicker' })
    expect(picker.props('initialOptions')).toEqual(salesUsers)
  })

  it('binds UserPicker model value to filters.sales', async () => {
    const wrapper = mountPool()

    const picker = wrapper.findComponent({ name: 'UserPicker' })
    await picker.vm.$emit('update:modelValue', '张三')

    expect(wrapper.vm.filters.sales).toBe('张三')
  })
})
