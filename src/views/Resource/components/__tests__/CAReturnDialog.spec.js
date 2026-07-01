import { mount, flushPromises } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import CAReturnDialog from '../CAReturnDialog.vue'

const mockCa = {
  id: 1,
  platformIds: ['政采云'],
  caType: 'ENTITY_CA',
  caTypeLabel: '实体CA',
  sealType: 'OFFICIAL_SEAL',
  sealTypeLabel: '公章',
  borrowStatus: 'BORROWED',
  currentBorrowerName: '王五',
  status: 'ACTIVE'
}

const mockApplications = [
  { id: 101, purpose: '项目A投标', borrowDate: '2026-06-20', status: 'APPROVED', statusLabel: '已通过' },
  { id: 102, purpose: '项目B投标', borrowDate: '2026-06-25', status: 'PENDING', statusLabel: '待审批' },
  { id: 103, purpose: '项目C投标', borrowDate: '2026-06-15', status: 'RETURNED', statusLabel: '已归还' }
]

const stubs = {
  'el-dialog': { template: '<div v-if="modelValue"><slot /><slot name="footer" /></div>', props: ['modelValue'] },
  'el-alert': { template: '<div><slot /></div>' },
  'el-form': { template: '<form><slot /></form>' },
  'el-form-item': { template: '<div><slot /></div>' },
  'el-input': { template: '<input />', props: ['modelValue'] },
  'el-select': { template: '<div><slot /></div>', props: ['modelValue'] },
  'el-option': { template: '<div />', props: ['value', 'label'] },
  'el-date-picker': { template: '<input />', props: ['modelValue'] },
  'el-button': { template: '<button :disabled="disabled"><slot /></button>', props: ['disabled', 'loading'] },
  'el-tag': { template: '<span><slot /></span>' }
}

function mountDialog(props = {}) {
  return mount(CAReturnDialog, {
    props: {
      modelValue: true,
      ca: mockCa,
      borrowApplications: mockApplications,
      submitting: false,
      ...props
    },
    global: { stubs }
  })
}

describe('CAReturnDialog', () => {
  it('只显示 APPROVED 状态的借用申请，不显示 PENDING 或 RETURNED', async () => {
    const wrapper = mountDialog()
    await flushPromises()

    const vm = wrapper.vm
    const activeApps = vm.activeApplications

    expect(activeApps.length).toBe(1)
    expect(activeApps[0].id).toBe(101)
    expect(activeApps[0].status).toBe('APPROVED')

    const pendingApp = activeApps.find(a => a.status === 'PENDING')
    expect(pendingApp).toBeUndefined()

    const returnedApp = activeApps.find(a => a.status === 'RETURNED')
    expect(returnedApp).toBeUndefined()
  })

  it('只有一条进行中申请时自动选中', async () => {
    const wrapper = mount(CAReturnDialog, {
      props: {
        modelValue: false,
        ca: mockCa,
        borrowApplications: [{ id: 101, status: 'APPROVED', purpose: '测试', borrowDate: '2026-06-20' }],
        submitting: false
      },
      global: { stubs }
    })
    await flushPromises()

    await wrapper.setProps({ modelValue: true })
    await flushPromises()

    expect(wrapper.vm.activeApplications.length).toBe(1)
    expect(wrapper.vm.form.applicationId).toBe(101)
  })

  it('没有进行中申请时列表为空', async () => {
    const wrapper = mountDialog({
      borrowApplications: [{ id: 201, status: 'RETURNED', purpose: '已归还', borrowDate: '2026-06-01' }]
    })
    await flushPromises()

    expect(wrapper.vm.activeApplications.length).toBe(0)
  })

  it('弹窗可见时正确重置表单', async () => {
    const wrapper = mountDialog()
    await flushPromises()

    expect(wrapper.vm.form.remark).toBe('')
    expect(wrapper.vm.form.actualReturnDate).toBeTruthy()
  })
})
