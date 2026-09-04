// 契约测试：案例"查看源项目 / 标书原文"操作（路由修正 + 未归档预检 + 假 ZIP 防护）
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { ElMessage } from 'element-plus'
import { projectLifecycleApi } from '@/api/modules/projectLifecycle'
import { casesApi } from '@/api/modules/knowledge'
import { triggerBlobDownload, downloadWithFilename } from '@/utils/download'
import { getDocuments } from '@/api/modules/projectDocuments'
import { useBidCaseSourceActions } from '../useBidCaseSourceActions'

vi.mock('element-plus', () => ({
  ElMessage: { warning: vi.fn(), error: vi.fn(), success: vi.fn() }
}))

vi.mock('@/api/modules/knowledge', () => ({
  casesApi: { getDownloadUrl: vi.fn() }
}))

vi.mock('@/api/modules/projectLifecycle', () => ({
  projectLifecycleApi: {
    getArchiveFiles: vi.fn(),
    exportProjectArchive: vi.fn()
  }
}))

vi.mock('@/api/modules/projectDocuments', () => ({
  getDocuments: vi.fn(() => Promise.resolve({ data: [] })),
  getDocumentDownloadUrl: vi.fn((projectId, docId) => `/api/projects/${projectId}/documents/${docId}/download`),
}))

vi.mock('@/utils/download', async (importOriginal) => {
  const actual = await importOriginal()
  return { ...actual, triggerBlobDownload: vi.fn(), downloadWithFilename: vi.fn() }
})

// 当前用户角色（角色闸门用）：默认非 sales，保证既有下载用例不受闸门影响
const { mockUserState } = vi.hoisted(() => ({ mockUserState: { currentUser: { roleCode: 'bid-Team' } } }))
vi.mock('@/stores/user', () => ({
  useUserStore: () => mockUserState
}))

describe('useBidCaseSourceActions', () => {
  let openSpy

  beforeEach(() => {
    vi.clearAllMocks()
    mockUserState.currentUser = { roleCode: 'bid-Team' }
    openSpy = vi.spyOn(window, 'open').mockImplementation(() => null)
    getDocuments.mockResolvedValue({ data: [] })
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

  it('标书原文：无 sourceProjectId 时警告，不做任何请求（无原文分支）', async () => {
    const { openBidDocument } = useBidCaseSourceActions()
    await openBidDocument({})
    expect(projectLifecycleApi.getArchiveFiles).not.toHaveBeenCalled()
    expect(projectLifecycleApi.exportProjectArchive).not.toHaveBeenCalled()
    expect(casesApi.getDownloadUrl).not.toHaveBeenCalled()
    expect(ElMessage.warning).toHaveBeenCalledWith('标书原文暂不可用')
  })

  it('标书原文（历史直链）：obsBidFileKey 非空 → 签发预签名链接并 window.open，不走档案 ZIP', async () => {
    casesApi.getDownloadUrl.mockResolvedValue({
      url: 'https://ehsy-widbid.obs.cn-east-3.myhuaweicloud.com/xxx?Signature=abc',
      filename: '项目复盘-中广核.docx',
      expireSeconds: 300
    })
    const { openBidDocument } = useBidCaseSourceActions()
    await openBidDocument({ caseId: 88, obsBidFileKey: '2026年（投标文件）/某项目/技术标.docx' })
    expect(casesApi.getDownloadUrl).toHaveBeenCalledWith(88)
    expect(openSpy).toHaveBeenCalledWith(expect.stringContaining('Signature=abc'), '_blank')
    expect(projectLifecycleApi.getArchiveFiles).not.toHaveBeenCalled()
    expect(triggerBlobDownload).not.toHaveBeenCalled()
    expect(ElMessage.success).toHaveBeenCalledWith('标书原文下载已开始：项目复盘-中广核.docx')
  })

  it('标书原文（历史直链）：签发接口报无原文（409）时透出服务端 msg，不打开任何链接', async () => {
    casesApi.getDownloadUrl.mockRejectedValue({ response: { status: 409, data: { msg: '该案例暂无原版标书' } } })
    const { openBidDocument } = useBidCaseSourceActions()
    await openBidDocument({ caseId: 88, obsBidFileKey: 'k' })
    expect(openSpy).not.toHaveBeenCalled()
    expect(ElMessage.error).toHaveBeenCalledWith('该案例暂无原版标书')
  })

  it('标书原文（分流优先级）：obsBidFileKey 与 sourceProjectId 同时存在 → 走直链不走 ZIP', async () => {
    casesApi.getDownloadUrl.mockResolvedValue({ url: 'https://signed', filename: 'a.docx', expireSeconds: 300 })
    const { openBidDocument } = useBidCaseSourceActions()
    await openBidDocument({ caseId: 9, sourceProjectId: 77, obsBidFileKey: 'k' })
    expect(casesApi.getDownloadUrl).toHaveBeenCalledWith(9)
    expect(projectLifecycleApi.getArchiveFiles).not.toHaveBeenCalled()
  })

  it('标书原文：档案无附件（原文未归档）且无投标文件时提示并中止，不调导出接口', async () => {
    projectLifecycleApi.getArchiveFiles.mockResolvedValue({ data: { totalElements: 0 } })
    const { openBidDocument } = useBidCaseSourceActions()
    await openBidDocument({ sourceProjectId: 5 })
    expect(projectLifecycleApi.getArchiveFiles).toHaveBeenCalledWith(5, 1)
    expect(projectLifecycleApi.exportProjectArchive).not.toHaveBeenCalled()
    expect(triggerBlobDownload).not.toHaveBeenCalled()
    expect(ElMessage.warning).toHaveBeenCalledWith('标书原文未归档：该项目暂无档案附件')
  })

  it('标书原文：档案无附件但项目有 BID 投标文件时，兜底直接下载该投标文件', async () => {
    projectLifecycleApi.getArchiveFiles.mockResolvedValue({ data: { totalElements: 0 } })
    getDocuments.mockResolvedValue({
      data: [{ id: 9001, name: '项目投标文件.docx', documentCategory: 'BID' }]
    })
    const { openBidDocument } = useBidCaseSourceActions()
    await openBidDocument({ sourceProjectId: 5 })
    expect(projectLifecycleApi.getArchiveFiles).toHaveBeenCalledWith(5, 1)
    expect(getDocuments).toHaveBeenCalledWith(5, { documentCategory: 'BID' })
    expect(downloadWithFilename).toHaveBeenCalledWith('/api/projects/5/documents/9001/download', '项目投标文件.docx')
    expect(ElMessage.success).toHaveBeenCalledWith('标书原文下载成功：项目投标文件.docx')
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

  it('角色闸门：投标项目负责人(bid-projectLeader)点标书原文（直链分支）→ 提示无权限下载，不调任何接口', async () => {
    mockUserState.currentUser = { roleCode: 'bid-projectLeader' }
    const { openBidDocument } = useBidCaseSourceActions()
    await openBidDocument({ caseId: 88, obsBidFileKey: 'k' })
    expect(ElMessage.error).toHaveBeenCalledWith('无权限下载')
    expect(casesApi.getDownloadUrl).not.toHaveBeenCalled()
    expect(openSpy).not.toHaveBeenCalled()
    expect(triggerBlobDownload).not.toHaveBeenCalled()
  })

  it('角色闸门：投标项目负责人(bid-projectLeader)点标书原文（档案分支）→ 提示无权限下载，不调任何接口', async () => {
    mockUserState.currentUser = { roleCode: 'bid-projectLeader' }
    const { openBidDocument } = useBidCaseSourceActions()
    await openBidDocument({ sourceProjectId: 5 })
    expect(ElMessage.error).toHaveBeenCalledWith('无权限下载')
    expect(projectLifecycleApi.getArchiveFiles).not.toHaveBeenCalled()
    expect(projectLifecycleApi.exportProjectArchive).not.toHaveBeenCalled()
    expect(getDocuments).not.toHaveBeenCalled()
    expect(triggerBlobDownload).not.toHaveBeenCalled()
  })
})
