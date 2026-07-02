import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import WarehouseDialog from './WarehouseDialog.vue'

vi.mock('@/api/client', () => ({
  default: {
    get: vi.fn().mockResolvedValue({ data: [] }),
    post: vi.fn().mockResolvedValue({ data: { id: 1 } }),
    put: vi.fn().mockResolvedValue({}),
    delete: vi.fn().mockResolvedValue({})
  }
}))

vi.mock('@/stores/user.js', () => ({
  useUserStore: () => ({ userRole: 'admin', currentUser: { role: 'admin' } })
}))

const mountDialog = (props = {}) => mount(WarehouseDialog, {
  props: {
    modelValue: true,
    form: {},
    ...props
  },
  global: {
    stubs: {
      'el-dialog': { template: '<div class="el-dialog-stub"><slot /></div><slot name="footer" />' },
      'el-form': { template: '<form><slot /></form>' },
      'el-form-item': { template: '<div class="el-form-item-stub"><slot /></div>' },
      'el-input': { template: '<input class="el-input-stub" />' },
      'el-select': { template: '<select class="el-select-stub"><slot /></select>', props: ['modelValue', 'filterable', 'clearable', 'placeholder'] },
      'el-option': { template: '<option class="el-option-stub">{{ label }}</option>', props: ['label', 'value'] },
      'el-date-picker': { template: '<div class="el-date-picker-stub" :data-type="type"></div>', props: ['modelValue', 'type', 'range-separator', 'start-placeholder', 'end-placeholder', 'value-format'] },
      'el-row': { template: '<div><slot /></div>' },
      'el-col': { template: '<div><slot /></div>' },
      'el-divider': { template: '<div></div>' },
      'el-button': { template: '<button><slot /></button>' },
      'el-switch': { template: '<div></div>' },
      'el-upload': { template: '<div></div>' },
      'el-icon': true
    }
  }
})

describe('WarehouseDialog - 省份字段', () => {
  it('所在省份使用 el-select 下拉选择器而非 el-input', () => {
    const wrapper = mountDialog()
    const items = wrapper.findAll('.el-form-item-stub')
    const provinceItem = items[3]
    expect(provinceItem.find('.el-select-stub').exists()).toBe(true)
    expect(provinceItem.find('.el-input-stub').exists()).toBe(false)
  })

  it('省份下拉有 34 个省级行政区选项', () => {
    const wrapper = mountDialog()
    const items = wrapper.findAll('.el-form-item-stub')
    const provinceItem = items[3]
    const options = provinceItem.findAll('.el-option-stub')
    expect(options.length).toBe(34)
  })

  it('省份选项包含常见省级行政区名称', () => {
    const wrapper = mountDialog()
    const items = wrapper.findAll('.el-form-item-stub')
    const provinceItem = items[3]
    const text = provinceItem.text()
    expect(text).toContain('北京市')
    expect(text).toContain('广东省')
    expect(text).toContain('新疆维吾尔自治区')
    expect(text).toContain('香港特别行政区')
  })
})

describe('WarehouseDialog - 发票租期字段', () => {
  it('发票租期使用 el-date-picker 日期选择器而非 el-input', () => {
    const wrapper = mountDialog()
    const items = wrapper.findAll('.el-form-item-stub')
    const invoicePeriodItem = items[12]
    expect(invoicePeriodItem.find('.el-date-picker-stub').exists()).toBe(true)
    expect(invoicePeriodItem.find('.el-input-stub').exists()).toBe(false)
  })

  it('发票租期日期选择器类型为 daterange', () => {
    const wrapper = mountDialog()
    const items = wrapper.findAll('.el-form-item-stub')
    const invoicePeriodItem = items[12]
    const picker = invoicePeriodItem.find('.el-date-picker-stub')
    expect(picker.attributes('data-type')).toBe('daterange')
  })
})
