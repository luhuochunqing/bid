// Input: tenders API module with mocked HTTP client
// Output: tender search, manual create, upload, and doc-insight parse API coverage
// Pos: src/api/modules/ - API module unit tests
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/api/client', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn()
  }
}))

import httpClient from '@/api/client'
import { tendersApi } from './tenders.js'

describe('tendersApi', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('getList(): sends search params to the backend without local re-filtering', async () => {
    httpClient.get.mockResolvedValue({
      success: true,
      data: {
        content: [
          { id: 1, title: '华东数据中心 GPU 算力平台采购项目', region: '上海' },
          { id: 2, title: '华北办公电脑采购项目', region: '北京' }
        ],
        totalElements: 2,
        totalPages: 1,
        pageNumber: 0,
        pageSize: 20,
        hasNext: false,
        hasPrevious: false
      }
    })

    const params = {
      keyword: 'GPU',
      status: 'PENDING',
      source: 'external',
      region: '上海',
      industry: '数据中心',
      purchaserName: '西域采购',
      purchaserHash: 'hash-shanghai-xiyu',
      budgetMin: 4000000,
      budgetMax: 6000000,
      deadlineFrom: '2026-05-01T00:00:00',
      deadlineTo: '2026-05-10T23:59:59',
      publishDateFrom: '2026-04-01',
      publishDateTo: '2026-04-30',
      aiScoreMin: 90,
      aiScoreMax: 95
    }

    const result = await tendersApi.getList(params)

    expect(httpClient.get).toHaveBeenCalledWith('/api/tenders', { params: { ...params, size: 200 } })
    expect(result.success).toBe(true)
    expect(result.data).toHaveLength(2)
    expect(result.total).toBe(2)
  })

  it('create(): posts manual tender payload to the real backend endpoint', async () => {
    const payload = {
      title: '人工录入标讯',
      budget: 1200000,
      region: '上海',
      tenderAgency: '上海招标代理有限公司',
      purchaserName: '上海西域采购中心',
      purchaserHash: 'hash-shanghai-xiyu',
      publishDate: '2026-04-21',
      deadline: '2026-05-08T18:00:00',
      bidOpeningTime: '2026-05-10T09:30:00',
      contactName: '王经理',
      contactPhone: '13800138000',
      customerType: 'KA 客户',
      priority: 'A',
      description: '人工录入测试',
      tags: ['数据中心'],
      source: 'manual',
      status: 'PENDING_ASSIGNMENT'
    }
    httpClient.post.mockResolvedValue({ success: true, data: { id: 10, ...payload } })

    await tendersApi.create(payload)

    expect(httpClient.post).toHaveBeenCalledWith(
      '/api/tenders',
      payload,
      expect.objectContaining({
        headers: expect.objectContaining({ 'Idempotency-Key': expect.any(String) })
      })
    )
  })

  it('parseTenderIntakeDocument(): uploads manual intake documents through doc-insight', async () => {
    const file = new File(['tender'], '招标公告.pdf', { type: 'application/pdf' })
    httpClient.post.mockResolvedValue({
      success: true,
      data: { documentId: 'doc-insight://TENDER_INTAKE/manual-tender/招标公告.pdf' }
    })

    await tendersApi.parseTenderIntakeDocument(file, { entityId: 'manual-tender' })

    expect(httpClient.post).toHaveBeenCalledWith(
      '/api/doc-insight/parse',
      expect.any(FormData),
      {
        headers: { 'Content-Type': 'multipart/form-data' },
        timeout: 45000
      }
    )
    const formData = httpClient.post.mock.calls[0][1]
    expect(formData.get('profile')).toBe('TENDER_INTAKE')
    expect(formData.get('entityId')).toBe('manual-tender')
    expect(formData.get('file').name).toBe('招标公告.pdf')
    expect(formData.get('file').type).toBe('application/pdf')
  })

  it('parseTenderIntakeText(): uploads pasted text as a tender intake text file', async () => {
    httpClient.post.mockResolvedValue({
      success: true,
      data: { documentId: 'doc-insight://TENDER_INTAKE/manual-tender/粘贴标讯文本.txt' }
    })

    await tendersApi.parseTenderIntakeText('项目名称：西域MRO项目', { entityId: 'manual-tender' })

    const formData = httpClient.post.mock.calls[0][1]
    expect(formData.get('profile')).toBe('TENDER_INTAKE')
    expect(formData.get('entityId')).toBe('manual-tender')
    expect(formData.get('file').name).toBe('粘贴标讯文本.txt')
    expect(formData.get('file').type).toBe('text/plain')
  })

  it('initUploadSession(): creates async upload session for large tender files', async () => {
    const payload = {
      fileName: '大型标书.pdf',
      expectedFileSize: 83886080
    }
    httpClient.post.mockResolvedValue({
      success: true,
      data: { uploadId: 'abc123', relativePath: '2026/04/22/1/abc123_大型标书.pdf' }
    })

    await tendersApi.initUploadSession(payload)

    expect(httpClient.post).toHaveBeenCalledWith('/api/tenders/upload-init', payload)
  })

  it('completeUpload(): queues task and returns task identifier', async () => {
    const payload = {
      uploadId: 'abc123',
      pageCount: 360,
      priority: 5
    }
    httpClient.post.mockResolvedValue({
      success: true,
      data: { fileId: 5, taskId: 88, status: 'QUEUED' }
    })

    await tendersApi.completeUpload(payload)

    expect(httpClient.post).toHaveBeenCalledWith('/api/tenders/upload-complete', payload)
  })

  it('getUploadTaskStatus(): rejects non-numeric task IDs before request', async () => {
    const result = await tendersApi.getUploadTaskStatus('task-88')

    expect(httpClient.get).not.toHaveBeenCalled()
    expect(result.success).toBe(false)
  })
})
