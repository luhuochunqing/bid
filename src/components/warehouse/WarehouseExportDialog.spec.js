import { describe, it, expect, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import WarehouseExportDialog from './WarehouseExportDialog.vue'

vi.mock('@/api/client', () => ({
  default: { get: vi.fn(), post: vi.fn() }
}))

import http from '@/api/client'

describe('WarehouseExportDialog', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  const globalStubs = {
    'el-dialog': { template: '<div><slot /><slot name="footer" /></div>' },
    'el-alert': true,
    'el-tag': true,
    'el-progress': true,
    'el-result': true,
    'el-button': { template: '<button><slot /></button>' },
    'el-icon': true,
    'el-radio-group': {
      template: '<div><slot /></div>',
      props: ['modelValue'],
      emits: ['update:modelValue']
    },
    'el-radio': {
      template: '<label @click="$emit(\'click\')"><slot /></label>',
      props: ['label'],
      emits: ['click']
    },
    'el-checkbox-group': {
      template: '<div><slot /></div>',
      props: ['modelValue'],
      emits: ['update:modelValue']
    },
    'el-checkbox': {
      template: '<label @click="$emit(\'click\')"><slot /></label>',
      props: ['label'],
      emits: ['click']
    }
  }

  it('defaults attachment scope to ALL', () => {
    const wrapper = mount(WarehouseExportDialog, {
      props: { modelValue: true },
      global: { stubs: globalStubs }
    })
    expect(wrapper.vm.attachmentScope).toBe('ALL')
    expect(wrapper.vm.attachmentTypes).toEqual([])
  })

  it('resets scope and types when dialog reopens', async () => {
    const wrapper = mount(WarehouseExportDialog, {
      props: { modelValue: true },
      global: { stubs: globalStubs }
    })
    wrapper.vm.attachmentScope = 'PARTIAL'
    wrapper.vm.attachmentTypes = ['INVOICE']
    await wrapper.setProps({ modelValue: false })
    await wrapper.setProps({ modelValue: true })
    expect(wrapper.vm.attachmentScope).toBe('ALL')
    expect(wrapper.vm.attachmentTypes).toEqual([])
  })

  it('sends attachmentScope and attachmentTypes in ids mode', async () => {
    http.post.mockResolvedValueOnce({ data: { taskId: 42 } })
    const wrapper = mount(WarehouseExportDialog, {
      props: { modelValue: true, mode: 'ids', selectedIds: [1, 2] },
      global: { stubs: globalStubs }
    })
    wrapper.vm.attachmentScope = 'PARTIAL'
    wrapper.vm.attachmentTypes = ['PROPERTY_CERTIFICATE', 'PHOTOS']
    wrapper.vm.startExport()
    await flushPromises()

    expect(http.post).toHaveBeenCalledWith(
      '/api/knowledge/warehouses/export',
      expect.objectContaining({
        ids: [1, 2],
        attachmentScope: 'PARTIAL',
        attachmentTypes: ['PROPERTY_CERTIFICATE', 'PHOTOS']
      })
    )
  })

  it('does not send attachmentTypes when scope is ALL', async () => {
    http.post.mockResolvedValueOnce({ data: { taskId: 43 } })
    const wrapper = mount(WarehouseExportDialog, {
      props: { modelValue: true, mode: 'filter', filters: { keyword: 'test' } },
      global: { stubs: globalStubs }
    })
    wrapper.vm.attachmentScope = 'ALL'
    wrapper.vm.startExport()
    await flushPromises()

    expect(http.post).toHaveBeenCalledWith(
      '/api/knowledge/warehouses/export',
      expect.objectContaining({ keyword: 'test', attachmentScope: 'ALL' })
    )
    const payload = http.post.mock.lastCall[1]
    expect(payload.attachmentTypes).toBeUndefined()
  })

  it('disables start export when PARTIAL is selected without any type', async () => {
    const wrapper = mount(WarehouseExportDialog, {
      props: { modelValue: true },
      global: { stubs: globalStubs }
    })
    expect(wrapper.vm.validation.valid).toBe(true)
    wrapper.vm.attachmentScope = 'PARTIAL'
    await flushPromises()
    expect(wrapper.vm.validation.valid).toBe(false)
    expect(wrapper.vm.validation.message).toBe('请至少选择一种附件类型')
    wrapper.vm.attachmentTypes = ['INVOICE']
    expect(wrapper.vm.validation.valid).toBe(true)
    expect(wrapper.vm.validation.message).toBe('')
  })
})
