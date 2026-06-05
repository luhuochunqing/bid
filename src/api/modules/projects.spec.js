// Input: projects API module with mocked HTTP client
// Output: project task decomposition and tender breakdown endpoint coverage
// Pos: src/api/modules/ - API module unit tests
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/api/client', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    patch: vi.fn(),
    delete: vi.fn(),
  },
}))

import httpClient from '@/api/client'
import { projectsApi } from './projects.js'

describe('projectsApi', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('decomposeTasks(): posts to the real project task decomposition endpoint', async () => {
    const payload = { strategy: 'fromTender' }
    httpClient.post.mockResolvedValue({
      success: true,
      data: [{ id: 1, name: '资格文件整理' }],
    })

    await projectsApi.decomposeTasks(12, payload)

    expect(httpClient.post).toHaveBeenCalledWith('/api/projects/12/tasks/decompose', payload, { silentError: true })
  })

  it('parseTenderBreakdown(): uploads tender files to the independent project breakdown endpoint', async () => {
    const file = new File(['招标正文'], '招标文件.docx', {
      type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    })
    httpClient.post.mockResolvedValue({
      success: true,
      data: { document: { snapshotId: 601 } },
    })

    await projectsApi.parseTenderBreakdown(12, file)

    expect(httpClient.post).toHaveBeenCalledWith(
      '/api/projects/12/tender-breakdown',
      expect.any(FormData),
      { headers: { 'Content-Type': 'multipart/form-data' }, timeout: 120000, silentError: true },
    )
    const formData = httpClient.post.mock.calls[0][1]
    expect(formData.get('file').name).toBe('招标文件.docx')
  })

  it('getTenderBreakdownReadiness(): checks project tender parsing configuration before upload', async () => {
    httpClient.get.mockResolvedValue({
      success: true,
      data: { ready: false, providerName: 'DeepSeek' },
    })

    await projectsApi.getTenderBreakdownReadiness(12)

    expect(httpClient.get).toHaveBeenCalledWith('/api/projects/12/tender-breakdown/readiness', { silentError: true })
  })

  it('getLatestTenderBreakdown(): fetches the reusable parsed tender breakdown snapshot', async () => {
    httpClient.get.mockResolvedValue({
      success: true,
      data: { document: { snapshotId: 601 } },
    })

    await projectsApi.getLatestTenderBreakdown(12)

    expect(httpClient.get).toHaveBeenCalledWith('/api/projects/12/tender-breakdown/latest', { silentError: true })
  })

  it('parseUploadedTenderBreakdown(): reuses an already uploaded tender document without multipart upload', async () => {
    httpClient.post.mockResolvedValue({
      success: true,
      data: { document: { snapshotId: 701, name: '已上传招标文件.docx' } },
    })

    await projectsApi.parseUploadedTenderBreakdown(12)

    expect(httpClient.post).toHaveBeenCalledWith(
      '/api/projects/12/tender-breakdown/reuse-uploaded',
      null,
      { timeout: 120000, silentError: true },
    )
  })

  it('uploadDocument(): sends real project document files as multipart payloads', async () => {
    const file = new File(['招标正文'], '招标文件.docx', {
      type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    })
    const formData = new FormData()
    formData.set('file', file)
    formData.set('name', file.name)
    httpClient.post.mockResolvedValue({
      success: true,
      data: { id: 301, fileUrl: 'bid-agent://tender-documents/12/stored.docx' },
    })

    await projectsApi.uploadDocument(12, formData)

    expect(httpClient.post).toHaveBeenCalledWith(
      '/api/projects/12/documents',
      formData,
      { headers: { 'Content-Type': 'multipart/form-data' }, timeout: 120000 },
    )
  })

  it('decomposeTasks(): rejects non-numeric project IDs before request', async () => {
    const result = await projectsApi.decomposeTasks('PROJECT_12')

    expect(httpClient.post).not.toHaveBeenCalled()
    expect(result.success).toBe(false)
  })

  it('decomposeTasks(): keeps demo project IDs read-only', async () => {
    const result = await projectsApi.decomposeTasks(-1)

    expect(httpClient.post).not.toHaveBeenCalled()
    expect(result.success).toBe(false)
    expect(result.message).toContain('Demo records are read-only')
  })

  it('updateTask issues PUT /api/tasks/{id} with backend dto', async () => {
    httpClient.put.mockResolvedValue({ success: true, data: { id: 1, title: 'X', status: 'TODO' } })
    const result = await projectsApi.updateTask(1, { title: 'X', status: 'TODO' })
    expect(httpClient.put).toHaveBeenCalledWith('/api/tasks/1', { title: 'X', status: 'TODO' })
    expect(result.data.id).toBe(1)
  })
})
