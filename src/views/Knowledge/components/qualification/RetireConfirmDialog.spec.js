import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import RetireConfirmDialog from './RetireConfirmDialog.vue'

describe('RetireConfirmDialog', () => {
  const stubs = {
    'el-dialog': { template: '<div class="el-dialog"><slot /><slot name="footer" /></div>' },
    'el-form': { template: '<form class="el-form"><slot /></form>' },
    'el-form-item': { template: '<div class="el-form-item"><slot /></div>' },
    'el-input': { template: '<textarea class="el-input" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />', props: ['modelValue'] },
    'el-checkbox': { template: '<input type="checkbox" class="el-checkbox" :checked="modelValue" @change="$emit(\'update:modelValue\', $event.target.checked)" />', props: ['modelValue'] },
    'el-button': { template: '<button class="el-button" :data-type="type" :disabled="disabled"><slot /></button>', props: ['disabled', 'type', 'loading'] }
  }

  it('should render certificate info', () => {
    const wrapper = mount(RetireConfirmDialog, {
      props: {
        modelValue: true,
        data: { id: 1, name: 'ISO认证', certificateNo: 'QC-001' }
      },
      global: { stubs }
    })
    expect(wrapper.text()).toContain('ISO认证')
    expect(wrapper.text()).toContain('QC-001')
  })

  it('should disable submit when reason is too short', async () => {
    const wrapper = mount(RetireConfirmDialog, {
      props: {
        modelValue: true,
        data: { id: 1, name: 'ISO', certificateNo: 'C001' }
      },
      global: { stubs }
    })
    const submitBtn = wrapper.findAll('.el-button').find(b => b.text().includes('确认下架'))
    expect(submitBtn?.attributes('disabled')).toBeDefined()
  })

  it('should emit confirm with id and reason on submit', async () => {
    const wrapper = mount(RetireConfirmDialog, {
      props: {
        modelValue: true,
        data: { id: 1, name: 'ISO', certificateNo: 'C001' }
      },
      global: { stubs }
    })
    wrapper.vm.form.reason = '证书已过期不再使用'
    wrapper.vm.form.confirmed = true
    await wrapper.vm.$nextTick()
    await wrapper.find('.el-button[data-type="danger"]').trigger('click')
    expect(wrapper.emitted('confirm')).toBeTruthy()
    expect(wrapper.emitted('confirm')[0][0]).toEqual({ id: 1, reason: '证书已过期不再使用' })
  })

  it('should reset form when dialog opens', async () => {
    const wrapper = mount(RetireConfirmDialog, {
      props: {
        modelValue: false,
        data: { id: 1, name: 'ISO', certificateNo: 'C001' }
      },
      global: { stubs }
    })
    wrapper.vm.form.reason = 'some reason'
    wrapper.vm.form.confirmed = true
    await wrapper.setProps({ modelValue: true })
    expect(wrapper.vm.form.reason).toBe('')
    expect(wrapper.vm.form.confirmed).toBe(false)
  })
})
