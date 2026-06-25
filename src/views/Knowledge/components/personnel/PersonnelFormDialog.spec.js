import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
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
    'el-form-item': { template: '<div class="el-form-item"><slot /></div>' },
    'el-input': { template: '<input class="el-input" />', props: ['modelValue'] },
    'el-select': { template: '<select class="el-select"><slot /></select>', props: ['modelValue'] },
    'el-option': { template: '<option class="el-option" />', props: ['label', 'value'] },
    'el-button': {
      template: '<button class="el-button" :data-type="type" :disabled="disabled || loading"><slot /></button>',
      props: ['type', 'disabled', 'loading', 'size', 'plain', 'link']
    },
    'el-date-picker': { template: '<input class="el-date-picker" />', props: ['modelValue'] },
    'el-row': { template: '<div class="el-row"><slot /></div>' },
    'el-col': { template: '<div class="el-col"><slot /></div>' },
    'el-checkbox': { template: '<input type="checkbox" class="el-checkbox" />', props: ['modelValue'] },
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
})
