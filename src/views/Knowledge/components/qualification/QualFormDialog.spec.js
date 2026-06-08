import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import QualFormDialog from './QualFormDialog.vue'

// Mock Element Plus components
vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn() },
  ElNotification: vi.fn(),
  ElDialog: { template: '<div><slot /></div>' },
  ElForm: { template: '<div><slot /></div>', methods: { clearValidate: vi.fn(), validate: vi.fn().mockResolvedValue(true) } },
  ElFormItem: { template: '<div><slot /></div>' },
  ElInput: { template: '<input />' },
  ElDatePicker: { template: '<input />' },
  ElUpload: { template: '<div><slot /></div>' },
  ElButton: { template: '<button />' },
  ElRow: { template: '<div><slot /></div>' },
  ElCol: { template: '<div><slot /></div>' },
  ElDivider: { template: '<div><slot /></div>' },
  ElIcon: { template: '<span />' },
  ElTag: { template: '<span class="el-tag"><slot /></span>' },
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

describe('QualFormDialog - §4.2.1.3 编辑模式', () => {
  it('编辑时应显示只读状态标签', async () => {
    const wrapper = mount(QualFormDialog, {
      props: {
        modelValue: false,
        initialData: { id: 1, name: 'ISO证书', status: 'IN_STOCK', fileUrl: '/files/cert.pdf' },
        status: 'IN_STOCK'
      }
    })
    await wrapper.setProps({ modelValue: true })
    await nextTick()
    expect(wrapper.vm.editingId).toBe(1)
    expect(wrapper.vm.statusLabel('valid')).toBe('有效')
  })

  it('编辑时应正确回填字段', async () => {
    const wrapper = mount(QualFormDialog, {
      props: {
        modelValue: false,
        initialData: {
          id: 2, name: 'Test', level: 'AAA', issuer: 'Org', certificateNo: '123',
          issueDate: '2024-01-01', expiryDate: '2025-01-01',
          agency: 'Agency', agencyContact: '13800138000',
          certScope: 'Scope', certReviewNote: 'Note'
        }
      }
    })
    await wrapper.setProps({ modelValue: true })
    await nextTick()
    expect(wrapper.vm.form.name).toBe('Test')
    expect(wrapper.vm.form.level).toBe('AAA')
    expect(wrapper.vm.form.issuer).toBe('Org')
    expect(wrapper.vm.form.certificateNo).toBe('123')
  })

  it('编辑时应显示当前附件文件名', async () => {
    const wrapper = mount(QualFormDialog, {
      props: {
        modelValue: false,
        initialData: { id: 1, name: 'ISO', fileUrl: '/path/to/cert.pdf' }
      }
    })
    await wrapper.setProps({ modelValue: true })
    await nextTick()
    expect(wrapper.vm.currentAttachmentName).toBe('cert.pdf')
  })

  it('编辑时附件校验应通过（无需上传新文件）', async () => {
    const wrapper = mount(QualFormDialog, {
      props: {
        modelValue: false,
        initialData: { id: 1, name: 'ISO' }
      }
    })
    await wrapper.setProps({ modelValue: true })
    await nextTick()
    const rules = wrapper.vm.formRules
    let passed = false
    rules.attachment[0].validator(null, null, (err) => { if (!err) passed = true })
    expect(passed).toBe(true)
  })

  it('清除已选文件后应恢复保留原附件状态', async () => {
    const wrapper = mount(QualFormDialog, {
      props: {
        modelValue: false,
        initialData: { id: 1, name: 'ISO', fileUrl: '/files/cert.pdf' }
      }
    })
    await wrapper.setProps({ modelValue: true })
    await nextTick()
    const file = new File(['content'], 'new.pdf', { type: 'application/pdf' })
    await wrapper.vm.onCertFileChange({ raw: file })
    expect(wrapper.vm.certFile).not.toBeNull()
    wrapper.vm.clearCertFile()
    expect(wrapper.vm.certFile).toBeNull()
    expect(wrapper.vm.certFileList).toEqual([])
  })
})
