import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { ElMessage } from 'element-plus'
import PersonnelFormDialog from './PersonnelFormDialog.vue'

vi.mock('element-plus', () => ({
  ElMessage: { warning: vi.fn(), error: vi.fn(), success: vi.fn() },
  ElMessageBox: { alert: vi.fn() }
}))

vi.mock('@/api/modules/personnel.js', () => ({
  default: { create: vi.fn(), update: vi.fn(), uploadCertAttachment: vi.fn() }
}))

describe('PersonnelFormDialog', () => {
  const stubs = {
    'el-dialog': {
      template: '<div class="el-dialog" :data-title="title"><slot /><slot name="footer" /></div>',
      props: ['title', 'modelValue']
    },
    'el-tabs': {
      template: '<div class="el-tabs"><slot /></div>',
      props: ['modelValue']
    },
    'el-tab-pane': {
      template: '<div class="el-tab-pane" :data-name="name"><slot /></div>',
      props: ['label', 'name']
    },
    'el-form': { template: '<form class="el-form"><slot /></form>' },
    'el-form-item': { template: '<div class="el-form-item" :data-label="label"><slot /></div>', props: ['label', 'required'] },
    'el-input': { template: '<input class="el-input" />', props: ['modelValue'] },
    'el-select': { template: '<select class="el-select"><slot /></select>', props: ['modelValue'] },
    'el-option': { template: '<option class="el-option" />', props: ['label', 'value'] },
    'el-button': {
      template: '<button class="el-button" :data-type="type" :disabled="disabled || loading"><slot /></button>',
      props: ['type', 'disabled', 'loading', 'size', 'plain', 'link']
    },
    'el-date-picker': { template: '<input class="el-date-picker" v-bind="$attrs" />', inheritAttrs: false },
    'el-row': { template: '<div class="el-row"><slot /></div>' },
    'el-col': { template: '<div class="el-col"><slot /></div>' },
    'el-checkbox': {
      template: '<input type="checkbox" class="el-checkbox" :checked="modelValue" @change="onChange($event)" />',
      props: ['modelValue'],
      methods: {
        onChange(e) { this.$emit('update:modelValue', e.target.checked); this.$emit('change', e.target.checked) }
      }
    },
    'el-upload': { template: '<div class="el-upload"><slot /></div>' },
    'el-icon': { template: '<span class="el-icon"><slot /></span>' }
  }

  const defaultProps = { modelValue: true }

  it('should show 下一步 button on basic tab', () => {
    const wrapper = mount(PersonnelFormDialog, {
      props: defaultProps,
      global: { stubs }
    })
    wrapper.vm.activeTab = 'basic'
    const buttons = wrapper.findAll('.el-button')
    const nextBtn = buttons.find(b => b.text().includes('下一步'))
    const saveBtn = buttons.find(b => b.text().includes('保存'))
    expect(nextBtn).toBeTruthy()
    expect(saveBtn).toBeFalsy()
  })

  it('should switch to education tab when clicking 下一步 on basic tab with valid data', async () => {
    const wrapper = mount(PersonnelFormDialog, {
      props: defaultProps,
      global: { stubs }
    })
    wrapper.vm.activeTab = 'basic'
    wrapper.vm.form.name = '张三'
    wrapper.vm.form.employeeNumber = 'E001'
    await wrapper.vm.$nextTick()
    const buttons = wrapper.findAll('.el-button')
    const nextBtn = buttons.find(b => b.text().includes('下一步'))
    await nextBtn.trigger('click')
    expect(wrapper.vm.activeTab).toBe('education')
  })

  it('should NOT switch tab when clicking 下一步 on basic tab with empty name', async () => {
    const wrapper = mount(PersonnelFormDialog, {
      props: defaultProps,
      global: { stubs }
    })
    wrapper.vm.activeTab = 'basic'
    await wrapper.vm.$nextTick()
    const buttons = wrapper.findAll('.el-button')
    const nextBtn = buttons.find(b => b.text().includes('下一步'))
    await nextBtn.trigger('click')
    expect(wrapper.vm.activeTab).toBe('basic')
  })

  it('should switch to certificate tab when clicking 下一步 on education tab with valid data', async () => {
    const wrapper = mount(PersonnelFormDialog, {
      props: defaultProps,
      global: { stubs }
    })
    wrapper.vm.activeTab = 'education'
    wrapper.vm.form.educations = [{ schoolName: '清华大学', highestEducation: '本科', studyForm: '全日制', startDate: '2020-09', endDate: '2024-06' }]
    await wrapper.vm.$nextTick()
    const buttons = wrapper.findAll('.el-button')
    const nextBtn = buttons.find(b => b.text().includes('下一步'))
    await nextBtn.trigger('click')
    expect(wrapper.vm.activeTab).toBe('certificate')
  })

  it('should NOT switch tab when clicking 下一步 on education tab with empty educations', async () => {
    const wrapper = mount(PersonnelFormDialog, {
      props: defaultProps,
      global: { stubs }
    })
    wrapper.vm.activeTab = 'education'
    await wrapper.vm.$nextTick()
    const buttons = wrapper.findAll('.el-button')
    const nextBtn = buttons.find(b => b.text().includes('下一步'))
    await nextBtn.trigger('click')
    expect(wrapper.vm.activeTab).toBe('education')
  })

  it('should show 保存 button on certificate tab', async () => {
    const wrapper = mount(PersonnelFormDialog, {
      props: defaultProps,
      global: { stubs }
    })
    wrapper.vm.activeTab = 'certificate'
    await wrapper.vm.$nextTick()
    const buttons = wrapper.findAll('.el-button')
    const saveBtn = buttons.find(b => b.text().includes('保存'))
    const nextBtn = buttons.find(b => b.text().includes('下一步'))
    expect(saveBtn).toBeTruthy()
    expect(nextBtn).toBeFalsy()
  })

  it('should show 下一步 button on basic tab in edit mode', async () => {
    const wrapper = mount(PersonnelFormDialog, {
      props: {
        ...defaultProps,
        personnel: { id: 1, name: '张三', employeeNumber: 'E001' }
      },
      global: { stubs }
    })
    wrapper.vm.activeTab = 'basic'
    await wrapper.vm.$nextTick()
    const buttons = wrapper.findAll('.el-button')
    const nextBtn = buttons.find(b => b.text().includes('下一步'))
    expect(nextBtn).toBeTruthy()
  })

  // ===== CO-535: 永久有效字段与到期日期联动 =====

  it('should disable expiry date when permanent is checked', async () => {
    // CO-535 测试要点 2：勾选永久有效 → 到期日期置灰不可填
    const wrapper = mount(PersonnelFormDialog, {
      props: defaultProps,
      global: { stubs }
    })
    wrapper.vm.form.certificates = [{
      name: '一级建造师', certificateNumber: 'C001', type: 'OTHER',
      expiryDate: null, isPermanent: true, title: '', remark: '',
      attachmentName: 'test.pdf', attachmentUrl: 'pending:test.pdf'
    }]
    await wrapper.vm.$nextTick()
    const datePicker = wrapper.find('[data-label="到期日期"] .el-date-picker')
    expect(datePicker.attributes('disabled')).toBeDefined()
  })

  it('should clear expiry date when permanent is checked', async () => {
    // CO-535 测试要点 3：勾选永久有效 → 到期日期清空
    const wrapper = mount(PersonnelFormDialog, {
      props: defaultProps,
      global: { stubs }
    })
    wrapper.vm.form.certificates = [{
      name: '一级建造师', certificateNumber: 'C001', type: 'OTHER',
      expiryDate: '2027-01-01', isPermanent: false, title: '', remark: '',
      attachmentName: 'test.pdf', attachmentUrl: 'pending:test.pdf'
    }]
    await wrapper.vm.$nextTick()
    const checkbox = wrapper.find('.el-checkbox')
    checkbox.element.checked = true
    await checkbox.trigger('change')
    expect(wrapper.vm.form.certificates[0].isPermanent).toBe(true)
    expect(wrapper.vm.form.certificates[0].expiryDate).toBe(null)
  })

  it('should restore editable expiry date when permanent is unchecked', async () => {
    // CO-535 测试要点 4：取消勾选 → 到期日期恢复可填
    const wrapper = mount(PersonnelFormDialog, {
      props: defaultProps,
      global: { stubs }
    })
    wrapper.vm.form.certificates = [{
      name: '一级建造师', certificateNumber: 'C001', type: 'OTHER',
      expiryDate: null, isPermanent: true, title: '', remark: '',
      attachmentName: 'test.pdf', attachmentUrl: 'pending:test.pdf'
    }]
    await wrapper.vm.$nextTick()
    const datePicker = wrapper.find('[data-label="到期日期"] .el-date-picker')
    expect(datePicker.attributes('disabled')).toBeDefined()
    // 取消勾选
    wrapper.vm.form.certificates[0].isPermanent = false
    await wrapper.vm.$nextTick()
    expect(datePicker.attributes('disabled')).toBeUndefined()
  })

  it('should block submit when permanent unchecked and expiry date empty', async () => {
    // CO-535 测试要点 1：永久有效未勾选 → 到期日期必填，不填无法保存
    const wrapper = mount(PersonnelFormDialog, {
      props: defaultProps,
      global: { stubs }
    })
    // 填写 basic + education 让前置校验通过
    wrapper.vm.form.name = '张三'
    wrapper.vm.form.employeeNumber = 'E001'
    wrapper.vm.form.educations = [{
      schoolName: '清华大学', highestEducation: '本科',
      studyForm: '全日制', endDate: '2024-06'
    }]
    // 添加一条证书：未勾选永久有效，到期日期为空
    wrapper.vm.form.certificates = [{
      name: '一级建造师', certificateNumber: 'C001', type: 'OTHER',
      expiryDate: null, isPermanent: false, title: '', remark: '',
      attachmentName: 'test.pdf', attachmentUrl: 'pending:test.pdf'
    }]
    wrapper.vm.activeTab = 'certificate'
    await wrapper.vm.$nextTick()
    const saveBtn = wrapper.findAll('.el-button').find(b => b.text().includes('保存'))
    await saveBtn.trigger('click')
    expect(ElMessage.warning).toHaveBeenCalledWith('永久有效未勾选时，到期日期必填')
  })
})
