import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import WarehouseImportDialog from './WarehouseImportDialog.vue'

vi.mock('@/api/client', () => ({
  default: { get: vi.fn(), post: vi.fn() }
}))

describe('WarehouseImportDialog', () => {
  const globalStubs = {
    'el-dialog': { template: '<div><slot /><slot name="footer" /></div>' },
    'el-alert': true,
    'el-link': { template: '<a @click="$emit(\'click\')"><slot /></a>' },
    'el-icon': true,
    'el-form': true,
    'el-form-item': true,
    'el-upload': true,
    'el-button': { template: '<button><slot /></button>' },
    'el-progress': true,
    'el-result': true,
    'el-tag': true,
    'el-table': true,
    'el-table-column': true
  }

  it('renders download template link when no task', () => {
    const wrapper = mount(WarehouseImportDialog, {
      props: { modelValue: true },
      global: { stubs: globalStubs }
    })
    expect(wrapper.text()).toContain('下载导入模板')
  })

  it('shows cancel button during import progress', async () => {
    const wrapper = mount(WarehouseImportDialog, {
      props: { modelValue: true },
      global: { stubs: globalStubs }
    })
    wrapper.vm.taskId = 'task-123'
    wrapper.vm.status = 'IMPORTING'
    await wrapper.vm.$nextTick()
    const buttons = wrapper.findAll('button')
    const hasCancel = buttons.some(b => b.text().includes('取消'))
    expect(hasCancel).toBe(true)
  })

  it('emits download-template event on link click', async () => {
    const wrapper = mount(WarehouseImportDialog, {
      props: { modelValue: true },
      global: { stubs: globalStubs }
    })
    const link = wrapper.find('a')
    expect(link.exists()).toBe(true)
    await link.trigger('click')
    expect(wrapper.emitted('download-template')?.length).toBeGreaterThanOrEqual(1)
  })
})
