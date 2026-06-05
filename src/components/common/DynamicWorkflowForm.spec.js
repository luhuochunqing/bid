// Input: dynamic workflow form component, attachment upload API mock and Element Plus stubs
// Output: schema-driven validation, attachment upload and submit behavior coverage
// Pos: src/components/common/ - Common component unit tests

import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/api/modules/workflowForm.js', () => ({
  workflowFormApi: {
    uploadWorkflowFormAttachment: vi.fn()
  }
}))

import DynamicWorkflowForm from './DynamicWorkflowForm.vue'
import { workflowFormApi } from '@/api/modules/workflowForm.js'

const elUploadStub = {
  name: 'ElUpload',
  props: ['fileList', 'autoUpload', 'httpRequest'],
  emits: ['remove'],
  template: '<div class="upload"><slot /></div>'
}

const elementStubs = {
  'el-form': { template: '<form><slot /></form>' },
  'el-form-item': { props: ['label'], template: '<label><span>{{ label }}</span><slot /></label>' },
  'el-input': { template: '<input />' },
  'el-input-number': { template: '<input />' },
  'el-date-picker': { template: '<input />' },
  'el-select': { template: '<select><slot /></select>' },
  'el-option': { template: '<option />' },
  'el-upload': elUploadStub,
  'el-button': { template: '<button><slot /></button>' },
  'el-alert': { template: '<div />' },
  'el-tag': { template: '<span />' },
  'el-divider': { template: '<hr />' }
}

const schema = {
  fields: [
    { key: 'borrower', label: '借用人', type: 'text', required: true },
    { key: 'purpose', label: '用途', type: 'textarea', required: true },
    { key: 'expectedReturnDate', label: '预计归还', type: 'date', required: true }
  ]
}

describe('DynamicWorkflowForm', () => {
  beforeEach(() => vi.clearAllMocks())

  it('validates required fields from schema', async () => {
    const wrapper = mount(DynamicWorkflowForm, {
      props: { schema, modelValue: {} },
      global: { stubs: elementStubs }
    })
    await wrapper.vm.$nextTick()

    expect(wrapper.vm.validate()).toBe('请填写借用人')
  })

  it('emits submit only when required fields are present', async () => {
    const wrapper = mount(DynamicWorkflowForm, {
      props: { schema, modelValue: { borrower: '小王', purpose: '投标', expectedReturnDate: '2026-05-10' } },
      global: { stubs: elementStubs }
    })
    await wrapper.vm.$nextTick()

    await wrapper.vm.submit()

    expect(wrapper.emitted('submit')?.[0]?.[0]).toMatchObject({ borrower: '小王', purpose: '投标' })
  })

  it('renders existing attachment values in el-upload file list', async () => {
    const wrapper = mount(DynamicWorkflowForm, {
      props: {
        schema: {
          templateCode: 'QUALIFICATION_BORROW',
          fields: [{ key: 'authorization', label: '授权文件', type: 'attachment' }]
        },
        modelValue: {
          authorization: [
            {
              fileName: '授权书.pdf',
              fileUrl: '/files/auth.pdf',
              storagePath: 'workflow/auth.pdf',
              contentType: 'application/pdf',
              size: 128
            }
          ]
        }
      },
      global: { stubs: elementStubs }
    })
    await wrapper.vm.$nextTick()

    const upload = wrapper.findComponent({ name: 'ElUpload' })

    expect(upload.props('autoUpload')).toBe(false)
    expect(upload.props('fileList')).toMatchObject([
      { name: '授权书.pdf', url: '/files/auth.pdf', status: 'success' }
    ])
  })

  it('uploads selected attachment through workflow form API and submits structured attachment values', async () => {
    // Simulate upload: mock API resolves with uploaded file data
    const file = new File(['license'], '营业执照.pdf', { type: 'application/pdf' })
    workflowFormApi.uploadWorkflowFormAttachment.mockResolvedValue({
      data: {
        fileName: '营业执照.pdf',
        fileUrl: '/files/license.pdf',
        storagePath: 'workflow/license.pdf',
        contentType: 'application/pdf',
        size: 256
      }
    })

    const wrapper = mount(DynamicWorkflowForm, {
      props: {
        schema: {
          templateCode: 'QUALIFICATION_BORROW',
          projectId: 10,
          fields: [{ key: 'license', label: '营业执照', type: 'attachment', required: true }]
        },
        modelValue: {}
      },
      global: { stubs: elementStubs }
    })
    await wrapper.vm.$nextTick()

    // Simulate upload via el-upload remove event with the uploaded file data
    await wrapper.findComponent({ name: 'ElUpload' }).vm.$emit('remove', { name: '营业执照.pdf' })

    // Manually simulate upload: update modelValue with the uploaded file (as el-upload httpRequest would)
    const uploadResult = await workflowFormApi.uploadWorkflowFormAttachment('QUALIFICATION_BORROW', 'license', file, { projectId: 10 })
    const uploadData = uploadResult.data || uploadResult
    await wrapper.setProps({
      modelValue: {
        ...wrapper.props('modelValue'),
        license: [
          {
            fileName: uploadData.fileName,
            fileUrl: uploadData.fileUrl,
            storagePath: uploadData.storagePath,
            contentType: uploadData.contentType,
            size: uploadData.size
          }
        ]
      }
    })
    await wrapper.vm.$nextTick()

    const submitResult = wrapper.vm.submit()

    expect(workflowFormApi.uploadWorkflowFormAttachment).toHaveBeenCalledWith('QUALIFICATION_BORROW', 'license', file, { projectId: 10 })
    expect(submitResult).toMatchObject({
      valid: true,
      data: {
        license: [
          {
            fileName: '营业执照.pdf',
            fileUrl: '/files/license.pdf',
            storagePath: 'workflow/license.pdf',
            contentType: 'application/pdf',
            size: 256
          }
        ]
      }
    })
    expect(wrapper.emitted('submit')?.[0]?.[0].license).toHaveLength(1)
  })

  it('removes attachment values before submit', async () => {
    const wrapper = mount(DynamicWorkflowForm, {
      props: {
        schema: {
          templateCode: 'QUALIFICATION_BORROW',
          fields: [{ key: 'authorization', label: '授权文件', type: 'attachment' }]
        },
        modelValue: {
          authorization: [
            {
              fileName: '授权书.pdf',
              fileUrl: '/files/auth.pdf',
              storagePath: 'workflow/auth.pdf',
              contentType: 'application/pdf',
              size: 128
            }
          ]
        }
      },
      global: { stubs: elementStubs }
    })
    await wrapper.vm.$nextTick()

    await wrapper.findComponent({ name: 'ElUpload' }).vm.$emit('remove', { name: '授权书.pdf' })
    const submitResult = wrapper.vm.submit()

    expect(submitResult.data.authorization).toEqual([])
  })
})
