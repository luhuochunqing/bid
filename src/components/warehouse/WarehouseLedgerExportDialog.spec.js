import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import WarehouseLedgerExportDialog from './WarehouseLedgerExportDialog.vue'

vi.mock('@/api/client', () => ({
  default: { get: vi.fn(), post: vi.fn() }
}))

describe('WarehouseLedgerExportDialog', () => {
  const globalStubs = {
    'el-dialog': { template: '<div><slot /><slot name="footer" /></div>' },
    'el-form': true,
    'el-form-item': true,
    'el-radio-group': true,
    'el-radio': true,
    'el-checkbox-group': true,
    'el-checkbox': true,
    'el-tag': true,
    'el-alert': true,
    'el-progress': true,
    'el-result': true,
    'el-button': { template: '<button><slot /></button>' },
    'el-icon': true
  }

  it('includes META in default sections', () => {
    const wrapper = mount(WarehouseLedgerExportDialog, {
      props: { modelValue: true },
      global: { stubs: globalStubs }
    })
    expect(wrapper.vm.form.sections).toContain('META')
    expect(wrapper.vm.form.sections).toEqual(['BASIC', 'LEASE', 'DOC', 'META'])
  })

  it('emits update:modelValue on close', async () => {
    const wrapper = mount(WarehouseLedgerExportDialog, {
      props: { modelValue: true },
      global: { stubs: globalStubs }
    })
    wrapper.vm.handleClose()
    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([false])
  })

  it('resets form on dialog open', async () => {
    const wrapper = mount(WarehouseLedgerExportDialog, {
      props: { modelValue: true },
      global: { stubs: globalStubs }
    })
    wrapper.vm.form.sections = ['BASIC']
    wrapper.vm.form.scope = 'ids'
    await wrapper.setProps({ modelValue: false })
    await wrapper.setProps({ modelValue: true })
    expect(wrapper.vm.form.sections).toEqual(['BASIC', 'LEASE', 'DOC', 'META'])
    expect(wrapper.vm.form.scope).toBe('filter')
  })
})
