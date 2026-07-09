import { describe, it, expect, vi, beforeEach } from 'vitest'
import { nextTick } from 'vue'

// Mock API client - 必须在 import useQualificationDetail 之前
const httpMock = {
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  delete: vi.fn()
}
vi.mock('@/api/client', () => ({ default: httpMock }))

// Mock Element Plus
const elMessageMock = { success: vi.fn(), error: vi.fn(), warning: vi.fn() }
const elMessageBoxConfirm = vi.fn()
vi.mock('element-plus', () => ({
  ElMessage: elMessageMock,
  ElMessageBox: { confirm: elMessageBoxConfirm }
}))

const { useQualificationDetail } = await import('./useQualificationDetail.js')

describe('useQualificationDetail - CO-368 附件删除走 DELETE 接口', () => {
  let composable

  beforeEach(() => {
    vi.clearAllMocks()
    const qualifications = { value: [{ id: 1, name: 'cert', fileUrl: '123_cert.pdf', attachments: [{ id: 10, fileName: 'cert.pdf', fileUrl: '123_cert.pdf' }] }] }
    const fetchQualifications = vi.fn()
    composable = useQualificationDetail({ qualifications, fetchQualifications })
    composable.detailQualification.value = { id: 1, name: 'cert', fileUrl: '123_cert.pdf' }
    composable.detailAttachments.value = [{ id: 10, fileName: 'cert.pdf', fileUrl: '123_cert.pdf' }]
  })

  it('handleAttachmentDelete 应调用 DELETE /qualifications/{id}/attachments/{attId}', async () => {
    elMessageBoxConfirm.mockResolvedValue('confirm')
    httpMock.delete.mockResolvedValue({ data: {} })

    await composable.handleAttachmentDelete({ id: 10, fileName: 'cert.pdf', fileUrl: '123_cert.pdf' })

    expect(httpMock.delete).toHaveBeenCalledWith('/api/knowledge/qualifications/1/attachments/10')
    expect(httpMock.put).not.toHaveBeenCalled()
    expect(elMessageMock.success).toHaveBeenCalledWith('附件已删除')
  })

  it('handleAttachmentDelete 应使用 att.id 作为 URL 参数', async () => {
    elMessageBoxConfirm.mockResolvedValue('confirm')
    httpMock.delete.mockResolvedValue({ data: {} })

    await composable.handleAttachmentDelete({ id: 99, fileName: 'other.pdf', fileUrl: '456_other.pdf' })

    expect(httpMock.delete).toHaveBeenCalledWith('/api/knowledge/qualifications/1/attachments/99')
  })

  it('handleAttachmentDelete 缺少 att.id 应提示错误', async () => {
    elMessageBoxConfirm.mockResolvedValue('confirm')

    await composable.handleAttachmentDelete({ fileName: 'no-id.pdf' })

    expect(httpMock.delete).not.toHaveBeenCalled()
    expect(elMessageMock.warning).toHaveBeenCalled()
  })

  it('handleAttachmentDelete HTTP 失败应弹错误提示', async () => {
    elMessageBoxConfirm.mockResolvedValue('confirm')
    httpMock.delete.mockRejectedValue(new Error('Server error'))

    await composable.handleAttachmentDelete({ id: 10, fileName: 'cert.pdf' })

    expect(elMessageMock.error).toHaveBeenCalled()
  })

  it('handleAttachmentDelete 用户取消确认不应调用接口', async () => {
    elMessageBoxConfirm.mockRejectedValue('cancel')

    await composable.handleAttachmentDelete({ id: 10, fileName: 'cert.pdf' })

    expect(httpMock.delete).not.toHaveBeenCalled()
    expect(elMessageMock.error).not.toHaveBeenCalled()
  })
})

describe('useQualificationDetail - CO-554 行下载按附件数自动选 zip/单文件', () => {
  let composable

  beforeEach(() => {
    vi.clearAllMocks()
    // jsdom 没有 createObjectURL，mock 掉避免报错
    if (!window.URL.createObjectURL) window.URL.createObjectURL = vi.fn(() => 'blob:mock')
    if (!window.URL.revokeObjectURL) window.URL.revokeObjectURL = vi.fn()
    const qualifications = { value: [] }
    composable = useQualificationDetail({ qualifications, fetchQualifications: vi.fn() })
  })

  it('多附件：应调 /batch-download 打包 zip，不调单个附件接口', async () => {
    httpMock.post.mockResolvedValue({ data: new Blob(['zip']) })
    const row = {
      id: 7, name: '多附件资质',
      attachments: [
        { id: 1, fileName: 'a.pdf', fileUrl: 'a.pdf' },
        { id: 2, fileName: 'b.pdf', fileUrl: 'b.pdf' }
      ]
    }

    await composable.handleDownloadFile(row)

    expect(httpMock.post).toHaveBeenCalledWith('/api/knowledge/qualifications/batch-download', { ids: [7] }, { responseType: 'blob' })
    expect(httpMock.get).not.toHaveBeenCalled()
  })

  it('单附件：应调 /qualifications/{id}/attachments/{attId} 下原文件', async () => {
    httpMock.get.mockResolvedValue({ data: new Blob(['pdf']) })
    const row = { id: 7, name: '单附件资质', attachments: [{ id: 9, fileName: 'only.pdf', fileUrl: 'only.pdf' }] }

    await composable.handleDownloadFile(row)

    expect(httpMock.get).toHaveBeenCalledWith('/api/knowledge/qualifications/7/attachments/9', { responseType: 'blob' })
    expect(httpMock.post).not.toHaveBeenCalled()
  })

  it('无附件（fileUrl 也为空）：应弹错误，不调任何下载接口', async () => {
    const row = { id: 7, name: '无附件资质', attachments: [] }

    await composable.handleDownloadFile(row)

    expect(httpMock.post).not.toHaveBeenCalled()
    expect(httpMock.get).not.toHaveBeenCalled()
    expect(elMessageMock.error).toHaveBeenCalledWith('下载失败')
  })

  it('附件 fileUrl 为空字符串应被过滤（视为无可下载附件）', async () => {
    const row = { id: 7, name: '空 fileUrl 资质', attachments: [{ id: 1, fileName: 'blank.pdf', fileUrl: '  ' }] }

    await composable.handleDownloadFile(row)

    expect(httpMock.get).not.toHaveBeenCalled()
    expect(httpMock.post).not.toHaveBeenCalled()
  })
})
