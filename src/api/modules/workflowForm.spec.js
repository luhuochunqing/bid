// Input: workflow form API module with mocked HTTP client and FormData attachment payloads
// Output: workflow form schema, submission and attachment endpoint contract coverage
// Pos: src/api/modules/ - API module unit tests
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/api/client', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn()
  }
}))

import httpClient from '@/api/client'
import { workflowFormApi } from './workflowForm.js'

describe('workflowFormApi', () => {
  beforeEach(() => vi.clearAllMocks())

  it('submitWorkflowForm(): posts a workflow form instance to the real backend endpoint', async () => {
    httpClient.post.mockResolvedValue({ success: true, data: { id: 1, status: 'OA_APPROVING' } })

    const result = await workflowFormApi.submitWorkflowForm('QUALIFICATION_BORROW', {
      templateCode: 'QUALIFICATION_BORROW',
      businessType: 'QUALIFICATION_BORROW',
      projectId: 10,
      applicantName: '小王',
      formData: { qualificationId: '1001' }
    })

    expect(httpClient.post).toHaveBeenCalledWith('/api/workflow-forms/instances', {
      templateCode: 'QUALIFICATION_BORROW',
      businessType: 'QUALIFICATION_BORROW',
      projectId: 10,
      applicantName: '小王',
      formData: { qualificationId: '1001' }
    })
    expect(result.data.status).toBe('OA_APPROVING')
  })

  it('getFormDefinition(): loads active schema from the backend template API', async () => {
    httpClient.get.mockResolvedValue({ success: true, data: { fields: [{ key: 'qualificationId' }] } })

    const result = await workflowFormApi.getFormDefinition('QUALIFICATION_BORROW')

    expect(httpClient.get).toHaveBeenCalledWith('/api/workflow-forms/templates/QUALIFICATION_BORROW/active')
    expect(result.data.fields.map((field) => field.key)).toContain('qualificationId')
  })

  it('admin template APIs use dedicated configuration endpoints', async () => {
    httpClient.get.mockResolvedValue({ success: true, data: [] })
    httpClient.post.mockResolvedValue({ success: true, data: { templateCode: 'SEAL_APPLY' } })
    httpClient.put.mockResolvedValue({ success: true, data: { workflowCode: 'WF_SEAL' } })

    await workflowFormApi.listAdminTemplates()
    await workflowFormApi.listTemplateVersions('SEAL_APPLY')
    await workflowFormApi.createTemplateDraft({ templateCode: 'SEAL_APPLY' })
    await workflowFormApi.saveOaBinding('SEAL_APPLY', { workflowCode: 'WF_SEAL' })
    await workflowFormApi.publishTemplate('SEAL_APPLY')
    await workflowFormApi.rollbackTemplateVersion('SEAL_APPLY', 1)
    await workflowFormApi.testSubmitTemplate('SEAL_APPLY', { formData: { title: '测试' } })

    expect(httpClient.get).toHaveBeenCalledWith('/api/admin/workflow-forms/templates')
    expect(httpClient.get).toHaveBeenCalledWith('/api/admin/workflow-forms/templates/SEAL_APPLY/versions')
    expect(httpClient.post).toHaveBeenCalledWith('/api/admin/workflow-forms/templates', { templateCode: 'SEAL_APPLY' })
    expect(httpClient.put).toHaveBeenCalledWith('/api/admin/workflow-forms/templates/SEAL_APPLY/oa-binding', { workflowCode: 'WF_SEAL' })
    expect(httpClient.post).toHaveBeenCalledWith('/api/admin/workflow-forms/templates/SEAL_APPLY/publish')
    expect(httpClient.post).toHaveBeenCalledWith('/api/admin/workflow-forms/templates/SEAL_APPLY/versions/1/rollback')
    expect(httpClient.post).toHaveBeenCalledWith('/api/admin/workflow-forms/templates/SEAL_APPLY/oa/test-submit', { formData: { title: '测试' } })
  })

  it('uploadWorkflowFormAttachment(): posts multipart attachment payload to the workflow form attachment API', async () => {
    httpClient.post.mockResolvedValue({
      success: true,
      data: {
        fileName: '授权书.pdf',
        fileUrl: '/files/auth.pdf',
        storagePath: 'workflow/QUALIFICATION_BORROW/auth.pdf',
        contentType: 'application/pdf',
        size: 128
      }
    })
    const file = new File(['pdf'], '授权书.pdf', { type: 'application/pdf' })

    const result = await workflowFormApi.uploadWorkflowFormAttachment('qualification_borrow', 'authorization', file, { projectId: 10 })

    expect(httpClient.post).toHaveBeenCalledTimes(1)
    const [url, body] = httpClient.post.mock.calls[0]
    expect(url).toBe('/api/workflow-forms/attachments')
    expect(body).toBeInstanceOf(FormData)
    expect(body.get('templateCode')).toBe('QUALIFICATION_BORROW')
    expect(body.get('fieldKey')).toBe('authorization')
    expect(body.get('projectId')).toBe('10')
    expect(body.get('file')).toBe(file)
    expect(result.data.storagePath).toBe('workflow/QUALIFICATION_BORROW/auth.pdf')
  })
})
