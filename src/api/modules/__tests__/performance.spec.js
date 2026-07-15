// Input: src/api/modules/performance.js — downloadTemplate/batchExport/batchExportZip + buildPayload/normalizePerformance
// Output: CO-444 Blob 处理回归测试 + CO-583 buildPayload/normalizePerformance 字段测试
// Pos: src/api/modules/__tests__/ — API 层单元测试
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
// 维护声明: downloadTemplate/batchExport/batchExportZip 函数改动时，同步更新对应的 Blob 处理测试用例；
//           buildPayload/normalizePerformance 字段改动时，同步更新 CO-583 测试用例。

import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import { performanceApi } from '../performance.js'

// mock httpClient：模拟 axios 拦截器对 blob 响应不解包的行为（返回完整 response 对象）
vi.mock('../../client.js', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn()
  }
}))

import httpClient from '../../client.js'

describe('CO-444 performance.js Blob 处理修复', () => {
  let originalCreateObjectURL
  let originalRevokeObjectURL
  let originalCreateElement
  let originalAppendChild
  let originalRemoveChild

  beforeEach(() => {
    // 保存原始 DOM API
    originalCreateObjectURL = window.URL.createObjectURL
    originalRevokeObjectURL = window.URL.revokeObjectURL
    originalCreateElement = document.createElement
    originalAppendChild = document.body.appendChild
    originalRemoveChild = document.body.removeChild

    // mock DOM API
    window.URL.createObjectURL = vi.fn((blob) => `blob:${blob?.size || 0}`)
    window.URL.revokeObjectURL = vi.fn()
    document.createElement = vi.fn(() => ({
      href: '', click: vi.fn(), setAttribute: vi.fn(), remove: vi.fn()
    }))
    document.body.appendChild = vi.fn()
    document.body.removeChild = vi.fn()
  })

  afterEach(() => {
    window.URL.createObjectURL = originalCreateObjectURL
    window.URL.revokeObjectURL = originalRevokeObjectURL
    document.createElement = originalCreateElement
    document.body.appendChild = originalAppendChild
    document.body.removeChild = originalRemoveChild
    vi.clearAllMocks()
  })

  // CO-444 核心修复：downloadTemplate 必须使用 res.data（Blob），不是完整 response 对象
  it('downloadTemplate 使用 res.data 构造 Blob（非完整 response 对象）', async () => {
    const blobData = new Blob(['fake excel content'], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
    // 模拟拦截器：blob 响应返回完整 response 对象（不解包 .data）
    httpClient.get.mockResolvedValueOnce({
      data: blobData,
      status: 200,
      statusText: 'OK',
      headers: {},
      config: { responseType: 'blob' }
    })

    await performanceApi.downloadTemplate()

    // 验证 createObjectURL 传入的是 res.data 对应的 Blob，而非序列化 response 对象的 Blob
    expect(window.URL.createObjectURL).toHaveBeenCalledTimes(1)
    const passedBlob = window.URL.createObjectURL.mock.calls[0][0]
    expect(passedBlob).toBeInstanceOf(Blob)
    // 关键断言：传入的应是 res.data 本身（同一个 Blob 引用），而非 new Blob([responseObj])
    expect(passedBlob).toBe(blobData)
    // size 应与原 Blob 一致（[object Object] 序列化后 size 不同）
    expect(passedBlob.size).toBe(blobData.size)
  })

  // CO-444 核心修复：batchExport 必须使用 res.data（Blob）
  it('batchExport 使用 res.data 构造 Blob（非完整 response 对象）', async () => {
    const blobData = new Blob(['fake export content'], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
    httpClient.get.mockResolvedValueOnce({
      data: blobData,
      status: 200,
      statusText: 'OK',
      headers: {},
      config: { responseType: 'blob' }
    })

    await performanceApi.batchExport([1, 2, 3])

    expect(window.URL.createObjectURL).toHaveBeenCalledTimes(1)
    const passedBlob = window.URL.createObjectURL.mock.calls[0][0]
    expect(passedBlob).toBeInstanceOf(Blob)
    expect(passedBlob).toBe(blobData)
    expect(passedBlob.size).toBe(blobData.size)
  })

  // CO-444 核心修复：batchExportZip 必须使用 res.data（Blob）
  it('batchExportZip 使用 res.data 构造 Blob（非完整 response 对象）', async () => {
    const blobData = new Blob(['fake zip content'], { type: 'application/zip' })
    httpClient.get.mockResolvedValueOnce({
      data: blobData,
      status: 200,
      statusText: 'OK',
      headers: {},
      config: { responseType: 'blob' }
    })

    await performanceApi.batchExportZip([1, 2])

    expect(window.URL.createObjectURL).toHaveBeenCalledTimes(1)
    const passedBlob = window.URL.createObjectURL.mock.calls[0][0]
    expect(passedBlob).toBeInstanceOf(Blob)
    expect(passedBlob).toBe(blobData)
    expect(passedBlob.size).toBe(blobData.size)
  })

  // 回归保护：downloadTemplate 调用正确的 URL
  it('downloadTemplate 调用 /api/knowledge/performance/template', async () => {
    const blobData = new Blob(['content'])
    httpClient.get.mockResolvedValueOnce({ data: blobData, config: { responseType: 'blob' } })

    await performanceApi.downloadTemplate()

    expect(httpClient.get).toHaveBeenCalledWith(
      '/api/knowledge/performance/template',
      { responseType: 'blob' }
    )
  })

  // CO-444 附件包上传：batchImport 支持可选 attachments 参数
  it('batchImport 不传附件时仅上传 Excel 文件', async () => {
    const file = new File(['excel'], 'test.xlsx')
    httpClient.post.mockResolvedValueOnce({ success: true, data: { successCount: 1, failureCount: 0, failures: [] } })

    await performanceApi.batchImport(file)

    expect(httpClient.post).toHaveBeenCalledWith(
      '/api/knowledge/performance/import',
      expect.any(FormData),
      { headers: { 'Content-Type': 'multipart/form-data' } }
    )
    const formData = httpClient.post.mock.calls[0][1]
    expect(formData.get('file')).toBe(file)
    expect(formData.getAll('attachments')).toEqual([])
  })

  it('batchImport 传入附件时将附件加入 FormData', async () => {
    const file = new File(['excel'], 'test.xlsx')
    const attach1 = new File(['pdf'], '合同协议.pdf')
    const attach2 = new File(['img'], '商城截图.png')
    httpClient.post.mockResolvedValueOnce({ success: true, data: { successCount: 1, failureCount: 0, failures: [] } })

    await performanceApi.batchImport(file, [attach1, attach2])

    const formData = httpClient.post.mock.calls[0][1]
    expect(formData.get('file')).toBe(file)
    const attachments = formData.getAll('attachments')
    expect(attachments).toHaveLength(2)
    expect(attachments[0]).toBe(attach1)
    expect(attachments[1]).toBe(attach2)
  })
})

describe('CO-583 buildPayload / normalizePerformance 字段调整', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('create 提交的 payload 不含 totalExpiryDate 字段', async () => {
    httpClient.post.mockResolvedValueOnce({ data: { id: 1 } })
    await performanceApi.create({
      contractName: '合同A',
      signingDate: '2024-01-01',
      expiryDate: '2025-01-01',
      // 即便前端误传 totalExpiryDate，buildPayload 也不应送出
      totalExpiryDate: '2025-12-01',
      attachmentMap: {}
    })
    const payload = httpClient.post.mock.calls[0][1]
    expect(payload).not.toHaveProperty('totalExpiryDate')
  })

  it('update 提交的 payload 不含 totalExpiryDate 字段', async () => {
    httpClient.put.mockResolvedValueOnce({ data: { id: 1 } })
    await performanceApi.update(1, {
      contractName: '合同A',
      signingDate: '2024-01-01',
      expiryDate: '2025-01-01',
      totalExpiryDate: '2025-12-01',
      attachmentMap: {}
    })
    const payload = httpClient.put.mock.calls[0][1]
    expect(payload).not.toHaveProperty('totalExpiryDate')
  })

  it('normalizePerformance 把后端 groupTotalExpiryDate 透传到前端', async () => {
    httpClient.get.mockResolvedValueOnce({
      data: [{
        id: 1,
        contractName: '合同A',
        groupCompany: '中核集团',
        signingDate: '2024-01-01',
        expiryDate: '2025-01-01',
        groupTotalExpiryDate: '2025-12-31',
        daysRemaining: 180,
        status: 'IN_PERFORMANCE'
      }]
    })
    const { data } = await performanceApi.getList({})
    expect(data[0].groupTotalExpiryDate).toBe('2025-12-31')
  })

  it('normalizePerformance 后端未返回 groupTotalExpiryDate 时为 null', async () => {
    httpClient.get.mockResolvedValueOnce({
      data: [{
        id: 1,
        contractName: '合同A',
        groupCompany: '',
        signingDate: '2024-01-01',
        expiryDate: '2025-01-01',
        daysRemaining: 180,
        status: 'IN_PERFORMANCE'
      }]
    })
    const { data } = await performanceApi.getList({})
    expect(data[0].groupTotalExpiryDate).toBeNull()
  })
})
