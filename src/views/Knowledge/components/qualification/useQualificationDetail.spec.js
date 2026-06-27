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
