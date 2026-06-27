import { describe, it, expect, vi, beforeEach } from 'vitest'

// CO-378: 验证项目文档打包导出 composable 的核心逻辑
// mock 依赖：httpClient / getProjectDocuments / triggerBlobDownload / ElMessage / jszip

vi.mock('@/api/client.js', () => ({
  default: {
    get: vi.fn(),
  },
}))

vi.mock('@/api/modules/projectDocuments.js', () => ({
  getDocuments: vi.fn(),
}))

vi.mock('@/utils/download.js', () => ({
  triggerBlobDownload: vi.fn(),
}))

vi.mock('element-plus', () => ({
  ElMessage: { info: vi.fn(), warning: vi.fn(), error: vi.fn(), success: vi.fn() },
}))

// jszip mock：记录 file() 调用，generateAsync 返回固定 blob
const fileCalls = []
const generateAsyncMock = vi.fn().mockResolvedValue(new Blob(['zip'], { type: 'application/zip' }))
vi.mock('jszip', () => ({
  default: class {
    file(name, blob) { fileCalls.push({ name, blob }) }
    generateAsync = generateAsyncMock
  },
}))

import httpClient from '@/api/client.js'
import { getDocuments as getProjectDocuments } from '@/api/modules/projectDocuments.js'
import { triggerBlobDownload } from '@/utils/download.js'
import { ElMessage } from 'element-plus'
import { useProjectDocumentsExport } from './useProjectDocumentsExport.js'

describe('CO-378 useProjectDocumentsExport', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    fileCalls.length = 0
  })

  it('项目信息缺失时给出 warning 且不发起请求', async () => {
    const { exportDocumentsAsZip } = useProjectDocumentsExport(null)
    await exportDocumentsAsZip()
    expect(ElMessage.warning).toHaveBeenCalledWith('项目信息缺失，无法导出')
    expect(getProjectDocuments).not.toHaveBeenCalled()
  })

  it('文档列表为空时给出 info 提示', async () => {
    getProjectDocuments.mockResolvedValue({ data: [] })
    const { exportDocumentsAsZip } = useProjectDocumentsExport(1)
    await exportDocumentsAsZip()
    expect(ElMessage.info).toHaveBeenCalledWith('当前项目暂无文档，无法导出')
    expect(triggerBlobDownload).not.toHaveBeenCalled()
  })

  it('成功打包多个文档为 zip 并触发下载', async () => {
    getProjectDocuments.mockResolvedValue({
      data: [{ id: 10, name: 'a.pdf' }, { id: 11, name: 'b.docx' }],
    })
    httpClient.get
      .mockResolvedValueOnce({ data: new Blob(['a'], { type: 'application/pdf' }) })
      .mockResolvedValueOnce({ data: new Blob(['b'], { type: 'application/octet-stream' }) })

    const { exportDocumentsAsZip } = useProjectDocumentsExport(1)
    await exportDocumentsAsZip()

    expect(fileCalls).toHaveLength(2)
    expect(fileCalls.map((c) => c.name)).toEqual(['a.pdf', 'b.docx'])
    expect(generateAsyncMock).toHaveBeenCalledWith({ type: 'blob', compression: 'DEFLATE' })
    expect(triggerBlobDownload).toHaveBeenCalled()
    expect(ElMessage.success).toHaveBeenCalledWith('已导出 2 个项目文档')
  })

  it('同名文件自动追加序号后缀避免覆盖', async () => {
    getProjectDocuments.mockResolvedValue({
      data: [{ id: 1, name: 'report.pdf' }, { id: 2, name: 'report.pdf' }, { id: 3, name: 'report.pdf' }],
    })
    httpClient.get.mockResolvedValue({ data: new Blob(['x'], { type: 'application/pdf' }) })

    const { exportDocumentsAsZip } = useProjectDocumentsExport(1)
    await exportDocumentsAsZip()

    expect(fileCalls.map((c) => c.name)).toEqual(['report.pdf', 'report (1).pdf', 'report (2).pdf'])
  })

  it('部分文件下载失败时仍打包成功的文件并 warning 提示失败项', async () => {
    getProjectDocuments.mockResolvedValue({
      data: [{ id: 1, name: 'ok.pdf' }, { id: 2, name: 'bad.pdf' }],
    })
    httpClient.get
      .mockResolvedValueOnce({ data: new Blob(['ok'], { type: 'application/pdf' }) })
      .mockRejectedValueOnce(new Error('network'))

    const { exportDocumentsAsZip } = useProjectDocumentsExport(1)
    await exportDocumentsAsZip()

    expect(fileCalls).toHaveLength(1)
    expect(fileCalls[0].name).toBe('ok.pdf')
    expect(ElMessage.warning).toHaveBeenCalledWith('导出完成：成功 1 个，失败 1 个（bad.pdf）')
  })

  it('所有文件下载均失败时给出 error 且不触发下载', async () => {
    getProjectDocuments.mockResolvedValue({
      data: [{ id: 1, name: 'a.pdf' }, { id: 2, name: 'b.pdf' }],
    })
    httpClient.get.mockRejectedValue(new Error('network'))

    const { exportDocumentsAsZip } = useProjectDocumentsExport(1)
    await exportDocumentsAsZip()

    expect(fileCalls).toHaveLength(0)
    expect(triggerBlobDownload).not.toHaveBeenCalled()
    expect(ElMessage.error).toHaveBeenCalledWith('文档导出失败：所有文件均下载失败，请稍后重试')
  })

  it('exporting ref 正确反映加载状态', async () => {
    getProjectDocuments.mockResolvedValue({ data: [] })
    const { exporting, exportDocumentsAsZip } = useProjectDocumentsExport(1)
    expect(exporting.value).toBe(false)
    const p = exportDocumentsAsZip()
    expect(exporting.value).toBe(true)
    await p
    expect(exporting.value).toBe(false)
  })

  it('支持 getter 函数形式的 projectId', async () => {
    getProjectDocuments.mockResolvedValue({ data: [] })
    const { exportDocumentsAsZip } = useProjectDocumentsExport(() => 42)
    await exportDocumentsAsZip()
    expect(getProjectDocuments).toHaveBeenCalledWith(42)
  })
})
