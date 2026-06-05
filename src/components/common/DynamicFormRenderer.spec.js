// Input: DynamicFormRenderer component with fields prop and injected uploadFn
// Output: pure rendering behavior, validate/submit contract and uploadFn injection coverage
// Pos: src/components/common/ - Common component unit tests

import { mount, flushPromises } from '@vue/test-utils'
import { describe, it, expect, vi } from 'vitest'

import DynamicFormRenderer from './DynamicFormRenderer.vue'

const elementStubs = {
  'el-form': { template: '<form><slot /></form>' },
  'el-form-item': {
    name: 'ElFormItem',
    props: ['label', 'required'],
    template: '<label class="el-form-item"><span>{{ label }}</span><slot /></label>'
  },
  'el-input': { template: '<input />' },
  'el-input-number': { template: '<input />' },
  'el-date-picker': { template: '<input />' },
  'el-select': { template: '<select><slot /></select>' },
  'el-option': { template: '<option />' },
  'el-upload': {
    name: 'ElUpload',
    props: ['fileList', 'autoUpload', 'httpRequest', 'limit', 'accept'],
    emits: ['change', 'remove'],
    template: '<div class="upload"><slot /></div>'
  },
  'el-button': { template: '<button><slot /></button>' },
  'el-alert': { template: '<div class="el-alert" />' }
}

describe('DynamicFormRenderer', () => {
  it('renders one el-form-item per visible field', () => {
    const fields = [
      { key: 't', label: 'T', type: 'text' },
      { key: 'd', label: 'D', type: 'date' },
      { key: 'n', label: 'N', type: 'number' },
      { key: 's', label: 'S', type: 'select', options: [{ label: 'A', value: 'a' }] }
    ]
    const wrapper = mount(DynamicFormRenderer, {
      props: { fields, modelValue: {} },
      global: { stubs: elementStubs }
    })
    expect(wrapper.findAllComponents({ name: 'ElFormItem' }).length).toBe(4)
  })

  it('skips fields with hidden=true', () => {
    const fields = [
      { key: 'a', label: 'A', type: 'text' },
      { key: 'b', label: 'B', type: 'text', hidden: true }
    ]
    const wrapper = mount(DynamicFormRenderer, {
      props: { fields, modelValue: {} },
      global: { stubs: elementStubs }
    })
    expect(wrapper.findAllComponents({ name: 'ElFormItem' }).length).toBe(1)
  })

  it('validate() returns label-prefixed message for missing required field', () => {
    const fields = [{ key: 'a', label: '甲', type: 'text', required: true }]
    const wrapper = mount(DynamicFormRenderer, {
      props: { fields, modelValue: {} },
      global: { stubs: elementStubs }
    })
    expect(wrapper.vm.validate()).toContain('甲')
  })

  it('submit() returns {valid:false,message} when required missing', () => {
    const fields = [{ key: 'a', label: '甲', type: 'text', required: true }]
    const wrapper = mount(DynamicFormRenderer, {
      props: { fields, modelValue: {} },
      global: { stubs: elementStubs }
    })
    const r = wrapper.vm.submit()
    expect(r.valid).toBe(false)
    expect(r.message).toContain('甲')
  })

  it('submit() returns {valid:true,data} when valid', () => {
    const fields = [{ key: 'a', label: '甲', type: 'text', required: true }]
    const wrapper = mount(DynamicFormRenderer, {
      props: { fields, modelValue: { a: 'x' } },
      global: { stubs: elementStubs }
    })
    const r = wrapper.vm.submit()
    expect(r.valid).toBe(true)
    expect(r.data).toEqual({ a: 'x' })
  })

  it('does not echo modelValue prop sync back to parent', async () => {
    const fields = [{ key: 'a', label: '甲', type: 'text' }]
    const wrapper = mount(DynamicFormRenderer, {
      props: { fields, modelValue: { a: '' } },
      global: { stubs: elementStubs }
    })

    await wrapper.setProps({ modelValue: { a: 'from-parent' } })
    await flushPromises()

    expect(wrapper.emitted('update:modelValue')).toBeFalsy()
  })

  it('attachment field: calls injected uploadFn', async () => {
    const uploadFn = vi.fn().mockResolvedValue({ fileName: 'a.pdf', fileUrl: '/x' })
    const fields = [{ key: 'f', label: 'F', type: 'attachment' }]
    const wrapper = mount(DynamicFormRenderer, {
      props: { fields, modelValue: {}, uploadFn },
      global: { stubs: elementStubs }
    })
    const file = new File(['x'], 'a.pdf')
    await wrapper.vm.uploadAttachment(fields[0], { file })
    await flushPromises()
    expect(uploadFn).toHaveBeenCalled()
  })

  it('attachment field: throws when uploadFn is missing', async () => {
    const fields = [{ key: 'f', label: 'F', type: 'attachment' }]
    const wrapper = mount(DynamicFormRenderer, {
      props: { fields, modelValue: {} },
      global: { stubs: elementStubs }
    })
    await expect(
      wrapper.vm.uploadAttachment(fields[0], { file: new File(['x'], 'a.pdf') })
    ).rejects.toThrow(/uploadFn/)
  })

  it('attachment uploadFn missing: calls request.onError and throws', async () => {
    const fields = [{ key: 'f', label: 'F', type: 'attachment' }]
    const wrapper = mount(DynamicFormRenderer, {
      props: { fields, modelValue: {} },
      global: { stubs: elementStubs }
    })
    const onError = vi.fn()
    await expect(
      wrapper.vm.uploadAttachment(fields[0], { file: new File(['x'], 'a.pdf'), onError })
    ).rejects.toThrow(/uploadFn/)
    expect(onError).toHaveBeenCalledTimes(1)
    expect(onError.mock.calls[0][0]).toBeInstanceOf(Error)
  })

  it('attachment uploadFn returning incomplete attachment logs a warn', async () => {
    const uploadFn = vi.fn().mockResolvedValue({ contentType: 'application/pdf' })
    const fields = [{ key: 'f', label: 'F', type: 'attachment' }]
    const wrapper = mount(DynamicFormRenderer, {
      props: { fields, modelValue: {}, uploadFn },
      global: { stubs: elementStubs }
    })
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {})
    try {
      await wrapper.vm.uploadAttachment(fields[0], { file: new File(['x'], 'a.pdf') })
      await flushPromises()
      expect(warnSpy).toHaveBeenCalled()
      expect(warnSpy.mock.calls[0][0]).toContain('DynamicFormRenderer')
    } finally {
      warnSpy.mockRestore()
    }
  })
})
