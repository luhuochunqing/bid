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
    'el-form': true,
    'el-form-item': true,
    'el-radio-group': {
      template: '<div><slot /></div>',
      props: ['modelValue'],
      emits: ['update:modelValue']
    },
    'el-radio': {
      template: '<label @click="$emit(\'click\')"><slot /></label>',
      props: ['value', 'disabled'],
      emits: ['click']
    },
    'el-checkbox-group': {
      template: '<div><slot /></div>',
      props: ['modelValue'],
      emits: ['update:modelValue']
    },
    'el-checkbox': {
      template: '<label @click="$emit(\'click\')"><slot /></label>',
      props: ['value'],
      emits: ['click']
    }
  }

  it('defaults attachment scope to ALL', () => {
    const wrapper = mount(WarehouseExportDialog, {
      props: { modelValue: true },
      global: { stubs: globalStubs }
    })
    expect(wrapper.vm.form.attachmentScope).toBe('ALL')
    expect(wrapper.vm.form.attachmentTypes).toEqual([])
  })

  it('defaults attachmentForms to [WORD_COMBINED] (CO-582 §3.1)', () => {
    const wrapper = mount(WarehouseExportDialog, {
      props: { modelValue: true },
      global: { stubs: globalStubs }
    })
    expect(wrapper.vm.form.attachmentForms).toEqual(['WORD_COMBINED'])
  })

  it('defaults scope to filter when no defaultScope provided', () => {
    const wrapper = mount(WarehouseExportDialog, {
      props: { modelValue: true },
      global: { stubs: globalStubs }
    })
    expect(wrapper.vm.form.scope).toBe('filter')
  })

  it('uses defaultScope prop as initial scope when provided', () => {
    const wrapper = mount(WarehouseExportDialog, {
      props: { modelValue: true, defaultScope: 'ids', selectedIds: [1, 2] },
      global: { stubs: globalStubs }
    })
    expect(wrapper.vm.form.scope).toBe('ids')
  })

  it('resets scope and types when dialog reopens', async () => {
    const wrapper = mount(WarehouseExportDialog, {
      props: { modelValue: true },
      global: { stubs: globalStubs }
    })
    wrapper.vm.form.attachmentScope = 'PARTIAL'
    wrapper.vm.form.attachmentTypes = ['INVOICE']
    wrapper.vm.form.scope = 'ids'
    await wrapper.setProps({ modelValue: false })
    await wrapper.setProps({ modelValue: true })
    expect(wrapper.vm.form.attachmentScope).toBe('ALL')
    expect(wrapper.vm.form.attachmentTypes).toEqual([])
    expect(wrapper.vm.form.scope).toBe('filter')
  })

  it('resets attachmentForms to default [WORD_COMBINED] when dialog reopens (CO-582)', async () => {
    const wrapper = mount(WarehouseExportDialog, {
      props: { modelValue: true },
      global: { stubs: globalStubs }
    })
    wrapper.vm.form.attachmentForms = ['ATTACHMENTS_FOLDER', 'WORD_COMBINED']
    await wrapper.setProps({ modelValue: false })
    await wrapper.setProps({ modelValue: true })
    expect(wrapper.vm.form.attachmentForms).toEqual(['WORD_COMBINED'])
  })

  it('sends attachmentScope and attachmentTypes in ids mode', async () => {
    http.post.mockResolvedValueOnce({ data: { taskId: 42 } })
    const wrapper = mount(WarehouseExportDialog, {
      props: { modelValue: true, defaultScope: 'ids', selectedIds: [1, 2] },
      global: { stubs: globalStubs }
    })
    wrapper.vm.form.attachmentScope = 'PARTIAL'
    wrapper.vm.form.attachmentTypes = ['PROPERTY_CERTIFICATE', 'PHOTOS']
    wrapper.vm.handleStart()
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

  it('sends attachmentForms in payload when handleStart (CO-582 §3.1)', async () => {
    http.post.mockResolvedValueOnce({ data: { taskId: 50 } })
    const wrapper = mount(WarehouseExportDialog, {
      props: { modelValue: true, defaultScope: 'ids', selectedIds: [1, 2] },
      global: { stubs: globalStubs }
    })
    wrapper.vm.form.attachmentForms = ['ATTACHMENTS_FOLDER', 'WORD_COMBINED']
    wrapper.vm.handleStart()
    await flushPromises()

    expect(http.post).toHaveBeenCalledWith(
      '/api/knowledge/warehouses/export',
      expect.objectContaining({
        ids: [1, 2],
        attachmentForms: ['ATTACHMENTS_FOLDER', 'WORD_COMBINED']
      })
    )
  })

  it('does not send attachmentTypes when scope is ALL', async () => {
    http.post.mockResolvedValueOnce({ data: { taskId: 43 } })
    const wrapper = mount(WarehouseExportDialog, {
      props: { modelValue: true, defaultScope: 'filter', filter: { keyword: 'test' } },
      global: { stubs: globalStubs }
    })
    wrapper.vm.form.attachmentScope = 'ALL'
    wrapper.vm.handleStart()
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
    wrapper.vm.form.attachmentScope = 'PARTIAL'
    await flushPromises()
    expect(wrapper.vm.validation.valid).toBe(false)
    expect(wrapper.vm.validation.message).toBe('请至少选择一种附件类型')
    wrapper.vm.form.attachmentTypes = ['INVOICE']
    expect(wrapper.vm.validation.valid).toBe(true)
    expect(wrapper.vm.validation.message).toBe('')
  })

  it('disables start export when no attachmentForms selected (CO-582 §3.1)', async () => {
    const wrapper = mount(WarehouseExportDialog, {
      props: { modelValue: true },
      global: { stubs: globalStubs }
    })
    expect(wrapper.vm.validation.valid).toBe(true)
    wrapper.vm.form.attachmentForms = []
    await flushPromises()
    expect(wrapper.vm.validation.valid).toBe(false)
    expect(wrapper.vm.validation.message).toBe('请至少选择一种附件组织形式')
    wrapper.vm.form.attachmentForms = ['WORD_COMBINED']
    expect(wrapper.vm.validation.valid).toBe(true)
    expect(wrapper.vm.validation.message).toBe('')
  })
})
