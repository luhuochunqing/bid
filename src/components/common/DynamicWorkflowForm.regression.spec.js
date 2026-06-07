// Regression baseline for DynamicWorkflowForm. MUST continue to pass after
// the D2/D3 refactor that extracts DynamicFormRenderer.
//
// Scope of this file (locked contract the refactor must not silently break):
//   1. Every non-hidden schema field renders exactly one <el-form-item>.
//   2. submit() returns { valid: false, message: '请填写<label>' } when a
//      required non-info field is empty (via validate()).
//   3. submit() returns { valid: true, data: { ... cloned modelValue ... } }
//      and emits 'submit' with the same payload when required fields are filled.
//   4. Selecting an attachment (via el-upload @change with a raw file whose
//      status is not 'success') invokes workflowFormApi.uploadWorkflowFormAttachment
//      with (templateCode, field.key, rawFile, { projectId }).
//
// Compromise notes:
//   - We use Element Plus stubs (same approach as DynamicWorkflowForm.spec.js)
//     because the project does not globally register Element Plus for unit
//     tests; without stubs the real components either warn about unresolved
//     tags or fail to mount in jsdom. Stubs preserve the component's
//     observable contract without coupling the test to Element Plus internals.
//   - el-form-item presence is asserted by counting stubbed <label> elements
//     (one per form-item stub) rather than the '.el-form-item' CSS class,
//     because the stub deliberately renders as <label>.
//   - The attachment upload test drives the same integration point that the
//     production template wires (@change -> handleAttachmentChange -> uploadAttachment),
//     which is what the refactor must preserve end-to-end.

import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/api/modules/workflowForm.js', () => ({
  workflowFormApi: {
    uploadWorkflowFormAttachment: vi.fn().mockResolvedValue({
      data: { fileName: 'a.pdf', fileUrl: '/x', storagePath: 'sp/a.pdf' }
    })
  }
}))

import DynamicWorkflowForm from './DynamicWorkflowForm.vue'
import { workflowFormApi } from '@/api/modules/workflowForm.js'

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

const schema = {
  templateCode: 'QUALIFICATION_BORROW',
  fields: [
    { key: 'name', label: '名称', type: 'text', required: true },
    { key: 'note', label: '备注', type: 'textarea' },
    { key: 'file', label: '附件', type: 'attachment' }
  ]
}

describe('DynamicWorkflowForm regression baseline', () => {
  beforeEach(() => vi.clearAllMocks())

  it('renders one form-item per visible field', () => {
    const wrapper = mount(DynamicWorkflowForm, {
      props: { schema, modelValue: {} },
      global: { stubs: elementStubs }
    })
    expect(wrapper.findAllComponents({ name: 'ElFormItem' }).length).toBe(3)
  })

  it('submit returns { valid: false, message } when a required field is empty', () => {
    const wrapper = mount(DynamicWorkflowForm, {
      props: { schema, modelValue: {} },
      global: { stubs: elementStubs }
    })
    const r = wrapper.vm.submit()
    expect(r.valid).toBe(false)
    expect(r.message).toBe('请填写名称')
    expect(wrapper.emitted('submit')).toBeFalsy()
  })

  it('submit returns { valid: true, data } and emits submit when required fields are filled', () => {
    const wrapper = mount(DynamicWorkflowForm, {
      props: { schema, modelValue: { name: 'x' } },
      global: { stubs: elementStubs }
    })
    const r = wrapper.vm.submit()
    expect(r.valid).toBe(true)
    expect(r.data.name).toBe('x')
    expect(wrapper.emitted('submit')?.[0]?.[0]).toMatchObject({ name: 'x' })
  })

  it('uploading an attachment calls workflowFormApi.uploadWorkflowFormAttachment', async () => {
    const wrapper = mount(DynamicWorkflowForm, {
      props: { schema, modelValue: {} },
      global: { stubs: elementStubs }
    })
    const file = new File(['content'], 'a.pdf', { type: 'application/pdf' })
    // Element Plus el-upload's @change fires with { raw, status, ... }.
    // The component's handleAttachmentChange forwards non-'success' events to uploadAttachment,
    // which is the integration point with workflowFormApi.
    const upload = wrapper.findComponent({ name: 'ElUpload' })
    expect(upload.exists()).toBe(true)
    await upload.vm.$emit('change', { raw: file, status: null })
    await flushPromises()
    expect(workflowFormApi.uploadWorkflowFormAttachment).toHaveBeenCalledTimes(1)
    expect(workflowFormApi.uploadWorkflowFormAttachment).toHaveBeenCalledWith(
      'QUALIFICATION_BORROW',
      'file',
      file,
      expect.objectContaining({ projectId: null })
    )
  })
})
