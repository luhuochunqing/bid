// 契约测试：案例"查看源项目 / 标书原文"操作（路由修正 + 未归档预检 + 假 ZIP 防护）
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { ElMessage } from 'element-plus'
import { projectLifecycleApi } from '@/api/modules/projectLifecycle'
import { triggerBlobDownload } from '@/utils/download'
import { useBidCaseSourceActions } from '../useBidCaseSourceActions'

vi.mock('element-plus', () => ({
  ElMessage: { warning: vi.fn(), error: vi.fn(), success: vi.fn() }
}))

vi.mock('@/api/modules/projectLifecycle', () => ({
  projectLifecycleApi: {
    getArchiveFiles: vi.fn(),
    exportProjectArchive: vi.fn()
  }
}))

vi.mock('@/utils/download', async (importOriginal) => {
  const actual = await importOriginal()
  return { ...actual, triggerBlobDownload: vi.fn() }
})

describe('useBidCaseSourceActions', () => {
  let openSpy

  beforeEach(() => {
    vi.clearAllMocks()
    openSpy = vi.spyOn(window, 'open').mockImplementation(() => null)
  })

  afterEach(() => {
    openSpy.mockRestore()
  })

  it('查看源项目：跳转投标项目详情 /project/{id}（旧路由 /bidding/detail 不存在）', () => {
    const { goToSourceProject } = useBidCaseSourceActions()
    goToSourceProject({ sourceProjectId: 123 })
    expect(openSpy).toHaveBeenCalledWith('/project/123', '_blank')
  })

  it('查看源项目：无 sourceProjectId 时警告，不跳转', () => {
    const { goToSourceProject } = useBidCaseSourceActions()
    goToSourceProject({})
    expect(openSpy).not.toHaveBeenCalled()
    expect(ElMessage.warning).toHaveBeenCalledWith('来源项目不可用')
  })

  it('标书原文：无 sourceProjectId 时警告，不做任何请求', async () => {
    const { openBidDocument } = useBidCaseSourceActions()
    await openBidDocument({})
    expect(projectLifecycleApi.getArchiveFiles).not.toHaveBeenCalled()
    expect(projectLifecycleApi.exportProjectArchive).not.toHaveBeenCalled()
    expect(ElMessage.warning).toHaveBeenCalledWith('标书原文暂不可用')
  })

  it('标书原文：档案无附件（原文未归档）时提示并中止，不调导出接口', async () => {
    projectLifecycleApi.getArchiveFiles.mockResolvedValue({ data: { totalElements: 0 } })
    const { openBidDocument } = useBidCaseSourceActions()
    await openBidDocument({ sourceProjectId: 5 })
    expect(projectLifecycleApi.getArchiveFiles).toHaveBeenCalledWith(5, 1)
    expect(projectLifecycleApi.exportProjectArchive).not.toHaveBeenCalled()
    expect(triggerBlobDownload).not.toHaveBeenCalled()
    expect(ElMessage.warning).toHaveBeenCalledWith('标书原文未归档：该项目暂无档案附件')
  })

  it('标书原文：错误体（application/json blob）报失败并提示原因，不落盘', async () => {
    projectLifecycleApi.getArchiveFiles.mockResolvedValue({ data: { totalElements: 2 } })
    const jsonBody = JSON.stringify({ msg: '无权限访问该项目档案' })
    projectLifecycleApi.exportProjectArchive.mockResolvedValue({
      data: new Blob([jsonBody], { type: 'application/json' })
    })
    const { openBidDocument } = useBidCaseSourceActions()
    await openBidDocument({ sourceProjectId: 5 })
    expect(triggerBlobDownload).not.toHaveBeenCalled()
    expect(ElMessage.error).toHaveBeenCalledWith('标书原文下载失败：无权限访问该项目档案')
  })

  it('标书原文：正常 ZIP 响应用 res.data 构造 Blob 并触发下载', async () => {
    projectLifecycleApi.getArchiveFiles.mockResolvedValue({ data: { totalElements: 3 } })
    const zipBlob = new Blob(['zip-bytes'], { type: 'application/zip' })
    projectLifecycleApi.exportProjectArchive.mockResolvedValue({ data: zipBlob })
    const { openBidDocument } = useBidCaseSourceActions()
    await openBidDocument({ sourceProjectId: 5 })
    expect(triggerBlobDownload).toHaveBeenCalledTimes(1)
    const [blobArg, filename] = triggerBlobDownload.mock.calls[0]
    expect(blobArg).toBeInstanceOf(Blob)
    expect(blobArg.type).toBe('application/zip')
    expect(filename).toBe('标书原文-5.zip')
    expect(ElMessage.success).toHaveBeenCalledWith('标书原文下载成功')
  })
})
