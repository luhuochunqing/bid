// Input: personnelBatchApi blob download paths (template + error report)
// Output: verify triggerBlobDownload receives res.data (Blob) not the whole response object
// Pos: src/api/modules/ - CO-419 fix verification
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/api/client', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn()
  }
}))

vi.mock('@/utils/download.js', () => ({
  triggerBlobDownload: vi.fn()
}))

import httpClient from '@/api/client'
import { triggerBlobDownload } from '@/utils/download.js'
import { personnelBatchApi } from './personnelBatchApi.js'

describe('personnelBatchApi blob download (CO-419)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('downloadImportTemplate(): passes res.data (Blob) to triggerBlobDownload, not the whole response', async () => {
    // 模拟 axios 拦截器对 blob 响应返回完整 response 对象的行为（client.js:133-135）
    const blob = new Blob(['fake-excel-bytes'], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
    const fakeResponse = {
      data: blob,
      status: 200,
      statusText: 'OK',
      headers: { 'content-type': 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' },
      config: { responseType: 'blob' }
    }
    httpClient.get.mockResolvedValue(fakeResponse)

    await personnelBatchApi.downloadImportTemplate()

    expect(httpClient.get).toHaveBeenCalledWith('/api/knowledge/personnel/import/template', { responseType: 'blob' })
    // CO-419 修复前：triggerBlobDownload(new Blob([res]), ...) 把整个 response 对象包成 Blob
    // 修复后：triggerBlobDownload(res.data, ...) 只传 Blob 数据
    expect(triggerBlobDownload).toHaveBeenCalledTimes(1)
    const [receivedBlob, filename] = triggerBlobDownload.mock.calls[0]
    expect(receivedBlob).toBe(blob)
    expect(filename).toBe('personnel_import_template.xlsx')
  })

  it('downloadErrorReport(): passes res.data (Blob) to triggerBlobDownload, not the whole response', async () => {
    const blob = new Blob(['fake-report-bytes'])
    const fakeResponse = { data: blob, status: 200, config: { responseType: 'blob' } }
    httpClient.get.mockResolvedValue(fakeResponse)

    await personnelBatchApi.downloadErrorReport('task-123')

    expect(httpClient.get).toHaveBeenCalledWith('/api/knowledge/personnel/import/task-123/report', { responseType: 'blob' })
    expect(triggerBlobDownload).toHaveBeenCalledTimes(1)
    const [receivedBlob, filename] = triggerBlobDownload.mock.calls[0]
    expect(receivedBlob).toBe(blob)
    expect(filename).toBe('import_error_report_task-123.xlsx')
  })

  it('regression guard: triggerBlobDownload must NOT receive a Blob wrapping the response object', async () => {
    // 回归防护：确保不会回退到 new Blob([res]) 写法
    const blob = new Blob(['real-data'])
    const fakeResponse = { data: blob, status: 200, config: {} }
    httpClient.get.mockResolvedValue(fakeResponse)

    await personnelBatchApi.downloadImportTemplate()

    const received = triggerBlobDownload.mock.calls[0][0]
    // 接收到的应该是原始 Blob（res.data），而不是 new Blob([response]) 后的新 Blob
    expect(received).toBe(blob)
    // 如果是 new Blob([res])，received 会是一个新的 Blob 实例，且不等于原始 blob
    expect(received instanceof Blob).toBe(true)
  })
})
