import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import WarehouseDialog from './WarehouseDialog.vue'
import { ElMessage } from 'element-plus'

vi.mock('element-plus', () => ({
  ElMessage: {
    error: vi.fn(),
    success: vi.fn()
  },
  ElMessageBox: { confirm: vi.fn().mockResolvedValue() }
}))

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

describe('WarehouseDialog - 附件必填校验', () => {
  const mountWithValidForm = (formOverrides = {}) => {
    const wrapper = mountDialog({
      form: {
        name: '测试仓库',
        type: 'SELF_OPERATED',
        region: '华东',
        province: '上海市',
        address: '测试地址',
        area: 100,
        contactPerson: '张三',
        startDate: '2025-01-01',
        endDate: '2025-12-31',
        lessor: '出租方',
        lessee: '承租方',
        ...formOverrides
      }
    })
    wrapper.vm.formRef = { validate: vi.fn().mockResolvedValue() }
    return wrapper
  }

  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('hasPropertyCert=true 且无附件时提交被拦截', async () => {
    const wrapper = mountWithValidForm({ hasPropertyCert: true })
    await wrapper.vm.handleSubmit()
    expect(ElMessage.error).toHaveBeenCalledWith('请上传产权证附件')
  })

  it('hasInvoice=true 且无附件时提交被拦截', async () => {
    const wrapper = mountWithValidForm({ hasInvoice: true })
    await wrapper.vm.handleSubmit()
    expect(ElMessage.error).toHaveBeenCalledWith('请上传发票附件')
  })

  it('无租赁合同附件时提交被拦截（无条件必填）', async () => {
    const wrapper = mountWithValidForm()
    await wrapper.vm.handleSubmit()
    expect(ElMessage.error).toHaveBeenCalledWith('请上传租赁合同附件')
  })

  it('三个资料核验开关关闭时仅触发租赁合同必填校验', async () => {
    const wrapper = mountWithValidForm({
      hasPropertyCert: false,
      hasInvoice: false,
      hasPhotos: false,
      hasLeaseContract: false
    })
    await wrapper.vm.handleSubmit()
    const errorCalls = ElMessage.error.mock.calls
    const attachmentErrors = errorCalls.filter(c =>
      c[0].includes('附件') || c[0].includes('照片')
    )
    expect(attachmentErrors.length).toBe(1)
    expect(attachmentErrors[0][0]).toContain('租赁合同')
  })

  it('hasPhotos=true 且无附件时原有校验仍然有效（回归）', async () => {
    const wrapper = mountWithValidForm({ hasPhotos: true })
    await wrapper.vm.handleSubmit()
    expect(ElMessage.error).toHaveBeenCalledWith('请至少上传 1 张内外照片')
  })

  it('hasPropertyCert=true 且有附件时不触发校验', async () => {
    const wrapper = mountWithValidForm({ hasPropertyCert: true })
    wrapper.vm.certFiles = [{ name: 'cert.pdf', uid: 1 }]
    await wrapper.vm.handleSubmit()
    const certErrors = ElMessage.error.mock.calls.filter(c => c[0].includes('产权证'))
    expect(certErrors.length).toBe(0)
  })

  it('hasInvoice=true 且有附件时不触发校验', async () => {
    const wrapper = mountWithValidForm({ hasInvoice: true })
    wrapper.vm.invoiceFiles = [{ name: 'invoice.pdf', uid: 1 }]
    await wrapper.vm.handleSubmit()
    const invoiceErrors = ElMessage.error.mock.calls.filter(c => c[0].includes('发票'))
    expect(invoiceErrors.length).toBe(0)
  })

  it('hasLeaseContract=true 且有附件时不触发校验', async () => {
    const wrapper = mountWithValidForm({ hasLeaseContract: true })
    wrapper.vm.leaseContractFiles = [{ name: 'lease.pdf', uid: 1 }]
    await wrapper.vm.handleSubmit()
    const leaseErrors = ElMessage.error.mock.calls.filter(c => c[0].includes('租赁合同'))
    expect(leaseErrors.length).toBe(0)
  })

  it('hasPhotos=true 且有附件时不触发校验', async () => {
    const wrapper = mountWithValidForm({ hasPhotos: true })
    wrapper.vm.photoFiles = [{ name: 'photo.jpg', uid: 1 }]
    await wrapper.vm.handleSubmit()
    const photoErrors = ElMessage.error.mock.calls.filter(c => c[0].includes('照片') || c[0].includes('内外'))
    expect(photoErrors.length).toBe(0)
  })

  it('所有开关开启且都有附件时不触发任何附件校验', async () => {
    const wrapper = mountWithValidForm({
      hasPropertyCert: true,
      hasInvoice: true,
      hasPhotos: true,
      hasLeaseContract: true
    })
    wrapper.vm.certFiles = [{ name: 'cert.pdf', uid: 1 }]
    wrapper.vm.invoiceFiles = [{ name: 'invoice.pdf', uid: 1 }]
    wrapper.vm.photoFiles = [{ name: 'photo.jpg', uid: 1 }]
    wrapper.vm.leaseContractFiles = [{ name: 'lease.pdf', uid: 1 }]
    await wrapper.vm.handleSubmit()
    const attachmentErrors = ElMessage.error.mock.calls.filter(c =>
      c[0].includes('附件') || c[0].includes('照片') || c[0].includes('产权证') || c[0].includes('发票') || c[0].includes('租赁')
    )
    expect(attachmentErrors.length).toBe(0)
  })
})
