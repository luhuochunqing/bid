import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import QualFormDialog from './QualFormDialog.vue'

// Mock Element Plus components
vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn() },
  ElNotification: vi.fn(),
  ElDialog: { template: '<div><slot /></div>' },
  ElForm: { template: '<div><slot /></div>' },
  ElFormItem: { template: '<div><slot /></div>' },
  ElInput: { template: '<input />' },
  ElDatePicker: { template: '<input />' },
  ElUpload: { template: '<div><slot /></div>' },
  ElButton: { template: '<button />' },
  ElRow: { template: '<div><slot /></div>' },
  ElCol: { template: '<div><slot /></div>' },
  ElDivider: { template: '<div><slot /></div>' },
  ElIcon: { template: '<span />' },
}))

describe('QualFormDialog - §4.2.1.1 必填字段校验', () => {
  it('level 应为必填', () => {
    const wrapper = mount(QualFormDialog, { props: { modelValue: true } })
    const rules = wrapper.vm.formRules
    expect(rules.level).toBeDefined()
    expect(rules.level[0].required).toBe(true)
    expect(rules.level[0].message).toContain('等级')
  })

  it('agency 应为必填', () => {
    const wrapper = mount(QualFormDialog, { props: { modelValue: true } })
    const rules = wrapper.vm.formRules
    expect(rules.agency).toBeDefined()
    expect(rules.agency[0].required).toBe(true)
    expect(rules.agency[0].message).toContain('代理机构')
  })

  it('agencyContact 应为必填', () => {
    const wrapper = mount(QualFormDialog, { props: { modelValue: true } })
    const rules = wrapper.vm.formRules
    expect(rules.agencyContact).toBeDefined()
    expect(rules.agencyContact[0].required).toBe(true)
    expect(rules.agencyContact[0].message).toContain('代理联系方式')
  })

  it('agencyContact 应保留格式校验', () => {
    const wrapper = mount(QualFormDialog, { props: { modelValue: true } })
    const rules = wrapper.vm.formRules
    expect(rules.agencyContact.length).toBeGreaterThanOrEqual(2)
    expect(rules.agencyContact[1].validator).toBeTypeOf('function')
  })

  it('certScope 应为必填', () => {
    const wrapper = mount(QualFormDialog, { props: { modelValue: true } })
    const rules = wrapper.vm.formRules
    expect(rules.certScope).toBeDefined()
    expect(rules.certScope[0].required).toBe(true)
    expect(rules.certScope[0].message).toContain('认证范围')
  })

  it('attachment 应为必填（新建时）', () => {
    const wrapper = mount(QualFormDialog, { props: { modelValue: true } })
    const rules = wrapper.vm.formRules
    expect(rules.attachment).toBeDefined()
    expect(rules.attachment[0].validator).toBeTypeOf('function')
  })

  it('name 应保持必填', () => {
    const wrapper = mount(QualFormDialog, { props: { modelValue: true } })
    const rules = wrapper.vm.formRules
    expect(rules.name[0].required).toBe(true)
  })

  it('issuer 应保持必填', () => {
    const wrapper = mount(QualFormDialog, { props: { modelValue: true } })
    const rules = wrapper.vm.formRules
    expect(rules.issuer[0].required).toBe(true)
  })

  it('certificateNo 应保持必填', () => {
    const wrapper = mount(QualFormDialog, { props: { modelValue: true } })
    const rules = wrapper.vm.formRules
    expect(rules.certificateNo[0].required).toBe(true)
  })

  it('issueDate 应保持必填', () => {
    const wrapper = mount(QualFormDialog, { props: { modelValue: true } })
    const rules = wrapper.vm.formRules
    expect(rules.issueDate[0].required).toBe(true)
  })

  it('expiryDate 应保持必填', () => {
    const wrapper = mount(QualFormDialog, { props: { modelValue: true } })
    const rules = wrapper.vm.formRules
    expect(rules.expiryDate[0].required).toBe(true)
  })
})
